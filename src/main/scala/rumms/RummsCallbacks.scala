package rumms

import java.io.InputStream

import scjson._

trait RummsCallbacks {
	/**  a new Conversation has been opened */
	def conversationAdded(conversationId:ConversationId):Unit
	/** an existing Conversation has been closed */
	def conversationRemoved(conversationId:ConversationId):Unit
	
	//------------------------------------------------------------------------------
	
	/** the browser sent a message to us */
	def receiveMessage(conversationId:ConversationId, message:JSONValue):Unit
	
	/** the browser uploads some data. */
	def uploadContents(conversationId:ConversationId, message:JSONValue, contents:Seq[Content]):Unit
	
	/** the browser wants to download some data. returns None if the download was rejected. */
	def downloadContent(conversationId:ConversationId, message:JSONValue):Option[Content]
}
