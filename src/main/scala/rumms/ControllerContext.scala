package rumms

import scjson.JSONValue

trait ControllerContext {
	def sendMessage(conversationId:ConversationId, message:JSONValue)
	def downloadURL(conversationId:ConversationId, message:JSONValue):String
	def remoteUser(conversationId:ConversationId):Option[String]
}
