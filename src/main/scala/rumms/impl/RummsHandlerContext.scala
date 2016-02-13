package rumms
package impl

trait RummsHandlerContext {
	def createConversation():ConversationId
	def expireConversations():Unit
	def useConversation(id:ConversationId):Option[Conversation]
}
