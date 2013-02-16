package rumms

import java.io.InputStreamReader
import java.net.URLEncoder
import javax.servlet.http._

import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.util.Streams
import org.apache.commons.fileupload.FileItemIterator
import org.apache.commons.fileupload.FileItemStream
	
import org.eclipse.jetty.continuation.ContinuationSupport

import scutil.lang._
import scutil.Implicits._
import scutil.Resource._
import scutil.tried._
import scutil.log.Logging

import scjson._
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
		catch {
			case e:Exception	=>
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
			"&message="			+ urlEncode(JSONMarshaller apply message)
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
		catch {
			case e:Exception => 
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
				val code		= JSONMarshaller apply value
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
	
	private val continuationAttribute	= "CONT"
	
	/** receive and send messages for a single Conversation */
	private def comm(request:HttpServletRequest, response:HttpServletResponse) {
		val continuation	= ContinuationSupport getContinuation request
		val action	=
				if (continuation.isInitial) {
					// DEBUG("initial continuation, reading data from request", continuation)
					val dataStr	= request.getReader use { _.readFully }
					for {
						data			<- JSONMarshaller unapply dataStr	toWin (Forbidden,		"invalid message")
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
								JSONMarshaller apply JSONVarObject(
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
								if (continuation.isSuspended) {
									continuation.resume()
								}
								else {
									// NOTE not checking isSuspended lead to a
									// java.lang.IllegalStateException: REDISPATCHED,resumed,expired
									WARN("cannot resume non-suspended continuation", continuation)
								}
							}
							
							Ignore
						}
					}
				}
				else if (continuation.isResumed || continuation.isExpired) {
					for {
						contAttr		<- (request getAttribute continuationAttribute).guardNotNull toWin (Ignore, "resumed continuation with null responseThunk")
						responseThunk	<-
								try {
									Win(contAttr.asInstanceOf[Thunk[String]])
								}
								catch { case e:ClassCastException =>
									Fail((Ignore, "cannot use continuation, scala.Function0 comes from different ClassLoaders"))
								}
					}
					yield {
						// DEBUG("resumed continuation, using fetcher", continuation)
						BatchRespose(responseThunk())
					}
				}
				else {
					Fail((Ignore, "unexpected continuation state"))
				}
		action.swap map { _._2 } foreach (ERROR(_))
		action cata (_._1, identity) apply response 
	}
	
	//------------------------------------------------------------------------------
	//## file transfer
	
	case class UploadState(conversationOpt:Option[Conversation], messageJSOpt:Option[JSONValue])
	
	/** upload a file to be played */
	private def upload(request:HttpServletRequest, response:HttpServletResponse) {
		val fit		= new ServletFileUpload getItemIterator request
		def loop(uploadState:UploadState):Tried[(HttpResponder,String),Either[UploadState,UploadState]]	=
				uploadNextState(request, fit, uploadState) match {
					case Win(Left(s))	=> loop(s)
					case x				=> x
				}
		loop(UploadState(None,None)) match {
			case Fail((responder,log))	=> 
				ERROR(log)
				responder(response)
			case Win(Left(_))	=>
				sys error "unexpected state"
			case Win(Right(uploadState))	=> 	
				uploadState.conversationOpt foreach { _.uploadBatchCompleted() }
				Uploaded(response)
		}
	}
			
	private def uploadNextState(request:HttpServletRequest, fit:FileItemIterator, uploadState:UploadState):Tried[(HttpResponder,String),Either[UploadState,UploadState]]	=
			if (fit.hasNext) {
				val	item:FileItemStream	= fit.next
				if (item.isFormField)	uploadFormField(request, item, uploadState)
				else					uploadFielStream(request, item, uploadState)
			}
			else Win(Right(uploadState))
			
	private def uploadFormField(request:HttpServletRequest, item:FileItemStream, uploadState:UploadState):Tried[(HttpResponder,String),Either[UploadState,UploadState]]	= {
		val	name	= item.getFieldName
		val stream	= item.openStream()
		val	value	= Streams asString (stream, Config.encoding.name)
		// DEBUG("form field " + name + " value " + value)
		if (name == "conversation") {
			for {
				conversation	<- conversations use ConversationId(value) toWin (Disconnected, "unknown conversation")
			}
			yield {
				// TODO ugly
				conversation.remoteUser	= request.getRemoteUser.guardNotNull
				Left(uploadState copy (conversationOpt	= Some(conversation)))
			}
		}
		else if (name == "message") {
			for {
				messageJS	<- JSONMarshaller unapply value toWin (Forbidden, "invalid message")
			}
			yield {
				Left(uploadState copy (messageJSOpt	= Some(messageJS)))
			}
		}
		else {
			Fail(Forbidden, "unexpected form parameter")
		}
	}
	
	private def uploadFielStream(request:HttpServletRequest, item:FileItemStream, uploadState:UploadState):Tried[(HttpResponder,String),Either[UploadState,UploadState]]	= {
		val	name	= item.getFieldName
		val stream	= item.openStream()
		for {
			conversation	<- uploadState.conversationOpt					toWin (Forbidden, "conversation missing")
			messageJS		<- uploadState.messageJSOpt						toWin (Forbidden, "invalid message")
			size			<- request.getContentLength guardBy { _ != -1 }	toWin (Forbidden, "upload stream was zero-sized")
			mimeType		= item.getContentType.guardNotNull flatMap MimeType.parse getOrElse unknown_unknown
			fileName		= item.getName
			// TODO files with "invalid encoding" (of their name) produce a length of 417 - don't add them!
			// NOTE with HTML5 this can be checked in the client by accessing the files's size which throws an exception in these cases 
			// if (!Validation.uploadSize(blob.size)) {
			// 	DEBUG("upload with invalid size swallowed")
			// 	drain()
			// 	return
			// }
			content			= Content(mimeType, size, stream)
			accepted		<-
					try {
						Win(conversation uploadContent (messageJS, content, fileName))
					}
					catch {
						case e:Exception =>
							Fail(Forbidden, "upload stream failed")
					}
		}
		yield {
			Left(uploadState)
		}
	}
	
	private def download(request:HttpServletRequest, response:HttpServletResponse) {
		val action	=
				for {
					conversationId	<- request	paramString "conversation"		toWin (Forbidden, 		"conversation missing") map ConversationId.apply
					message			<- request	paramString	"message"			toWin (Forbidden, 		"message missing")
					messageJS		<- JSONMarshaller unapply message			toWin (Forbidden, 		"invalid message")
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
	
	private val Forbidden	= SetStatus(FORBIDDEN)
	private val NotFound	= SetStatus(NOT_FOUND)
	
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
}
