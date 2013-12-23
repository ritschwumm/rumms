package rumms
package impl

import java.io._
import java.nio.charset.Charset

import javax.servlet._
import javax.servlet.http._

import scutil.lang._
import scutil.implicits._
import scutil.log._

import scjson._
import scjson.codec._
import scjson.JSONNavigation._

import scwebapp._
import scwebapp.implicits._
import scwebapp.instances._
import scwebapp.status._

import rumms.impl.HandlerUtil._

/** mount this with an url-pattern of /rumms/STAR (where STAR is a literal "*") */
final class RummsServlet(application:RummsApplication, configuration:RummsConfiguration) extends HttpServlet with Logging {
	private val serverVersion	=
			Config.version.toString + "/" + configuration.version
		
	//------------------------------------------------------------------------------
	//## request handling
	
	override def doGet(request:HttpServletRequest, response:HttpServletResponse) {
		handle(request, response)
	}
	
	override def doPost(request:HttpServletRequest, response:HttpServletResponse) {
		handle(request, response)
	}
	
	private def handle(request:HttpServletRequest, response:HttpServletResponse) {
		try {
			application.expireConversations()
			
			// TODO ugly, but changes how parameters are parsed and what getReader does
			request	setEncoding	Config.encoding
			// NOTE this would change the content type, but we send that explicitly
			// response	setEncoding	Config.encoding
			response noCache	()
			
			plan(request)(response)
		}
		catch { case e:Exception => 
			ERROR(e)
			throw e
		}
	}
	
	private val plan:HttpHandler	=
			(PathInfoUTF8("/code")		guardOn	code)		orElse
			(PathInfoUTF8("/hi")		guardOn	hi)			orElse
			(PathInfoUTF8("/comm")		guardOn	comm)		orElse
			(PathInfoUTF8("/upload")	guardOn	upload)		orElse
			(PathInfoUTF8("/download")	guardOn	download)	orAlways
			Respond(NotFound)
	
	//------------------------------------------------------------------------------
	//## code transfer
	
	/** send javascript code for client configuration */
	private def code(request:HttpServletRequest):HttpResponder	= {
		val servletPrefix	= request.getContextPath + request.getServletPath
		ClientCode(clientCode(servletPrefix))
	}
		
