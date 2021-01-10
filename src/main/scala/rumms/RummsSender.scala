package rumms

import scwebapp._

import scjson.ast._

trait RummsSender {
	def partialHandler:HttpPHandler

	/** relative to the webapp's context */
	def codePath:String

	/** ids of currently active Conversations */
	def conversationIds():Set[ConversationId]

	/** send a message to a Conversation, returns success */
	def sendMessage(receiver:ConversationId, message:JsonValue):Boolean
}
