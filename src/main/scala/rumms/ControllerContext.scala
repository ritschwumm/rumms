package rumms

import scjson.JSONValue

trait ControllerContext {
	def configuration:Map[String,String]
	def conversationIds:Set[ConversationId]
	def sendMessage(receiver:ConversationId, message:JSONValue):Boolean
}
