package rumms

import javax.servlet.ServletContext

import scutil.lang._

import scjson.JSONValue

import rumms.impl.RummsApplication

object Rumms {
	/** must be called from a ServletContextListener.contextInitialized method */
	def create(sc:ServletContext, configuration:RummsConfiguration):Rumms	=
			RummsApplication create (sc, configuration)
}

/** must be disposed in a ServletContextListener.contextDestroyed method */
trait Rumms extends Disposable {
	def start(callbacks:RummsCallbacks):Unit
	
	/** ids of currently active Conversations */
	def conversationIds:Set[ConversationId]
	
	/** send a message to a Conversation, returns success */
	def sendMessage(receiver:ConversationId, message:JSONValue):Boolean
	
	/** relative to the webapp's context */
	def codePath:String
}
