package rumms

import javax.servlet.ServletContext

import scutil.base.implicits._
import scutil.lang._
import scutil.uid._
import scutil.worker._
import scutil.log._

import scwebapp._
import scwebapp.servlet.implicits._

import scjson.ast._

import rumms.impl._

object Rumms {
	/** must be called from a ServletContextListener.contextInitialized method */
	def create(sc:ServletContext, configuration:RummsConfiguration):Rumms	= {
		new Rumms(configuration) doto { rumms =>
			sc mount (
				name			= "RummsServlet",
				handler			= rumms.httpHandler,
				mappings		= Vector(rumms.mountPath),
				loadOnStartup	= Some(100)
			)
		}
	}
}

final class Rumms(configuration:RummsConfiguration) extends Disposable with Logging { outer =>
	@volatile
	private var callbacks:RummsCallbacks	= null
	private var conversations:Synchronized[ISeq[Conversation]]	= Synchronized(Vector.empty)
	
	private val uidGenerator	= new UidGenerator(Constants.secureIds)
	private val rummsHandler	= new RummsHandler(configuration, new RummsHandlerContext{
		def createConversation():ConversationId							= outer.createConversation()
		def expireConversations():Unit									= outer.expireConversations()
		def findConversation(id:ConversationId):Option[Conversation]	= outer findConversation id
	})
	private val httpHandler:HttpHandler	= rummsHandler.totalPlan
	private val mountPath:String		= configuration.path + "/*"
	
	private val sendWorker	=
			new Worker(
				"conversation publisher",
				Constants.sendDelay,
				publishConversations,
				ERROR("publishing conversations failed", _)
			)
			
	//------------------------------------------------------------------------------
	//## public interface
	
	val partialHandler:HttpPHandler	= rummsHandler.partialPlan
	
	/** relative to the webapp's context */
	val codePath:String	= (configuration.path substring 1) + Constants.paths.code + "?_="
	
	def start(callbacks:RummsCallbacks) {
		require(this.callbacks == null, "rumms application is already started")
		
		this.callbacks	= callbacks
		
		DEBUG("starting send worker")
		sendWorker.start()
		
		INFO("running")
	}
	
	def dispose() {
		DEBUG("destroying send worker")
		
		sendWorker.dispose()
		sendWorker.awaitWorkless()
		sendWorker.join()
		
		INFO("stopped")
	}
	
	/** ids of currently active Conversations */
	def conversationIds:Set[ConversationId]	=
			(conversations.get map { _.id }).toSet
	
	/** send a message to a Conversation, returns success */
	def sendMessage(receiver:ConversationId, message:JSONValue):Boolean	=
			findConversation(receiver)
			.someEffect	{ _ appendOutgoing message }
			.isDefined
			
	//------------------------------------------------------------------------------
	//## eonversations
	
	private def createConversation():ConversationId = {
		val	conversationId	= nextConversationId()
		val conversation	= new Conversation(conversationId, callbacks)
		conversations update { _ :+ conversation }
		callbacks conversationAdded conversationId
		conversationId
	}
	
	private def expireConversations() {
		conversations
		.modify		{ _  partition { _.alive } }
		.map		{ _.id }
		.foreach	(callbacks.conversationRemoved)
	}
	
	private def publishConversations() {
		conversations.get foreach { _ maybePublish () }
	}
	
	private def findConversation(id:ConversationId):Option[Conversation]	=
			conversations.get find { _.id ==== id }
		
	private def nextConversationId():ConversationId	=
			ConversationId(UidPrisms.String read uidGenerator.next)
}
