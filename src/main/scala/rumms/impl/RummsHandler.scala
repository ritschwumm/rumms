package rumms
package impl

import scutil.core.implicits._
import scutil.jdk.implicits._
import scutil.lang._
import scutil.log._

import scjson.ast._
import scjson.ast.JsonNavigation._
import scjson.codec._
import scjson.converter._
import scjson.converter.syntax._

import scwebapp._
import scwebapp.instances._
import scwebapp.method._
import scwebapp.status._
import scwebapp.header._
import scwebapp.data.MimeType

import rumms.impl.syntax._

/** mount this with an url-pattern of <configuration.path>/STAR (where STAR is a literal "*") */
final class RummsHandler(configuration:RummsConfiguration, context:RummsHandlerContext) extends Logging {
	import Constants.paths

	private val serverVersion	=
		Constants.version.toString + "/" + configuration.version

	//------------------------------------------------------------------------------
	//## request handling

	lazy val totalPlan:HttpHandler	=
		partialPlan	orAlways
		constant(HttpResponder.sync(EmptyStatus(NOT_FOUND)))

	lazy val partialPlan:HttpPHandler	=
		subHandler(GET,		paths.code,	code)	orElse
		subHandler(POST,	paths.hi,	hi)		orElse
		subHandler(POST,	paths.comm,	comm)

	private def subHandler(method:HttpMethod, subPath:String, handler:HttpHandler):HttpPHandler	=
		req => {
			(req.fullPathUTF8 exists (_ ==== configuration.path + subPath)) option {
				if (req.method.toOption == Some(method)) {
					try {
						context.expireConversations()
						handler(req)
					}
					catch { case e:Exception =>
						ERROR(e)
						HttpResponder.sync(EmptyStatus(INTERNAL_SERVER_ERROR))
					}
				}
				else HttpResponder.sync(EmptyStatus(METHOD_NOT_ALLOWED))
			}
		}

	//------------------------------------------------------------------------------
	//## code transfer

	/** send javascript code for client configuration */
	private def code(request:HttpRequest):HttpResponder	= {
		val servletPrefix	= request.contextPath + configuration.path
		ClientCode(clientCode(servletPrefix))
	}

	private def clientCode(servletPrefix:String):String	= {
		val resource	= "rumms/Client.js"
		val raw			= getClass.getClassLoader.classpathResourceOrError(resource).string(Constants.encoding)
		configure(raw, Map[String,JsonValue](
			"VERSION"			-> JsonValue.fromString(serverVersion),
			"ENCODING"			-> JsonValue.fromString(Constants.encoding.name),
			"CLIENT_TTL"		-> JsonValue.fromLong(Constants.clientTTL.millis),
			"SERVLET_PREFIX"	-> JsonValue.fromString(servletPrefix)
		))
	}

	/** patch raw code by replacing @{id} tags */
	private def configure(raw:String, params:Map[String,JsonValue]):String =
		params.foldLeft(raw){ (raw, param) =>
			val (key, value)	= param
			val pattern			= "@{" + key + "}"
			val code			= JsonCodec encodeShort value
			raw.replace(pattern, code)
		}

	//------------------------------------------------------------------------------
	//## message transfer

	private object MyWriters
		extends	JsonWriters

	/** establish a new Conversation */
	private def hi(request:HttpRequest):HttpResponder	= {
		// BETTER send Json data here
		val action:Action[HttpResponder]	=
			for {
				clientVersion	<- bodyString(request).toAction(Forbidden,	"unreadable message")
			}
			yield (clientVersion == serverVersion).cata (
				Upgrade,
				Connected(context.createConversation())
			)

		action.log foreach { ERROR(_:_*) }
		action.responder
	}

