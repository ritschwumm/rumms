package rumms

import java.io.InputStreamReader
import java.io.IOException
import java.net.URLEncoder
import javax.servlet._
import javax.servlet.http._

import scala.collection.JavaConverters._

import scutil.lang._
import scutil.Implicits._
import scutil.log._

import scjson._
import scjson.codec._
import scjson.JSONNavigation._

import scwebapp.MimeType
import scwebapp.HttpResponder
import scwebapp.HttpStatusEnum._
import scwebapp.HttpImplicits._
import scwebapp.HttpInstances._
import scwebapp.StandardMimeTypes._

/** delegates incoming requests to new Controller instances */
final class RummsServlet extends HttpServlet with Logging {
	//------------------------------------------------------------------------------
	//## life cycle
	
	private val CONTROLLER_PARAMETER	= "controller"
	
	@volatile var controller:Controller	= null
	
	override def init() {
		val className	= getInitParameter(CONTROLLER_PARAMETER)
		INFO("loading controller", className)
		try {
			controller	= (Class forName className getConstructor classOf[ControllerContext] newInstance controllerContext).asInstanceOf[Controller]
			INFO("controller loaded")
		}
		catch { case e:Exception	=>
			INFO("cannot load controller", e)
			throw e
		}
	}
	override def destroy() {
		controller.dispose()
		controller	= null
	}
	
	//------------------------------------------------------------------------------
	//## conversation management
	
	private val conversations	= new ConversationManager
	
	private def createConversation(remoteUser:Option[String]):ConversationId = {
		val	conversationId	= ConversationId.next
		val conversation	= new Conversation(conversationId, controller)
		conversation.remoteUser	= remoteUser
		conversations put conversation
		controller conversationAdded conversationId
		conversationId
	}
	
	private def expireConversations() {
		conversations.expire().map { _.id } foreach controller.conversationRemoved
	}
	
	private val controllerContext	= new ControllerContext {
		def sendMessage(receiver:ConversationId, message:JSONValue) {
			conversations get receiver foreach { _ appendOutgoing message }
		}
		def broadcastMessage(receiver:Predicate[ConversationId], message:JSONValue) {
			conversations find receiver foreach { _ appendOutgoing message }
		}
		def downloadURL(receiver:ConversationId, message:JSONValue):String = {
			def urlEncode(s:String):String	= URLEncoder encode (s, Config.encoding.name)
			servletPrefix		+
			"/download"			+
			"?conversation="	+ urlEncode(receiver.idval) +
			"&message="			+ urlEncode(encodeJSON(message))
		}
		def remoteUser(conversationId:ConversationId):Option[String]	=
				conversations get conversationId flatMap { _.remoteUser }	
	}
	
	//------------------------------------------------------------------------------
	//## request handling
	
	// TODO ugly hack
	@volatile private var servletPrefix:String	= _
	
	override def doGet(request:HttpServletRequest, response:HttpServletResponse) {
		handle(request, response)
	}
	
	override def doPost(request:HttpServletRequest, response:HttpServletResponse) {
		handle(request, response)
	}
	
	private def handle(request:HttpServletRequest, response:HttpServletResponse) {
		try {
			expireConversations()
			
			request		setEncoding	Config.encoding
			response	setEncoding	Config.encoding
			response	noCache		()
			
			// TODO ugly hack
			servletPrefix	= request.getContextPath + request.getServletPath
			
			// TODO let those return a responder
			request.getPathInfo match {
				case "/code"		=> code(request, response)
				case "/hi"			=> hi(request, response)
				case "/comm"		=> comm(request, response)
				case "/upload"		=> upload(request, response)
				case "/download"	=> download(request, response)
				case _				=> NotFound(response)
			}
		}
		catch { case e:Exception => 
			ERROR(e)
			throw e
		}
	}
	
	//------------------------------------------------------------------------------
	//## code transfer
	
	private def clientCode	= {
		val path	= "/rumms/Client.js" 
		val stream	= getClass getResourceAsStream path nullError ("cannot access resource " + path)
		val raw		= stream use { stream => new InputStreamReader(stream, Config.encoding.name).readFully }
		configure(raw, Map(
			"VERSION"			-> JSONString(serverVersion),
			"ENCODING"			-> JSONString(Config.encoding.name),
			"CLIENT_TTL"		-> JSONNumber(Config.clientTTL.millis),
			"SERVLET_PREFIX"	-> JSONString(servletPrefix),
			"USER_DATA"			-> controller.userData
		))
	}
	
	/** patch raw code by replacing @{id} tags */
	private def configure(raw:String, params:Map[String,JSONValue]):String =
			params.foldLeft(raw){ (raw,param) =>
				val (key,value)	= param
				val pattern		= "@{" + key + "}"
				val code		= encodeJSON(value)
				raw replace (pattern, code)
			}
	
