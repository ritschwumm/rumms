package rumms

import scjson._

trait RummsCallbacks {
	/**  a new Conversation has been opened */
	def conversationAdded(conversationId:ConversationId):Unit
	
	/** an existing Conversation has been closed */
	def conversationRemoved(conversationId:ConversationId):Unit
	
	/** the browser sent a message to us */
	def messageReceived(conversationId:ConversationId, message:JSONValue):Unit
}
