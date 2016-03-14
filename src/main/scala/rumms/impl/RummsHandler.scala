package rumms
package impl

import java.nio.charset.Charset

import scutil.lang._
import scutil.implicits._
import scutil.io.Charsets
import scutil.log._

import scjson._
import scjson.codec._
import scjson.syntax._
import scjson.serialization._
import scjson.JSONNavigation._

import scwebapp._
import scwebapp.instances._
import scwebapp.method._
import scwebapp.status._
import scwebapp.header._
import scwebapp.data.MimeType

import rumms.impl.HandlerUtil._

/** mount this with an url-pattern of <configuration.path>/STAR (where STAR is a literal "*") */
final class RummsHandler(configuration:RummsConfiguration, context:RummsHandlerContext) extends Logging {
	import Constants.paths
	
	private val serverVersion	=
			Constants.version.toString + "/" + configuration.version
		
	//------------------------------------------------------------------------------
	//## request handling
	
	lazy val totalPlan:HttpHandler	=
			partialPlan	orAlways
			constant(HttpResponder(EmptyStatus(NOT_FOUND)))
			
	lazy val partialPlan:HttpPHandler	=
			subHandler(GET,		paths.code,	code)	orElse
			subHandler(POST,	paths.hi,	hi)		orElse
			subHandler(POST,	paths.comm,	comm)
			
	private def subHandler(method:HttpMethod, subPath:String, handler:HttpHandler):HttpPHandler	=
			req => {
				(req.fullPathUTF8 ==== configuration.path + subPath) guard {
					if (req.method.toOption == Some(method)) {
						try {
							context.expireConversations()
							handler(req)
						}
						catch { case e:Exception =>
							ERROR(e)
							HttpResponder(EmptyStatus(INTERNAL_SERVER_ERROR))
						}
					}
					else HttpResponder(EmptyStatus(METHOD_NOT_ALLOWED))
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
		val raw	=
				getClass.getClassLoader.resources
				.readBytes	(resource)
				.getOrError (so"cannot read resource ${resource}")
				.into		(Constants.encoding.decodeTried)
				.getOrError (so"cannot decode resource value")
		configure(raw, Map(
			"VERSION"			-> JSONString(serverVersion),
			"ENCODING"			-> JSONString(Constants.encoding.name),
			"CLIENT_TTL"		-> JSONNumber(Constants.clientTTL.millis),
			"SERVLET_PREFIX"	-> JSONString(servletPrefix)
		))
	}
	
	/** patch raw code by replacing @{id} tags */
	private def configure(raw:String, params:Map[String,JSONValue]):String =
			params.foldLeft(raw){ (raw, param) =>
				val (key, value)	= param
				val pattern			= "@{" + key + "}"
				val code			= JSONCodec encode value
				raw replace (pattern, code)
			}
	
	//------------------------------------------------------------------------------
	//## message transfer
	
	private object MyProtocol
			extends	NativeProtocol
			with	ISeqProtocol
			with	IdentityProtocol
	
	/** establish a new Conversation */
	private def hi(request:HttpRequest):HttpResponder	= {
		// BETTER send JSON data here
		val action:Action[HttpResponder]	=
				for {
					clientVersion	<- bodyString(request)	toUse (Forbidden,	"unreadable message")
				}
				yield clientVersion == serverVersion cata (
					Upgrade,
					Connected(context.createConversation())
				)
					
		actionLog(action) foreach { ERROR(_:_*) }
		actionResponder(action)
	}
	
	/** receive and send messages for a single Conversation */
	private def comm(request:HttpRequest):HttpResponder	= {
		import MyProtocol._
		
		val action:Action[HttpResponder]	=
				for {
					json			<- bodyString(request)						toUse (Forbidden,		"unreadable message")
					data			<- JSONCodec decode json					toUse (Forbidden,		"invalid message")
					conversationId	<- (data / "conversation").string			toUse (Forbidden,		"conversationId missing")	map ConversationId.apply
					clientCont		<- (data / "clientCont").long				toUse (Forbidden,		"clientCont missing")
					serverCont		<- (data / "serverCont").long				toUse (Forbidden,		"serverCont missing")
					incoming		<- (data / "messages").arraySeq				toUse (Forbidden,		"messages missing")
					conversation	<- context findConversation conversationId	toUse (Disconnected,	"unknown conversation")
				}
				yield {
					conversation.touch()
					
					// tell the client it's alive
					conversation.handleHeartbeat()
					
					// give new messages to the client
					conversation handleIncoming (incoming, clientCont)
					
					def compileResponse(batch:Batch):HttpResponse =
							JsonOK(
								jsonObject(
									"clientCont"	-> clientCont,
									"serverCont"	-> batch.serverCont,
									"messages"		-> batch.messages
								)
							)
						
					// maybe there already are new messages, if not, we have to wait
					val fromConversation	= conversation fetchOutgoing serverCont
					if (fromConversation.messages.nonEmpty || incoming.nonEmpty) {
						HttpResponder(compileResponse(fromConversation))
					}
					else {
						val (responder, send)	=
								HttpResponder async (
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
				
		actionLog(action) foreach { ERROR(_:_*) }
		actionResponder(action)
	}
	
	private def bodyString(request:HttpRequest):Tried[Exception,String]	=
			Catch.exception in (request.body readString Constants.encoding)
	
	//------------------------------------------------------------------------------
	//## helper
	
	private val CONNECTED_TEXT		= "OK"
	private val DISCONNECTED_TEXT	= "CONNECT"
	private val UPLOADED_TEXT		= "OK"
	private val UPGRADED_TEXT		= "VERSION"
	
	private val Forbidden:HttpResponder		=
			HttpResponder(EmptyStatus(FORBIDDEN))
	
	private def ClientCode(code:String):HttpResponder	=
			HttpResponder(StringOK(code, text_javascript))
	
	private def Connected(conversationId:ConversationId):HttpResponder	=
			SendPlainTextCharset(CONNECTED_TEXT + " " + conversationId.idval)
			
	private def Upgrade:HttpResponder	=
			SendPlainTextCharset(UPGRADED_TEXT + " " + serverVersion)
	
	private val Disconnected:HttpResponder	=
			SendPlainTextCharset(DISCONNECTED_TEXT)
			
	private def SendPlainTextCharset(s:String):HttpResponder	=
			HttpResponder(StringOK(s, text_plain))
		
	//------------------------------------------------------------------------------

	private def JsonOK(json:JSONValue):HttpResponse	=
			HttpResponse(
				OK,	None,
				NoCache ++
				HeaderValues(
					ContentType(application_json)
				),
				HttpOutput writeString (
					Charsets.utf_8,
					JSONCodec encode json
				)
			)
					
	private def StringOK(text:String, contentType:MimeType):HttpResponse	=
			HttpResponse(
				OK,	None,
				NoCache ++
				HeaderValues(
					ContentType(contentType addParameter ("charset",  Constants.encoding.name))
				),
				HttpOutput writeString (Constants.encoding, text)
			)
			
	private def EmptyStatus(status:HttpStatus):HttpResponse	=
			HttpResponse(
				status,	None,
				NoCache,
				HttpOutput.empty
			)
}
