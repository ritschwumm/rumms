package rumms

import scjson.ast._

trait RummsCallbacks {
	/**  a new Conversation has been opened */
	def conversationAdded(conversationId:ConversationId):Unit
	
	/** an existing Conversation has been closed */
	def conversationRemoved(conversationId:ConversationId):Unit
	
	/** a conversation is still fully alive */
	def conversationAlive(conversationId:ConversationId):Unit
	
	/** the browser sent a message to us */
	def messageReceived(conversationId:ConversationId, message:JsonValue):Unit
}
