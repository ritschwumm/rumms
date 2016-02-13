package rumms
package impl

trait RummsHandlerContext {
	def createConversation():ConversationId
	def expireConversations():Unit
	def findConversation(id:ConversationId):Option[Conversation]
}
