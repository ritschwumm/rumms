package rumms

import java.io.InputStreamReader
import java.io.IOException

import javax.servlet._
import javax.servlet.http._

import scala.collection.JavaConverters._

import scutil.lang._
import scutil.Implicits._
import scutil.log._

import scjson._
import scjson.codec._
import scjson.JSONNavigation._

import scwebapp._
import scwebapp.implicits._
import scwebapp.instances._
import scwebapp.status._

object RummsServlet {
	private val controllerParamName	= "controller"
	private val servletPath			= "/rumms"
}

/**
delegates incoming requests to new Controller instances 
mount this with an url-pattern of /rumms/STAR (where STAR is a literal "*")
*/
final class RummsServlet extends HttpServlet with Logging {
	//------------------------------------------------------------------------------
	//## handler dsl
	
	private type Action[T]	= Tried[(HttpResponder,Problem),T]
	
	private def actionLog(action:Action[Any]):Option[Seq[Any]]	=
			action.swap.toOption map { _._2.loggable }
		
	private def actionResponder(action:Action[HttpResponder]):HttpResponder	=
			action cata (_._1, identity)
	
	private sealed trait Problem {
		def loggable:Seq[Any]
	}
	private case class PlainProblem(message:String) extends Problem {
		def loggable:Seq[Any]	= Seq(message)
	}
	private case class ExceptionProblem(message:String, exception:Exception) extends Problem {
		def loggable:Seq[Any]	= Seq(message, exception)
	}
	
	private def HttpPartsProblem(it:HttpPartsProblem):Problem	=
			it match {
				case NotMultipart(e)		=> ExceptionProblem("not a multipart request", e)
				case InputOutputFailed(e)	=> ExceptionProblem("io failure", e)
				case SizeLimitExceeded(e)	=> ExceptionProblem("size limits exceeded", e)
			}
				
	private implicit class ProblematicTriedParts[W](peer:Tried[HttpPartsProblem,W]) {
		def toUse(responder:HttpResponder):Action[W]	=
				peer withSwapped { _ map { pp:HttpPartsProblem =>
					(responder, HttpPartsProblem(pp))
				} }
	} 
	
	private implicit class ProblematicTried[F<:Exception,W](peer:Tried[F,W]) {
		def toUse(responder:HttpResponder, text:String):Action[W]	=
				peer withSwapped { _ map { e => (responder, ExceptionProblem(text, e)) } }
	} 
	
	private implicit class ProblematicOption[W](peer:Option[W]) {
		def toUse(responder:HttpResponder, text:String):Action[W]	=
				peer toWin (responder, PlainProblem(text))
	} 
	
	private def triedIOException[T](block: =>T):Tried[IOException,T]	=
			try { Win(block) }
			catch { case e:IOException	=> Fail(e) }
			
	//------------------------------------------------------------------------------
	//## life cycle
	
	@volatile 
	private var controller:Controller	= null
	
	override def init() {
		INFO("initializing")
		val className	= 
				getServletConfig					initParamString 
				RummsServlet.controllerParamName	getOrError 
				"missing init parameter ${RummsServlet.controllerParamName}"
		INFO("loading controller", className)
		controller	=
				try {
					(
						Class						forName 
						className					getConstructor 
						classOf[ControllerContext]	newInstance 
						controllerContext
					).asInstanceOf[Controller]
				}
				catch { case e:Exception	=>
					INFO("cannot load controller", e)
					throw e
				}
		INFO("controller loaded")
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
		def downloadURL(receiver:ConversationId, message:JSONValue):String =
				servletPrefix		+
				"/download"			+
				"?conversation="	+ (URIComponent encode receiver.idval) +
				"&message="			+ (message |> JSONCodec.encode |> URIComponent.encode)
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
			
			// TODO ugly
			request		setEncoding	Config.encoding
			response	setEncoding	Config.encoding
			response	noCache		()
			
			// TODO ugly hack
			// NOTE context path is statically available as getServletContext.getContextPath
			servletPrefix	= request.getContextPath + request.getServletPath
			
			// TODO let those return a responder
			request.pathInfoUTF8 match {
				case Some("/code")		=> code(request, response)
				case Some("/hi")		=> hi(request, response)
				case Some("/comm")		=> comm(request, response)
				case Some("/upload")	=> upload(request, response)
				case Some("/download")	=> download(request, response)
				case _					=> NotFound(response)
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
				val code		= JSONCodec encode value
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
		val action:Action[HttpResponder]	=
				for {
					data			<- request.getReader use { _.readFully } into JSONCodec.decode	toUse (Forbidden,		"invalid message")
					conversationId	<- (data / "conversation").string								toUse (Forbidden,		"conversationId missing")	map ConversationId.apply
					clientCont		<- (data / "clientCont").long									toUse (Forbidden,		"clientCont missing")
					serverCont		<- (data / "serverCont").long									toUse (Forbidden,		"serverCont missing")
					incoming		<- (data / "messages").arraySeq									toUse (Forbidden,		"messages missing")
					conversation	<- conversations use conversationId								toUse (Disconnected,	"unknown conversation")
				}
				yield {
					conversation.remoteUser	= request.remoteUser
					
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
						
						Pass
					}
				}
				
		actionLog(action) foreach { ERROR(_:_*) }
		actionResponder(action) apply response
	}
	
