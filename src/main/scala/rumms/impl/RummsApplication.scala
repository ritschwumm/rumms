package rumms
package impl

import javax.servlet.ServletContext

import scutil.implicits._
import scutil.log._
import scutil.worker._

import scwebapp.servlet.implicits._
import scwebapp.HttpHandler

import scjson._

object RummsApplication {
	/** must be called from a ServletContextListener.contextInitialized method */
	def create(sc:ServletContext, configuration:RummsConfiguration):Rumms	= {
		val application	= new RummsApplication(configuration)
		sc mount (
			name			= "RummsServlet",
			handler			= application.httpHandler,
			mappings		= Vector(configuration.path + "/*"),
			loadOnStartup	= Some(100)
		)
		application.userInterface
	}
}

final class RummsApplication(configuration:RummsConfiguration) extends Logging { outer =>
	@volatile
	private var callbacks:RummsCallbacks	= null
	
	def httpHandler:HttpHandler	= rummsHandler.plan
	private def rummsHandler	= new RummsHandler(this, configuration)
	
	private val codePath:String	= (configuration.path substring 1) + Constants.paths.code + "?_="
	def userInterface:Rumms	= new Rumms {
		def codePath:String	=
				outer.codePath
		def start(callbacks:RummsCallbacks):Unit	=
				outer start callbacks
		def dispose():Unit	=
				outer.dispose()
		def conversationIds:Set[ConversationId]		=
				outer.conversationIds
		def sendMessage(receiver:ConversationId, message:JSONValue):Boolean	=
				outer sendMessage (receiver, message)
	}
	
	//------------------------------------------------------------------------------
	//## life cycle
	
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
		this.callbacks	= null
	}
	
	//------------------------------------------------------------------------------
	//## send
	
	private lazy val sendWorker	=
			new Worker(
				"conversation publisher",
				Constants.sendDelay,
				publishConversations,
				ERROR("publishing conversations failed", _)
			)
			
	def sendMessage(receiver:ConversationId, message:JSONValue):Boolean	=
			(conversations get receiver)
			.someEffect	{ _ appendOutgoing message }
			.isDefined
			
	//------------------------------------------------------------------------------
	//## conversation management
	
	private val idGenerator	= new IdGenerator(Constants.secureIds)
	
	private def nextConversationId():ConversationId	=
			ConversationId(IdPrisms.IdString read idGenerator.next)
	
	private val conversations	= new ConversationManager
	
	def conversationIds:Set[ConversationId]	= conversations.ids
	
	def createConversation():ConversationId = {
		val	conversationId	= nextConversationId
		val conversation	= new Conversation(conversationId, callbacks)
		conversations put conversation
		callbacks conversationAdded conversationId
		conversationId
	}
	
	def useConversation(id:ConversationId):Option[Conversation]	=
			conversations use id
	
	def expireConversations() {
		conversations.expire().map { _.id } foreach callbacks.conversationRemoved
	}
	
	private def publishConversations() {
		conversations.all foreach { _ maybePublish () }
	}
}
