package rumms

import java.io.InputStreamReader
import java.net.URLEncoder
import javax.servlet.http._

import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.util.Streams

import org.eclipse.jetty.continuation.ContinuationSupport

import scutil.Implicits._
import scutil.Types._
import scutil.Functions._
import scutil.Resource._
import scutil.log.Logging

import scjson._
import scjson.JSNavigation._

import scwebapp.MimeType
import scwebapp.HttpStatusCodes._
import scwebapp.HttpImplicits._

import rumms.util.Expiring

/** delegates incoming requests to new FrontHandler instances */
final class FrontServlet extends HttpServlet with Logging {
	//------------------------------------------------------------------------------
	//## life cycle
	
	val CONTROLLER	= "controller"
	
	@volatile var controller:Controller	= null
	
	override def init() {
		val className	= getInitParameter(CONTROLLER)
		INFO("loading controller", className)
		try {
			controller	= (Class forName className getConstructor classOf[ControllerContext] newInstance controllerContext).asInstanceOf[Controller]
			INFO("controller loaded")
		}
		catch {
			case e	=>
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
	
	private val conversations	= new Expiring[ConversationId,Conversation](Config.conversationTTL)
	
	def createConversation(remoteUser:Option[String]):ConversationId = {
		val	conversationId	= ConversationId.next
		val conversation	= new Conversation(conversationId, controller)
		conversation.remoteUser	= remoteUser
		synchronized {
			conversations put (conversationId, conversation)
		}
		controller conversationAdded conversationId
		conversationId
	}
	
	def getConversation(conversationId:ConversationId):Option[Conversation] =  {
		synchronized { 
			conversations get conversationId
		}
	}
	
	def expireConversations() {
		synchronized { 
			conversations.expire() 
		} 
		.foreach {
			controller conversationRemoved _.id 
		}
	}
	
	val controllerContext	= new ControllerContext {
		def sendMessage(conversationId:ConversationId, message:JSValue) {
			getConversation(conversationId) foreach { _ appendOutgoing message }
		}
		def downloadURL(conversationId:ConversationId, message:JSValue):String = {
			def urlEncode(s:String):String	= URLEncoder encode (s, Config.encoding.name)
			servletPrefix		+
			"/download"			+
			"?conversation="	+ urlEncode(conversationId.idval) +
			"&message="			+ urlEncode(JSMarshaller apply message)
		}
		def remoteUser(conversationId:ConversationId):Option[String]	=
				getConversation(conversationId) flatMap { _.remoteUser }	
	}
	
	//------------------------------------------------------------------------------
	//## request handling
	
	val CONNECTED_TEXT		= "OK"
	val DISCONNECTED_TEXT	= "CONNECT"
	val UPLOADED_TEXT		= "OK"
	val UPGRADED_TEXT		= "VERSION"
	
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
			/*
			println("### getContextPath="	+ request.getContextPath)	// ""
			println("### getServletPath="	+ request.getServletPath)	// "/rumms"
			println("### getPathInfo="		+ request.getPathInfo)		// "/comm"
			println("### servletPrefix="	+ servletPrefix)
			*/
			
			request.getPathInfo match {
				case "/code"		=> code(request, response)
				case "/hi"			=> hi(request, response)
				case "/comm"		=> comm(request, response)
				case "/upload"		=> upload(request, response)
				case "/download"	=> download(request, response)
				case _				=> response setStatus NOT_FOUND
			}
		}
		catch {
			case e => 
				ERROR(e)
				throw e
		}
	}
	
	//------------------------------------------------------------------------------
	//## code transfer
	
	case class RawJS(code:JSValue)
	
	private lazy val clientCode	= {
		val path	= "/rumms/Client.js" 
		val stream	= getClass getResourceAsStream path nullError ("cannot access resource " + path)
		val raw		= stream use { stream => new InputStreamReader(stream, Config.encoding.name).readFully }
		configure(raw, Map(
			"VERSION"			-> serverVersion,
			"ENCODING"			-> Config.encoding.name,
			"CLIENT_TTL"		-> Config.clientTTL.millis,
			"SERVLET_PREFIX"	-> servletPrefix,
			"USER_DATA"			-> RawJS(controller.userData)
		))
	}
	
