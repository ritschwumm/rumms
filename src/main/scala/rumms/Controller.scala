package rumms

import java.io.InputStream

import scjson._

trait Controller {
	/** used for client-side version checking */
	val version:Int
	/** available as rumms.userData in the client */
	val userData:JSValue
	
	/** the application is going down */
	def dispose():Unit
	
	/**  a new Conversation has been opened */
	def conversationAdded(conversationId:ConversationId):Unit
	/** an existing Conversation has been closed */
	def conversationRemoved(conversationId:ConversationId):Unit
	
	/** the browser sent a message to us */
	def receiveMessage(conversationId:ConversationId, message:JSValue):Unit
	
	/** the browser uploads some data. returns false if the upload was rejected */
	def uploadContent(conversationId:ConversationId, message:JSValue, content:Content, fileName:String):Boolean
	/** the browser wants to download some data. returns None if the download was rejected. */
	def downloadContent(conversationId:ConversationId, message:JSValue):Option[Content]
	
	/** called after a single upload request is finished, possibly after multiple calls to handleUpload */
	def uploadBatchCompleted(conversationId:ConversationId):Unit
}
