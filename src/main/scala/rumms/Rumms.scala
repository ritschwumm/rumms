package rumms

import jakarta.servlet.ServletContext

import scala.util.control.NonFatal

import scutil.core.implicits._
import scutil.lang._
import scutil.guid.Guid
import scutil.concurrent._
import scutil.log._

import scwebapp._
import scwebapp.servlet.extensions._

import scjson.ast._

import rumms.impl._

object Rumms extends Logging {
	def create(configuration:RummsConfiguration):IoResource[Rumms]	=
		for {
			rumms	<-	IoResource delay new Rumms(configuration)
			_		<-	SimpleWorker.create(
							"rumms conversation publisher",
							Thread.MIN_PRIORITY,
							Io delay {
								try {
									rumms.publishConversations()
									Thread.sleep(Constants.sendDelay.millis)
									true
								}
								catch { case NonFatal(e) =>
									ERROR("publishing conversations failed", e)
									false
								}

							}
						)
		}
		yield rumms
}

final class Rumms(configuration:RummsConfiguration) { outer =>
	private val rummsHandler	=
		new RummsHandler(
			configuration,
			new RummsHandlerContext {
				def createConversation():ConversationId							= outer.createConversation()
				def expireConversations():Unit									= outer.expireConversations()
				def findConversation(id:ConversationId):Option[Conversation]	= outer findConversation id
			}
		)

	//------------------------------------------------------------------------------
	//## public interface

	/** relative to the webapp's context */
	val codePath:String	= (configuration.path substring 1) + Constants.paths.code + "?_="

	/** must be called from a ServletContextListener.contextInitialized method */
	def mountAt(sc:ServletContext):Unit	=
		sc.mount(
			name			= "RummsServlet",
			handler			= rummsHandler.totalPlan,
			mappings		= Vector(configuration.path + "/*"),
			loadOnStartup	= Some(100),
			multipartConfig	= None
		)

	val partialHandler:HttpPHandler	= rummsHandler.partialPlan

	/** ids of currently active Conversations */
	def conversationIds():Set[ConversationId]	=
		conversations.get().map(_.id).toSet

	/** send a message to a Conversation, returns success */
	def sendMessage(receiver:ConversationId, message:JsonValue):Boolean	=
		findConversation(receiver).someEffect(_ appendOutgoing message).isDefined

	/** send a message to all Conversations */
	def broadcastMessage(message:JsonValue):Unit	=
		conversations.get().foreach(_ appendOutgoing message)

	/** listen for incoming messages and conversation status changes */
	def listen(callbacks:RummsCallbacks):IoResource[Unit]	=
		IoResource.unsafe.disposing{
			addCallbacks(callbacks)
		}{
			_ => removeCallbacks(callbacks)
		}
		.void

	//------------------------------------------------------------------------------
	//## callbacks

	private val callbackses:Synchronized[Seq[RummsCallbacks]]	= Synchronized(Vector.empty)

	private def addCallbacks(callbacks:RummsCallbacks):Unit	=
		callbackses update (_ :+ callbacks)

	private def removeCallbacks(callbacks:RummsCallbacks):Unit	=
		callbackses update (_ filter (_ != callbacks))

	private val callbacksProxy:RummsCallbacks	=
		new RummsCallbacks {
			def conversationAdded(conversationId:ConversationId):Unit	=
				callbackses.get() foreach (_ conversationAdded conversationId)

			def conversationRemoved(conversationId:ConversationId):Unit	=
				callbackses.get() foreach (_ conversationRemoved conversationId)

			def conversationAlive(conversationId:ConversationId):Unit	=
				callbackses.get() foreach (_ conversationAlive conversationId)

			def messageReceived(conversationId:ConversationId, message:JsonValue):Unit	=
				callbackses.get() foreach (_.messageReceived(conversationId, message))
		}


	//------------------------------------------------------------------------------
	//## eonversations

	private val conversations:Synchronized[Seq[Conversation]]	= Synchronized(Vector.empty)

	private def createConversation():ConversationId = {
		val	conversationId	= nextConversationId()
		val conversation	= new Conversation(conversationId, callbacksProxy)
		conversations update { _ :+ conversation }
		callbacksProxy.conversationAdded(conversationId)
		conversationId
	}

	private def expireConversations():Unit	=
		conversations
		.modify		(_  partition { _.alive() })
		.map		{ _.id }
		.foreach	(callbacksProxy.conversationRemoved)

	private def publishConversations():Unit	=
		conversations.get() foreach { _.maybePublish() }

	private def findConversation(id:ConversationId):Option[Conversation]	=
		conversations.get() find { _.id ==== id }

	private def nextConversationId():ConversationId	=
		ConversationId(Guid.unsafeFresh())
}
