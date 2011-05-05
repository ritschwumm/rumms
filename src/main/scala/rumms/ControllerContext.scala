package rumms

import scjson.JSValue

trait ControllerContext {
	def sendMessage(conversationId:ConversationId, message:JSValue)
	def downloadURL(conversationId:ConversationId, message:JSValue):String
}
