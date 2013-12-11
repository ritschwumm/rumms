package rumms

import scjson.JSONValue

trait ControllerContext {
	def sendMessage(receiver:ConversationId, message:JSONValue):Boolean
}
