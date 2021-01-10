package rumms

import javax.servlet.ServletContext

import scala.util.control.NonFatal

import scutil.core.implicits._
import scutil.lang._
import scutil.guid.Guid
import scutil.concurrent._
import scutil.log._

import scwebapp._
import scwebapp.servlet.implicits._

import scjson.ast._

import rumms.impl._

object Rumms {
	/** must be called from a ServletContextListener.contextInitialized method */
	def createAndMount(sc:ServletContext, configuration:RummsConfiguration):Rumms	= {
		// TODO use Using here, too
		new Rumms(configuration) doto { rumms =>
			sc.mount(
				name			= "RummsServlet",
				handler			= rumms.httpHandler,
				mappings		= Vector(rumms.mountPath),
				loadOnStartup	= Some(100)
			)
		}
	}

	// TODO construct this from multiple Usings to ensure all resources are properly freed
	def create(configuration:RummsConfiguration, callbacks:RummsCallbacks):Using[RummsSender]	=
		Using.of(
			()	=> new Rumms(configuration) doto (_.start(callbacks))
		)(
			_.close()
		)
		.map(identity[Rumms])
}

final class Rumms(configuration:RummsConfiguration) extends RummsSender with AutoCloseable with Logging { outer =>
	@volatile
	private var callbacks:RummsCallbacks	= null
	private val conversations:Synchronized[Seq[Conversation]]	= Synchronized(Vector.empty)

	private val rummsHandler	=
		new RummsHandler(configuration, new RummsHandlerContext{
			def createConversation():ConversationId							= outer.createConversation()
			def expireConversations():Unit									= outer.expireConversations()
			def findConversation(id:ConversationId):Option[Conversation]	= outer findConversation id
		})
	private val httpHandler:HttpHandler	= rummsHandler.totalPlan
	private val mountPath:String		= configuration.path + "/*"

	@volatile
	private var sendWorker:Disposable	= Disposable.empty

	//------------------------------------------------------------------------------
	//## public interface

	val partialHandler:HttpPHandler	= rummsHandler.partialPlan

	/** relative to the webapp's context */
	val codePath:String	= (configuration.path substring 1) + Constants.paths.code + "?_="

	def start(callbacks:RummsCallbacks):Unit	= {
		require(this.callbacks == null, "rumms application is already started")

		this.callbacks	= callbacks

		DEBUG("starting send worker")
		sendWorker	=
			SimpleWorker.build(
				"conversation publisher",
				Thread.MIN_PRIORITY,
				Io delay {
					try {
						publishConversations()
						Thread.sleep(Constants.sendDelay.millis)
						true
					}
					catch { case NonFatal(e) =>
						ERROR("publishing conversations failed", e)
						false
					}

				}
			)
			.openVoid()

		INFO("running")
	}

	def close():Unit	= {
		DEBUG("destroying send worker")

		sendWorker.dispose()

		INFO("stopped")
	}

	/** ids of currently active Conversations */
	def conversationIds():Set[ConversationId]	=
		(conversations.get() map { _.id }).toSet

	/** send a message to a Conversation, returns success */
	def sendMessage(receiver:ConversationId, message:JsonValue):Boolean	=
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

	private def expireConversations():Unit	= {
		conversations
		.modify		(_  partition { _.alive() })
		.map		{ _.id }
		.foreach	(callbacks.conversationRemoved)
	}

	private def publishConversations():Unit	= {
		conversations.get() foreach { _.maybePublish() }
	}

	private def findConversation(id:ConversationId):Option[Conversation]	=
		conversations.get() find { _.id ==== id }

	private def nextConversationId():ConversationId	=
		ConversationId(Guid.unsafeFresh())
}
