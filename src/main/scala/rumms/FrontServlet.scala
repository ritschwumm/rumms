package rumms

import java.io.InputStreamReader
import java.net.URLEncoder
import javax.servlet.http._

import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.util.Streams

import org.eclipse.jetty.continuation.ContinuationSupport

import scutil.Resource._
import scutil.Functions._
import scutil.log.Logging
import scutil.ext.AnyRefImplicits._
import scutil.ext.ReaderImplicits._
import scutil.ext.OptionImplicits._
import scutil.ext.StringImplicits._
import scutil.ext.InputStreamImplicits._

import scjson._
import scjson.JSExtract._

import rumms.util.Expiring

import rumms.http._
import rumms.http.HttpImplicits._

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
	
	def createConversation():ConversationId = {
		val	conversationId	= ConversationId.next
		val conversation	= new Conversation(conversationId, controller)
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
			"&message="			+ urlEncode(JSParser unparse message)
		}
	}
	
	//------------------------------------------------------------------------------
	//## request handling
	
	val CONNECTED_TEXT		= "OK"
	val DISCONNECTED_TEXT	= "CONNECT"
	val UPLOADED_TEXT		= "OK"
	val UPGRADED_TEXT		= "VERSION"
	
	private val textPlain		= ContentType("text", 			"plain",		Config.encoding)
	private val textJS			= ContentType("text", 			"javascript",	Config.encoding)
	private val applicationJSON	= ContentType("application",	"json",			Config.encoding)
	
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
			response	setNoCache	()
			
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
				case _				=> response.setStatusNotFound()
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
					case value			=> JSSerialization serialize param._2
				}
				val code	= JSParser unparse json
				raw replace (pattern, code)
			}
	
	/** send javascript code for client configuration */
	private def code(request:HttpServletRequest, response:HttpServletResponse) {
		// TODO allow caching?
		response sendString (textJS, clientCode)
	}
		
	lazy val serverVersion:String	= Config.version.toString + "/" + controller.version
	
	//------------------------------------------------------------------------------
	//## message transfer
	
	/** establish a new Conversation */
	// TODO send JSON data here
	private def hi(request:HttpServletRequest, response:HttpServletResponse) {
		val	clientVersion = request.getReader use { _.readFully }
		clientVersion match {
			case `serverVersion`	=>
				val	conversationId	= createConversation()
				response sendString (textPlain, CONNECTED_TEXT + " " + conversationId.idval)
			case _	=>
				response sendString (textPlain, UPGRADED_TEXT + " " + serverVersion)
		}
	}
	
	/** receive and send messages for a single Conversation */
	private def comm(request:HttpServletRequest, response:HttpServletResponse) {
		val continuationAttribute	= "CONT"
		val readerAttribute			= "READ"
	
		// maybe there's data from the continuation
		val continuationData	= (request getAttribute continuationAttribute).asInstanceOf[String]
		if (continuationData != null) {
			response sendString (applicationJSON, continuationData)
			return
		}
		
		// request reader data is not repeatable across an expired continuation
		val dataStr	= (request getAttribute readerAttribute).asInstanceOf[String].nullOption getOrElse {
			val	tmp	= request.getReader use { _.readFully }
			request setAttribute (readerAttribute, tmp)
			tmp
		}
		
		// parse batch message
		val data			= JSParser parse dataStr
		val conversationId	= (data / "conversation"	string)		getOrElse { response setStatusIllegalParams();	return }
		val	clientCont		= (data / "clientCont"		long)		getOrElse { response setStatusIllegalParams();	return }
		val	serverCont		= (data / "serverCont"		long)		getOrElse { response setStatusIllegalParams();	return }
		val incoming		= (data / "messages"		arraySeq)	getOrElse { response setStatusIllegalParams();	return }
		
		val conversation	= getConversation(ConversationId(conversationId)) getOrElse { response sendString (textPlain, DISCONNECTED_TEXT); 	return }
		
		// give new messages to the client
		conversation handleIncoming (incoming, clientCont)
		
		def compileResponse(batch:Batch):String =
				JSParser unparse JSObject(Map(
					JSString("clientCont")	-> JSNumber(clientCont),
					JSString("serverCont")	-> JSNumber(batch.serverCont),
					JSString("messages")	-> JSArray(batch.messages)
				))
					
		// maybe there already are new messages
		val fromConversation	= conversation fetchOutgoing serverCont
		if (fromConversation.messages.nonEmpty) {
			response sendString (applicationJSON, compileResponse(fromConversation))
			return
		}
		
		// nothing there atm, use a continuation to delay the response
		val continuation	= ContinuationSupport.getContinuation(request)
		
		// If no timeout listeners resume or complete the continuation, 
		// then the continuation is resumed with continuation.isExpired() true
		if (continuation.isExpired) {
			// NOTE the publisher for this continuation is silently overwritten in the Conversation
			response sendString (applicationJSON, compileResponse(fromConversation))
			return
		}
		
		// fetch again later and resume the continuation
		continuation.setTimeout(Config.continuationTTL.millis)
		continuation.suspend()
		conversation onHasOutgoing thunk {
			// the continuation may have expired already
			if (continuation.isSuspended) {
				val fromLater	= conversation fetchOutgoing serverCont
				continuation.setAttribute(continuationAttribute, compileResponse(fromLater))
				continuation.resume()
			}
		}
	}
	
	//------------------------------------------------------------------------------
	//## file transfer
	
	/** upload a file to be played */
	private def upload(request:HttpServletRequest, response:HttpServletResponse) {
		if (!(ServletFileUpload isMultipartContent request))	{ response setStatusIllegalParams();	return }
		
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
					if (conversationOpt.isEmpty) { response sendString (textPlain, DISCONNECTED_TEXT);	return }
				}
				else if (name == "message") {
					messageJSOpt	= JSParser parse value
					if (messageJSOpt.isEmpty) { response.setStatusIllegalParams();	return }
				}
				else {
					WARN("unexpected form parameter", name)
				}
			}
			else {
				// TODO log errors
				val conversation	= conversationOpt	getOrElse { response.setStatusIllegalParams();	return }
				val messageJS		= messageJSOpt		getOrElse { response.setStatusIllegalParams();	return }
				val	size			= request.getContentLength
				val contentType		= item.getContentType.nullOption map ContentType.apply getOrElse ContentType.unknown
				val fileName		= item.getName
				
				if (size == -1)	{
					ERROR("upload stream was zero-sized", fileName)
					response.setStatusIllegalParams()
					return 
				}
				
				// TODO files with "invalid encoding" (of their name) produce a length of 417 - don't add them!
				// NOTE with HTML5 this can be checked in the client by accessing the files's size which throws an exception in these cases 
				// if (!Validation.uploadSize(blob.size)) {
				// 	DEBUG("upload with invalid size swallowed")
				// 	drain()
				// 	return
				// }
				
				DEBUG("upload stream running", fileName, contentType)
				
				val upload	= Upload(contentType, size, stream, fileName)
				val accepted	= 
						try {
							conversation handleUpload (messageJS, upload)
						}
						catch {
							case e =>
								ERROR("upload stream failed", fileName, e)
								response.setStatusIllegalParams()
								return
						}
				try { stream.close() }
				catch { case e	=> /* may already be closed */ }
				
				DEBUG("upload stream done", fileName, accepted)
			}
		}
		
		conversationOpt foreach { _.uploadBatchCompleted() }
		response sendString (textPlain, UPLOADED_TEXT)
	}
	
	private def download(request:HttpServletRequest, response:HttpServletResponse) {
		// TODO log errors, use Validated
		val conversationId	= request	paramString "conversation"	getOrElse { response setStatusIllegalParams();	return }
		val message			= request	paramString	"message"		getOrElse { response setStatusIllegalParams();	return }
		val messageJS		= JSParser parse message				getOrElse { response setStatusIllegalParams();	return }
		val conversation	= getConversation(ConversationId(conversationId)) getOrElse { response  sendString (textPlain, DISCONNECTED_TEXT); 	return }
		val	download		= conversation handleDownload messageJS

		download match {
			// TODO catch exceptions for closed connections
			case Some(download)	=> response sendDownload		download
			case None			=> response setStatusNotFound	()
		}
	}
}