	//------------------------------------------------------------------------------
	//## file transfer
	
	/** upload a file to be played */
	private def upload(request:HttpServletRequest, response:HttpServletResponse) {
		def stringValue(part:Part):String	=
				new InputStreamReader(part.getInputStream(), Config.encoding.name) use { _ readFully }
			
		def fileNames(part:Part):Seq[String]	=
				for {
					header			<- (part getHeader "content-disposition").guardNotNull.toVector
					snip			<- header splitAroundChar ';'
					(name,value)	<- snip.trim splitAroundFirst '='
					if name == "filename"
				}
				// TODO see http://tools.ietf.org/html/rfc2184 for non-ascii
				yield value replaceAll ("^\"|\"$", "")
				
		def handleFile(conversation:Conversation, message:JSONValue)(part:Part):Action[Boolean]	=
				for {
					// TODO wrong message with multiple file names
					fileName	<- fileNames(part).singleOption				toUse (Forbidden, "filename missing")
					mimeType	= (part getHeader "content-type").guardNotNull flatMap MimeType.parse getOrElse unknown_unknown 
					size		= part.getSize
					// TODO files with "invalid encoding" (of their name) produce a length of 417 - don't add them!
					// NOTE with HTML5 this can be checked in the client by accessing the files's size which throws an exception in these cases 
					stream		<- triedIOException(part.getInputStream)	toUse (Forbidden, s"upload stream failed for ${fileName}")
					content		= Content(mimeType, size, stream)
					// TODO ugly stream.use
					upload		= thunk { stream use { _ => conversation uploadContent (message, content, fileName) } }
					accepted	<- Tried catchException upload()	toUse (Forbidden, s"upload stream failed for ${fileName}")
				}
				yield accepted
			
		val action:Action[HttpResponder]	=
				// NOTE these are not in the order of the request in jetty 8.1.5
				for {
					parts				<- request.parts												toUse (Forbidden)
					conversationPart	<- (parts filter { _.getName == "conversation" }).singleOption	toUse (Forbidden,		"multiple conversation parts encountered")
					conversationId		=  conversationPart |> stringValue |> ConversationId.apply
					conversation		<- conversations use conversationId								toUse (Disconnected,	"unknown conversation")
					messagePart			<- (parts filter { _.getName == "message" }).singleOption		toUse (Forbidden,		"multiple message parts encountered")
					message				<- messagePart |> stringValue |> JSONCodec.decode				toUse (Forbidden,		"cannot parse message")
					outcome				<- {
						val subActions:Seq[Action[Boolean]]	=
								parts filter { _.getName ==== "file" } map handleFile(conversation, message)
								
						conversation.uploadBatchCompleted()
						
						// TODO check
						subActions.sequenceTried map { _ => Uploaded }
					}
				}
				yield outcome
			
		actionLog(action) foreach { ERROR(_:_*) }
		actionResponder(action) apply response
	}
	
	private def download(request:HttpServletRequest, response:HttpServletResponse) {
		val action:Action[HttpResponder]	=
				for {
					conversationId	<- request	paramString "conversation"		toUse (Forbidden, 		"conversation missing")	map ConversationId.apply
					message			<- request	paramString	"message"			toUse (Forbidden, 		"message missing")
					messageJS		<- JSONCodec decode message					toUse (Forbidden,		"cannot parse message")
					conversation	<- conversations use conversationId			toUse (Disconnected,	"unknown conversation")
					// TODO ugly
					_				= { conversation.remoteUser	= request.remoteUser }
					content			<- conversation downloadContent messageJS	toUse (NotFound,		"content not found")
				}
				yield SendContent(content)
				
		actionLog(action) foreach { ERROR(_:_*) }
		actionResponder(action) apply response
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
}
