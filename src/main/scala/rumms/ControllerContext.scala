package rumms

import scjson.JSONValue

trait ControllerContext {
	def remoteUser(conversationId:ConversationId):Option[String]
	
	def sendMessage(receiver:ConversationId, message:JSONValue):Boolean
}