	/** patch raw code by replacing @{id} tags */
	private def configure(raw:String, params:Map[String,Any]):String =
			params.foldLeft(raw){ (raw,param) =>
				val pattern	= "@{" + param._1 + "}"
				val json	=  param._2 match {
					case RawJS(value)	=> value
					case value			=> JSMapper write param._2
				}
				val code	= JSMarshaller apply json
				raw replace (pattern, code)
			}
	
	/** send javascript code for client configuration */
	private def code(request:HttpServletRequest, response:HttpServletResponse) {
		// TODO allow caching?
		response setContentType MimeTypes.textJavascript
		response sendString clientCode
	}
		
	lazy val serverVersion:String	= Config.version.toString + "/" + controller.version
	
	//------------------------------------------------------------------------------
	//## message transfer
	
	/** establish a new Conversation */
	private def hi(request:HttpServletRequest, response:HttpServletResponse) {
		// BETTER send JSON data here
		response setContentType MimeTypes.textPlain
		val	clientVersion = request.getReader use { _.readFully }
		clientVersion match {
			case `serverVersion`	=>
				val	conversationId	= createConversation(request.getRemoteUser.guardNotNull)
				response sendString (CONNECTED_TEXT + " " + conversationId.idval)
			case _	=>
				response sendString (UPGRADED_TEXT + " " + serverVersion)
		}
	}
	
	private val continuationAttribute	= "CONT"
	
	/** receive and send messages for a single Conversation */
	private def comm(request:HttpServletRequest, response:HttpServletResponse) {
		val continuation	= ContinuationSupport getContinuation request
		
		if (continuation.isInitial) {
			// DEBUG("initial continuation, reading data from request", continuation)
			
			val dataStr	= request.getReader use { _.readFully }
			
			// parse batch message
			val data			= JSMarshaller unapply dataStr
			val conversationId	= (data / "conversation"	string)		getOrElse { INFO("conversationId missing");	response setStatus FORBIDDEN;	return }
			val	clientCont		= (data / "clientCont"		long)		getOrElse { INFO("clientCont missing");		response setStatus FORBIDDEN;	return }
			val	serverCont		= (data / "serverCont"		long)		getOrElse { INFO("serverCont missing");		response setStatus FORBIDDEN;	return }
			val incoming		= (data / "messages"		arraySeq)	getOrElse { INFO("messages missing");		response setStatus FORBIDDEN;	return }
			
			val conversation	= getConversation(ConversationId(conversationId)) getOrElse { response setContentType MimeTypes.textPlain; response sendString DISCONNECTED_TEXT; 	return }			
			conversation.remoteUser	= request.remoteUser
			
			// give new messages to the client
			conversation handleIncoming (incoming, clientCont)
		
			def compileResponse(batch:Batch):String =
					JSMarshaller apply JSObject(Map(
						JSString("clientCont")	-> JSNumber(clientCont),
						JSString("serverCont")	-> JSNumber(batch.serverCont),
						JSString("messages")	-> JSArray(batch.messages)
					))
				
			// maybe there already are new messages
			val fromConversation	= conversation fetchOutgoing serverCont
			if (fromConversation.messages.nonEmpty) {
				// DEBUG("sending available data immediately", continuation)
				response setContentType MimeTypes.applicationJSON
				response sendString compileResponse(fromConversation)
			}
			else {
				val responseThunk:Thunk[String]	= thunk {
					val fromLater	= conversation fetchOutgoing serverCont
					compileResponse(fromLater)
				}
				// DEBUG("suspending continuation to delay response", continuation)
				// fetch again later and resume the continuation
				continuation.setTimeout(Config.continuationTTL.millis)
				continuation setAttribute (continuationAttribute, responseThunk)
				continuation.suspend()
				conversation onHasOutgoing thunk {
					// DEBUG("resuming continuation", continuation)
					continuation.resume()
				}
			}
		}
		else if (continuation.isResumed || continuation.isExpired) {
			val responseThunk:Thunk[String]	= (request getAttribute continuationAttribute).asInstanceOf[Thunk[String]]
			if (responseThunk != null) {
				// DEBUG("resumed continuation, using fetcher", continuation)
				response setContentType MimeTypes.applicationJSON
				response sendString responseThunk()
			}
			else {
				// DEBUG("resumed continuation with null responseThunk!!!", continuation)
			}
		}
		else {
			WARN("unexpected continuation state", continuation)
		}
	}
	
