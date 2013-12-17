package rumms

import scutil.lang._
import scutil.implicits._
import scutil.log._
import scutil.worker._

import scjson._

final class RummsApplication(factory:ControllerFactory) extends Disposable with Logging {
	@volatile 
	private var controller:Controller	= null
	
	private val controllerContext	=
			new ControllerContext {
				def sendMessage(receiver:ConversationId, message:JSONValue):Boolean	=
						(conversations get receiver)
						.someEffect	{ _ appendOutgoing message }
						.isDefined
			}
			
	def start() {
		DEBUG("creating controller")
		controller	= factory newController controllerContext
		
		DEBUG("starting send worker")
		sendWorker.start()
		
		INFO("running")
	}
	
	def dispose() {
		DEBUG("destroying sendworker")
		sendWorker.dispose()
		sendWorker.awaitWorkless()
		
		DEBUG("destroying controller")
		controller.dispose()
		controller	= null
		
		INFO("stopped")
	}
	
	//------------------------------------------------------------------------------
	//## send worker
	
	private lazy val sendWorker	=
			new Worker(
				"conversation publisher",
				Config.sendDelay,
				publishConversations, 
				e => ERROR("publishing conversations failed", e)
			)
			
	//------------------------------------------------------------------------------
	//## conversation management
	
	private val idGenerator	= new IdGenerator(Config.secureIds)
	
	private def nextConversationId():ConversationId	=
			ConversationId(IdMarshallers.IdString write idGenerator.next) 
	
	private val conversations	= new ConversationManager
	
	def createConversation():ConversationId = {
		val	conversationId	= nextConversationId
		val conversation	= new Conversation(conversationId, controller)
		conversations put conversation
		controller conversationAdded conversationId
		conversationId
	}
	
	def useConversation(id:ConversationId):Option[Conversation]	=
			conversations use id
	
	def expireConversations() {
		conversations.expire().map { _.id } foreach controller.conversationRemoved
	}
	
	private def publishConversations() {
		conversations.all foreach { _ maybePublish () }
	}
	
	//------------------------------------------------------------------------------
	
	def userData:JSONValue	=
			controller.userData
		
	def serverVersion:String	=
			Config.version.toString + "/" + controller.version
}
