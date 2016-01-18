package rumms
package impl

import java.io._
import java.nio.charset.Charset

import scutil.lang._
import scutil.implicits._
import scutil.log._

import scjson._
import scjson.codec._
import scjson.syntax._
import scjson.serialization._
import scjson.JSONNavigation._

import scwebapp._
import scwebapp.instances._
import scwebapp.status._

import rumms.impl.HandlerUtil._

/** mount this with an url-pattern of /rumms/STAR (where STAR is a literal "*") */
final class RummsHandler(application:RummsApplication, configuration:RummsConfiguration) extends Logging {
	import Constants.paths
	
	private val serverVersion	=
			Constants.version.toString + "/" + configuration.version
		
	//------------------------------------------------------------------------------
	//## request handling
	
	lazy val plan:HttpHandler	=
			request =>
			try {
				application.expireConversations()
				planImpl(request)
			}
			catch { case e:Exception =>
				ERROR(e)
				throw e
			}
			
	private lazy val planImpl:HttpHandler	=
			(PathInfoUTF8(paths.code)	guardOn	code)	orElse
			(PathInfoUTF8(paths.hi)		guardOn	hi)		orElse
			(PathInfoUTF8(paths.comm)	guardOn	comm)	orAlways
			constant(HttpResponder(EmptyStatus(NOT_FOUND)))
	
	//------------------------------------------------------------------------------
	//## code transfer
	
	/** send javascript code for client configuration */
	private def code(request:HttpRequest):HttpResponder	= {
		val servletPrefix	= request.contextPath + request.servletPath
		ClientCode(clientCode(servletPrefix))
	}
		
	private def clientCode(servletPrefix:String):String	= {
		val resource	= "/rumms/Client.js"
		val stream		= getClass getResourceAsStream resource nullError so"cannot access resource ${resource}"
		val raw			= stream use { stream => new InputStreamReader(stream, Constants.encoding.name).readFully }
		configure(raw, Map(
			"VERSION"			-> JSONString(serverVersion),
			"ENCODING"			-> JSONString(Constants.encoding.name),
			"CLIENT_TTL"		-> JSONNumber(Constants.clientTTL.millis),
			"SERVLET_PREFIX"	-> JSONString(servletPrefix),
			"USER_DATA"			-> configuration.userData
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
	
	private object Protocol
			extends	NativeProtocol
			with	ISeqProtocol
			with	IdentityProtocol
	import Protocol._
	
	/** establish a new Conversation */
	private def hi(request:HttpRequest):HttpResponder	= {
		// BETTER send JSON data here
		val action	=
				for {
					clientVersion	<- bodyString(request)	toUse (Forbidden,	"unreadable message")
				}
				yield clientVersion == serverVersion cata (
					Upgrade,
					Connected(application.createConversation())
				)
					
		actionLog(action) foreach { ERROR(_:_*) }
		actionResponder(action)
	}
	
	/** receive and send messages for a single Conversation */
	private def comm(request:HttpRequest):HttpResponder	= {
		val action:Action[HttpResponder]	=
				for {
					json			<- bodyString(request)							toUse (Forbidden,		"unreadable message")
					data			<- JSONCodec decode json						toUse (Forbidden,		"invalid message")
					// TODO ugly
					conversationId	<- (data / "conversation").string				toUse (Forbidden,		"conversationId missing")	map ConversationId.apply
					clientCont		<- (data / "clientCont").long					toUse (Forbidden,		"clientCont missing")
					serverCont		<- (data / "serverCont").long					toUse (Forbidden,		"serverCont missing")
					incoming		<- (data / "messages").arraySeq					toUse (Forbidden,		"messages missing")
					conversation	<- application useConversation conversationId	toUse (Disconnected,	"unknown conversation")
				}
				yield {
					// tell the client it's alive
					conversation.handleHeartbeat()
					
					// give new messages to the client
					conversation handleIncoming (incoming, clientCont)
					
					def compileResponse(batch:Batch):HttpResponse =
							JsonOK(
								JSONCodec encode jsonObject(
									"clientCont"	-> clientCont,
									"serverCont"	-> batch.serverCont,
									"messages"		-> batch.messages
								)
							)
						
					// maybe there already are new messages
					val fromConversation	= conversation fetchOutgoing serverCont
					// incoming messages should not block the receiver
					if (fromConversation.messages.nonEmpty || incoming.nonEmpty) {
						// DEBUG("sending available data immediately", continuation)
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
	
	private implicit class MimeTypeExt(peer:MimeType) {
		def withCharset(charset:Charset):MimeType	=
				peer addParameter ("charset", charset.name)
	}
	
	private val CONNECTED_TEXT		= "OK"
	private val DISCONNECTED_TEXT	= "CONNECT"
	private val UPLOADED_TEXT		= "OK"
	private val UPGRADED_TEXT		= "VERSION"
	
	private val Forbidden:HttpResponder		= HttpResponder(EmptyStatus(FORBIDDEN))
	
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

	private def JsonOK(s:String):HttpResponse	=
			StringOK(s, application_json)
					
	private def EmptyStatus(status:HttpStatus):HttpResponse	=
			HttpResponse(
				status,	None,
				NoCache,
				HttpOutput.empty
			)
			
	private def StringOK(text:String, contentType:MimeType):HttpResponse	=
			HttpResponse(
				OK,	None,
				NoCache ++
				Vector(
					ContentType(contentType	withCharset Constants.encoding)
				),
				HttpOutput writeString (Constants.encoding, text)
			)
}