	private def clientCode(servletPrefix:String):String	= {
		val path	= "/rumms/Client.js" 
		val stream	= getClass getResourceAsStream path nullError s"cannot access resource ${path}"
		val raw		= stream use { stream => new InputStreamReader(stream, Config.encoding.name).readFully }
		configure(raw, Map(
			"VERSION"			-> JSONString(serverVersion),
			"ENCODING"			-> JSONString(Config.encoding.name),
			"CLIENT_TTL"		-> JSONNumber(Config.clientTTL.millis),
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
	
	/** establish a new Conversation */
	private def hi(request:HttpServletRequest):HttpResponder	= {
		// BETTER send JSON data here
		val action	=
				for {
					clientVersion	<- Catch.exception in (request.getReader use { _.readFully })	toUse (Forbidden,	"unreadable message")
				}
				yield clientVersion == serverVersion cata (
					Upgrade,
					Connected(application.createConversation())
				)
					
		actionLog(action) foreach { ERROR(_:_*) }
		actionResponder(action)
	}
	
	/** receive and send messages for a single Conversation */
	private def comm(request:HttpServletRequest):HttpResponder	= {
		val action:Action[HttpResponder]	=
				for {
					json			<- Catch.exception in (request.getReader use { _.readFully })	toUse (Forbidden,		"unreadable message")
					data			<- JSONCodec decode json										toUse (Forbidden,		"invalid message")
					// TODO ugly
					conversationId	<- (data / "conversation").string								toUse (Forbidden,		"conversationId missing")	map ConversationId.apply
					clientCont		<- (data / "clientCont").long									toUse (Forbidden,		"clientCont missing")
					serverCont		<- (data / "serverCont").long									toUse (Forbidden,		"serverCont missing")
					incoming		<- (data / "messages").arraySeq									toUse (Forbidden,		"messages missing")
					conversation	<- application useConversation conversationId					toUse (Disconnected,	"unknown conversation")
				}
				yield {
					// give new messages to the client
					conversation handleIncoming (incoming, clientCont)
					
					def compileResponse(batch:Batch):String =
							JSONCodec encode JSONVarObject(
								"clientCont"	-> JSONNumber(clientCont),
								"serverCont"	-> JSONNumber(batch.serverCont),
								"messages"		-> JSONArray(batch.messages)
							)
						
					// maybe there already are new messages
					val fromConversation	= conversation fetchOutgoing serverCont
					if (fromConversation.messages.nonEmpty) {
						// DEBUG("sending available data immediately", continuation)
						BatchRespose(compileResponse(fromConversation))
					}
					else {
						val asyncCtx	= request.startAsync()
						asyncCtx setTimeout Config.continuationTTL.millis
						
						// TODO ugly
						@volatile
						var alive	= true
						
						// all throws IOException
						asyncCtx addListener new AsyncListener {
							def onStartAsync(ev:AsyncEvent)	{}
							def onComplete(ev:AsyncEvent)	{ 
								alive	= false	
							}
							def onTimeout(ev:AsyncEvent)	{ 
								alive	= false
								// TODO why send anything here?
								val	asyncResponse	= asyncCtx.getResponse.asInstanceOf[HttpServletResponse]
								BatchRespose(compileResponse(conversation fetchOutgoing serverCont)) apply asyncResponse
								asyncCtx.complete()
							}	
							def onError(ev:AsyncEvent)		{
								alive	= false
								// TODO why send anything here?
								val	asyncResponse	= asyncCtx.getResponse.asInstanceOf[HttpServletResponse]
								InternalError apply asyncResponse
								asyncCtx.complete()
							}
						}
						
						conversation onHasOutgoing thunk {
							if (alive) {
								val	asyncResponse	= asyncCtx.getResponse.asInstanceOf[HttpServletResponse]
								BatchRespose(compileResponse(conversation fetchOutgoing serverCont)) apply asyncResponse
								asyncCtx.complete()
							}
						}
						
						Pass
					}
				}
				
		actionLog(action) foreach { ERROR(_:_*) }
		actionResponder(action)
	}
	
	//------------------------------------------------------------------------------
	//## file transfer
	
	/** upload a file to be played */
	private def upload(request:HttpServletRequest):HttpResponder	= {
		def stringValue(part:Part):String	=
				(part.body encoded Config.encoding).fullString
			
		def getContent(part:Part):Action[Content]	=
				for {
					fileName1	<- part.fileName									toUse  Forbidden
					fileName	<- fileName1										toUse (Forbidden, "expected an existing filename")
					// TODO files with "invalid encoding" (of their name) produce a length of 417 - don't add them!
					// NOTE with HTML5 this can be checked in the client by accessing the files's size which throws an exception in these cases 
				}
				yield Content(
					// TODO questionable
					mimeType		= part.contentType.toOption.flatten getOrElse application_octetStream,
					contentLength	= part.getSize,
					// TODO questionable
					fileName		= Some(fileName),
					inputStream		= thunk { 
						Catch.byType[IOException] in {
							part.getInputStream 
						}
					}
				)
			
		val action:Action[HttpResponder]	=
				// NOTE these are not in the order of the request in jetty 8.1.5
				for {
					parts				<- request.parts												toUse (Forbidden)
					conversationPart	<- (parts filter { _.getName == "conversation" }).singleOption	toUse (Forbidden,		"multiple conversation parts encountered")
					conversationId		=  conversationPart |> stringValue |> ConversationId.apply
					conversation		<- application useConversation conversationId					toUse (Disconnected,	"unknown conversation")
					messagePart			<- (parts filter { _.getName == "message" }).singleOption		toUse (Forbidden,		"multiple message parts encountered")
					message				<- messagePart |> stringValue |> JSONCodec.decode				toUse (Forbidden,		"cannot parse message")
					outcome				<- {
						val subActions:Seq[Action[Content]]	=
								parts filter { _.getName ==== "file" } map getContent
							
						val contents	= 
								subActions flatMap { _.toOption }
							
						try {
							// passes valid contents only
							conversation uploadContents (message, contents)
						}
						catch { case e:Exception =>
							// TODO handle properly
							ERROR(e)
						}
						
						// TODO wrong: this should not be all-or-nothuing
						subActions.sequenceTried map { _ => Uploaded }
					}
				}
				yield outcome
			
		actionLog(action) foreach { ERROR(_:_*) }
		actionResponder(action)
	}
	
	private def download(request:HttpServletRequest):HttpResponder	= {
		val reqParams	= request.parameters
		
		val action:Action[HttpResponder]	=
				for {
					conversationId	<- reqParams	firstString "conversation"		toUse (Forbidden, 		"conversation missing")	map ConversationId.apply
					message			<- reqParams	firstString	"message"			toUse (Forbidden, 		"message missing")
					messageJS		<- JSONCodec decode message						toUse (Forbidden,		"cannot parse message")
					conversation	<- application useConversation conversationId	toUse (Disconnected,	"unknown conversation")
					content			<- conversation downloadContent messageJS		toUse (NotFound,		"content not found")
				}
				yield SendContent(content)
				
		actionLog(action) foreach { ERROR(_:_*) }
		actionResponder(action)
	}
	
	//------------------------------------------------------------------------------
	
	private implicit class MimeTypeExt(peer:MimeType) {
		def withCharset(charset:Charset):MimeType	=
				peer addParameter ("charset", charset.name)
	}
	
	private val CONNECTED_TEXT		= "OK"
	private val DISCONNECTED_TEXT	= "CONNECT"
	private val UPLOADED_TEXT		= "OK"
	private val UPGRADED_TEXT		= "VERSION"
	
	private val Forbidden:HttpResponder		= SetStatus(FORBIDDEN)
	private val NotFound:HttpResponder		= SetStatus(NOT_FOUND)
	private val InternalError:HttpResponder	= SetStatus(INTERNAL_SERVER_ERROR)
	
	// BETTER allow caching?
	private def ClientCode(code:String):HttpResponder	=
			SetContentType(text_javascript	withCharset Config.encoding)	~>
			SendString(code)
	
	private def Connected(conversationId:ConversationId):HttpResponder	=
			SendPlainTextCharset(CONNECTED_TEXT + " " + conversationId.idval)
			
	private def Upgrade:HttpResponder	=
			SendPlainTextCharset(UPGRADED_TEXT + " " + serverVersion)
	
	private val Disconnected:HttpResponder	=
			SendPlainTextCharset(DISCONNECTED_TEXT)
			
	private def BatchRespose(text:String):HttpResponder	=
			SetContentType(application_json)	~>
			SendString(text)
					
	// TODO very ugly
	private def SendContent(content:Content):HttpResponder	=
			HttpResponder { response =>
				content.inputStream()
				.cata (
					e => {
						ERROR(e)
						SetStatus(NOT_FOUND)
					},
					ist => {
						SetContentType(content.mimeType)				~>
						SetContentLength(content.contentLength)			~>
						(content.fileName cata (Pass, SetAttachment))	~>
						StreamFrom(thunk(ist))
					}
				)
				.apply(response)
			}
			
	private def SetAttachment(fileName:String):HttpResponder	=
			AddHeader("Content-Disposition", s"attachment; filename=${HttpUtil quote fileName}")
			
	private val Uploaded:HttpResponder	=
			SendPlainTextCharset(UPLOADED_TEXT)
			
	private def SendPlainTextCharset(s:String):HttpResponder	=
			SetContentType(text_plain withCharset Config.encoding)	~>
			SendString(s)
}