	/** receive and send messages for a single Conversation */
	private def comm(request:HttpRequest):HttpResponder	= {
		import MyWriters._

		val action:Action[HttpResponder]	=
			for {
				json			<- bodyString(request)						.toAction (Forbidden,		"unreadable message")
				data			<- JsonCodec.decode(json)					.toAction (Forbidden,		"invalid message")
				conversationId	<- (data / "conversation").string			.toAction (Forbidden,		"conversationId missing")	map ConversationId.apply
				clientCont		<- (data / "clientCont").toLong				.toAction (Forbidden,		"clientCont missing")
				serverCont		<- (data / "serverCont").toLong				.toAction (Forbidden,		"serverCont missing")
				incoming		<- (data / "messages").arraySeq				.toAction (Forbidden,		"messages missing")
				conversation	<- context.findConversation(conversationId)	.toAction (Disconnected,	"unknown conversation")
			}
			yield {
				conversation.touch()

				// tell the client it's alive
				conversation.handleHeartbeat()

				// give new messages to the client
				conversation.handleIncoming(incoming, clientCont)

				def compileResponse(batch:Batch):HttpResponse = {
					val json	=
						jsonObject(
							"clientCont"	-> clientCont,
							"serverCont"	-> batch.serverCont,
							"messages"		-> batch.messages
						)
					json match {
						case Validated.Valid(x)		=>
							JsonOK(x)
						case Validated.Invalid(errors)	=>
							ERROR("json creation failed", errors)
							EmptyStatus(INTERNAL_SERVER_ERROR)
					}
				}

				// maybe there already are new messages, if not, we have to wait
				val fromConversation	= conversation fetchOutgoing serverCont
				if (fromConversation.messages.nonEmpty || incoming.nonEmpty) {
					HttpResponder.sync(compileResponse(fromConversation))
				}
				else {
					val (responder, send)	=
						HttpResponder.async(
							timeout	= Constants.continuationTTL,
							timeoutResponse	= thunk {
								compileResponse(conversation fetchOutgoing serverCont)
							},
							errorResponse	= thunk {
								EmptyStatus(INTERNAL_SERVER_ERROR)
							}
						)
					conversation onHasOutgoing thunk {
						send(compileResponse(conversation fetchOutgoing serverCont))
					}
					responder
				}
			}

		action.log foreach { ERROR(_:_*) }
		action.responder
	}

	private def bodyString(request:HttpRequest):Either[Exception,String]	=
		Catch.exception in (request.body readString Constants.encoding)

	//------------------------------------------------------------------------------
	//## helper

	private val CONNECTED_TEXT		= "OK"
	private val DISCONNECTED_TEXT	= "CONNECT"
	private val UPGRADED_TEXT		= "VERSION"

	private val Forbidden:HttpResponder		=
		HttpResponder.sync(EmptyStatus(FORBIDDEN))

	private def ClientCode(code:String):HttpResponder	=
		HttpResponder.sync(StringOK(code, text_javascript))

	private def Connected(conversationId:ConversationId):HttpResponder	=
		SendPlainTextCharset(CONNECTED_TEXT + " " + conversationId.value)

	private def Upgrade:HttpResponder	=
		SendPlainTextCharset(UPGRADED_TEXT + " " + serverVersion)

	private val Disconnected:HttpResponder	=
		SendPlainTextCharset(DISCONNECTED_TEXT)

	private def SendPlainTextCharset(s:String):HttpResponder	=
		HttpResponder.sync(StringOK(s, text_plain))

	//------------------------------------------------------------------------------

	private def JsonOK(json:JsonValue):HttpResponse	=
		HttpResponse(
			OK,	None,
			DisableCaching ++
			HeaderValues(
				ContentType(application_json)
			),
			HttpOutput.writeString(
				Charsets.utf_8,
				JsonCodec encodeShort json
			)
		)

	private def StringOK(text:String, contentType:MimeType):HttpResponse	=
		HttpResponse(
			OK,	None,
			DisableCaching ++
			HeaderValues(
				ContentType(contentType.addParameter("charset",  Constants.encoding.name))
			),
			HttpOutput.writeString(Constants.encoding, text)
		)

	private def EmptyStatus(status:HttpStatus):HttpResponse	=
		HttpResponse(
			status,	None,
			DisableCaching,
			HttpOutput.empty
		)
}