	//------------------------------------------------------------------------------
	//## file transfer
	
	/** upload a file to be played */
	private def upload(request:HttpServletRequest, response:HttpServletResponse) {
		if (!(ServletFileUpload isMultipartContent request))	{ response setStatus FORBIDDEN;	return }
		
		var	fields:Map[String,String]				= Map.empty
		var conversationOpt:Option[Conversation]	= None
		var messageJSOpt:Option[JSValue]			= None
		
		val fit	= new ServletFileUpload getItemIterator request
		while (fit.hasNext) {
			val	item	= fit.next
			val	name	= item.getFieldName
			val stream	= item.openStream()
			
			if (item.isFormField) {
				val	value	= Streams asString (stream, "UTF-8")	// TODO hardcoded!
				// DEBUG("form field " + name + " value " + value)
				fields	= fields + (name -> value)
				
				if (name == "conversation") {
					conversationOpt	= getConversation(ConversationId(value))
					if (conversationOpt.isEmpty) { response setContentType MimeTypes.textPlain; response sendString DISCONNECTED_TEXT;	return }
					conversationOpt foreach{ conversation =>
						conversation.remoteUser	= request.remoteUser
					}
				}
				else if (name == "message") {
					messageJSOpt	= JSMarshaller unapply value
					if (messageJSOpt.isEmpty) { response setStatus FORBIDDEN;	return }
				}
				else {
					WARN("unexpected form parameter", name)
				}
			}
			else {
				// TODO log errors
				val conversation	= conversationOpt	getOrElse { response setStatus FORBIDDEN;	return }
				val messageJS		= messageJSOpt		getOrElse { response setStatus FORBIDDEN;	return }
				val	size			= request.getContentLength
				val mimeType		= item.getContentType.guardNotNull flatMap MimeType.parse getOrElse MimeTypes.unknown
				val fileName		= item.getName
				
				if (size == -1)	{
					ERROR("upload stream was zero-sized", fileName)
					response setStatus FORBIDDEN
					return 
				}
				
				// TODO files with "invalid encoding" (of their name) produce a length of 417 - don't add them!
				// NOTE with HTML5 this can be checked in the client by accessing the files's size which throws an exception in these cases 
				// if (!Validation.uploadSize(blob.size)) {
				// 	DEBUG("upload with invalid size swallowed")
				// 	drain()
				// 	return
				// }
				
				// DEBUG("upload stream running", fileName, mimeType)
				
				val content	= Content(mimeType, size, stream)
				val accepted	= 
						try {
							conversation uploadContent (messageJS, content, fileName)
						}
						catch {
							case e =>
								ERROR("upload stream failed", fileName, e)
								response setStatus FORBIDDEN
								return
						}
				try { stream.close() }
				catch { case e	=> /* may already be closed */ }
				
				// DEBUG("upload stream done", fileName, accepted)
			}
		}
		
		conversationOpt foreach { _.uploadBatchCompleted() }
		response setContentType MimeTypes.textPlain
		response sendString UPLOADED_TEXT
	}
	
	private def download(request:HttpServletRequest, response:HttpServletResponse) {
		// TODO log errors, use Validated
		val conversationId	= request	paramString "conversation"	getOrElse { response setStatus FORBIDDEN;	return }
		val message			= request	paramString	"message"		getOrElse { response setStatus FORBIDDEN;	return }
		val messageJS		= JSMarshaller unapply message			getOrElse { response setStatus FORBIDDEN;	return }
		val conversation	= getConversation(ConversationId(conversationId)) getOrElse { response setContentType MimeTypes.textPlain; response sendString DISCONNECTED_TEXT; 	return }
		conversation.remoteUser	= request.remoteUser
		val	content			= conversation downloadContent messageJS

		content match {
			// TODO catch exceptions for closed connections
			case Some(content)	=> 
					response setContentType 	content.mimeType
					response setContentLength	content.contentLength
					response sendStream			content.inputStream
			case None	=> 
					response setStatus 		NOT_FOUND
		}
	}
}