	/** send javascript code for client configuration */
	private def code(request:HttpServletRequest, response:HttpServletResponse) {
		ClientCode(response)
	}
		
	private def serverVersion:String	= Config.version.toString + "/" + controller.version
	
	//------------------------------------------------------------------------------
	//## message transfer
	
	/** establish a new Conversation */
	private def hi(request:HttpServletRequest, response:HttpServletResponse) {
		val	clientVersion = request.getReader use { _.readFully }
		// BETTER send JSON data here
		val version	= serverVersion
		val action	= clientVersion match {
			case `version`	=> Connected(createConversation(request.getRemoteUser.guardNotNull))
			case _			=> Upgrade
		}
		action apply response
	}
	
	/** receive and send messages for a single Conversation */
	private def comm(request:HttpServletRequest, response:HttpServletResponse) {
		val dataStr	= request.getReader use { _.readFully }
		
		val action	=
				for {
					data			<- dataStr |> decodeJSON			toWin (Forbidden,		"invalid message")
					conversationId	<- (data / "conversation").string	toWin (Forbidden,		"conversationId missing")	map ConversationId.apply
					clientCont		<- (data / "clientCont").long		toWin (Forbidden,		"clientCont missing")
					serverCont		<- (data / "serverCont").long		toWin (Forbidden,		"serverCont missing")
					incoming		<- (data / "messages").arraySeq		toWin (Forbidden,		"messages missing")
					conversation	<- conversations use conversationId	toWin (Disconnected,	"unknown conversation")
				}
				yield {
					conversation.remoteUser	= request.remoteUser
					
					// give new messages to the client
					conversation handleIncoming (incoming, clientCont)
					
					def compileResponse(batch:Batch):String =
							encodeJSON(JSONVarObject(
								"clientCont"	-> JSONNumber(clientCont),
								"serverCont"	-> JSONNumber(batch.serverCont),
								"messages"		-> JSONArray(batch.messages)
							))
						
					// maybe there already are new messages
					val fromConversation	= conversation fetchOutgoing serverCont
					if (fromConversation.messages.nonEmpty) {
						// DEBUG("sending available data immediately", continuation)
						BatchRespose(compileResponse(fromConversation))
					}
					else {
						val asyncCtx	= request.startAsync()
						asyncCtx setTimeout Config.continuationTTL.millis
						
						def sendBack() {
							val	asyncResponse	= asyncCtx.getResponse.asInstanceOf[HttpServletResponse]
							BatchRespose(compileResponse(conversation fetchOutgoing serverCont)) apply asyncResponse
							asyncCtx.complete()
						}
						
						@volatile var alive		= true
						// all throws IOException
						asyncCtx addListener new AsyncListener {
							def onStartAsync(ev:AsyncEvent)	{}
							def onComplete(ev:AsyncEvent)	{ 
								alive	= false	
							}
							def onTimeout(ev:AsyncEvent)	{ 
								alive	= false
								val	asyncResponse	= asyncCtx.getResponse.asInstanceOf[HttpServletResponse]
								BatchRespose(compileResponse(conversation fetchOutgoing serverCont)) apply asyncResponse
								asyncCtx.complete()
							}	
							def onError(ev:AsyncEvent)		{
								alive	= false
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
						
						Ignore
					}
				}
				
		action.swap map { _._2 } foreach (ERROR(_))
		action cata (_._1, identity) apply response 
	}
	
	//------------------------------------------------------------------------------
	//## file transfer
	
	/** upload a file to be played */
	private def upload(request:HttpServletRequest, response:HttpServletResponse) {
		def stringValue(part:Part):String	=
				new InputStreamReader(part.getInputStream(), Config.encoding.name) use { _ readFully }
			
		def fileNames(part:Part):Seq[String]	=
				for {
					header			<- (part getHeader "content-disposition").guardNotNull.toSeq
					snip			<- header splitAround ";"
					(name,value)	<- snip.trim splitAroundFirst '='
					if name == "filename"
				}
				// TODO see http://tools.ietf.org/html/rfc2184 for non-ascii
				yield value replaceAll ("^\"|\"$", "")
				
		case class Problem(message:String, exception:Option[Exception] = None)
				
		def handleFile(conversation:Conversation, message:JSONValue)(part:Part):Tried[(HttpResponder,Problem),Boolean]	=
				for {
					// TODO wrong message with multiple file names
					fileName	<- fileNames(part).singleOption toWin (Forbidden, Problem("filename missing"))
					 mimeType	= (part getHeader "content-type").guardNotNull flatMap MimeType.parse getOrElse unknown_unknown 
					 size		= part.getSize
					 // TODO files with "invalid encoding" (of their name) produce a length of 417 - don't add them!
					 // NOTE with HTML5 this can be checked in the client by accessing the files's size which throws an exception in these cases 
					 stream		<-
							try { 
								Win(part.getInputStream)
							}
							catch { case e:IOException	=>
								Fail(Forbidden, Problem(s"upload stream failed for ${fileName}", Some(e)))
							}
					 content	= Content(mimeType, size, stream)
					 accepted	<-
							try {
								Win(conversation uploadContent (message, content, fileName))
							}
							catch { case e:Exception =>
								Fail(Forbidden, Problem(s"upload stream failed for ${fileName}", Some(e)))
							}
							finally {
								try { stream.close() }
								catch { case e:Exception => () }
							}
				}
				yield accepted
			
		val action:Tried[(HttpResponder,Problem),HttpResponder]	=
				// NOTE these are not in the order of the request in jetty 8.1.5
				for {
					parts	<-
							try {
								Win(request.getParts.asScala.toVector)
							}
							catch { case e:ServletException =>  
								Fail(Forbidden, Problem("cannot get parts", Some(e)))
							}
					conversationPart	<- (parts filter { _.getName == "conversation" }).singleOption	toWin (Forbidden,		Problem("multiple conversation parts encountered"))
					conversationId		=  conversationPart |> stringValue |> ConversationId.apply
					conversation		<- conversations use conversationId								toWin (Disconnected,	Problem("unknown conversation"))
					messagePart			<- (parts filter { _.getName == "message" }).singleOption		toWin (Forbidden,		Problem("multiple message parts encountered"))
					message				<- messagePart |> stringValue |> decodeJSON						toWin (Forbidden,		Problem("cannot parse message"))
					outcome				<- {
						val subActions:Seq[Tried[(HttpResponder,Problem),Boolean]]	=
								parts filter { _.getName == "file" } map handleFile(conversation, message)
								
						conversation.uploadBatchCompleted()
						
						// TODO check
						subActions.sequenceTried map { _ => Uploaded }
					}
				}
				yield outcome
				
		action.swap map { _._2 } foreach {
			case Problem(message, None)				=> ERROR(message)
			case Problem(message, Some(exception))	=> ERROR(message, exception)
		}
		action cata (_._1, identity) apply response
	}
	
	private def download(request:HttpServletRequest, response:HttpServletResponse) {
		val action	=
				for {
					conversationId	<- request	paramString "conversation"		toWin (Forbidden, 		"conversation missing") map ConversationId.apply
					message			<- request	paramString	"message"			toWin (Forbidden, 		"message missing")
					messageJS		<- message |> decodeJSON					toWin (Forbidden, 		"invalid message")
					conversation	<- conversations use conversationId			toWin (Disconnected,	"unknown conversation")
					// TODO ugly
					_				= { conversation.remoteUser	= request.remoteUser }
					content			<- conversation downloadContent messageJS	toWin (NotFound,		"Content not found")
				}
				yield SendContent(content)
				
		action.swap map { _._2 } foreach (ERROR(_))
		action cata (_._1, identity) apply response
	}
	
	//------------------------------------------------------------------------------
	
	private val text_plain_charset		= text_plain		attribute ("charset", Config.encoding.name)
	private val text_javascript_charset	= text_javascript	attribute ("charset", Config.encoding.name)
	
	private val CONNECTED_TEXT		= "OK"
	private val DISCONNECTED_TEXT	= "CONNECT"
	private val UPLOADED_TEXT		= "OK"
	private val UPGRADED_TEXT		= "VERSION"
	
	private val Forbidden		= SetStatus(FORBIDDEN)
	private val NotFound		= SetStatus(NOT_FOUND)
	private val InternalError	= SetStatus(INTERNAL_SERVER_ERROR)
	
	// TODO allow caching?
	private def ClientCode	=
			SetContentType(text_javascript_charset)	~>
			SendString(clientCode)
	
	private def Connected(conversationId:ConversationId)	=
			SetContentType(text_plain_charset)	~>
			SendString(CONNECTED_TEXT + " " + conversationId.idval)
			
	private def Upgrade	=
			SetContentType(text_plain_charset)	~>
			SendString(UPGRADED_TEXT + " " + serverVersion)
	
	private val Disconnected	= 
			SetContentType(text_plain_charset)	~>
			SendString(DISCONNECTED_TEXT)
			
	private def BatchRespose(text:String)	=
			SetContentType(application_json)	~>
			SendString(text)
							
	private def SendContent(content:Content)	=
			SetContentType(content.mimeType)		~>
			SetContentLength(content.contentLength)	~>
			// TODO thunk this in Content, too (???)
			StreamFrom(thunk { content.inputStream })
			
	private val Uploaded	=
			SetContentType(text_plain_charset)	~>
			SendString(UPLOADED_TEXT)
			
	private val Ignore	= (_:HttpServletResponse) => ()
	
	//------------------------------------------------------------------------------
	
	private def encodeJSON(it:JSONValue):String	= 
			JSONCodec encode it
		
	// TODO use the original Fail
	private def decodeJSON(it:String):Option[JSONValue]	= 
			JSONCodec decode it toOption;
}
