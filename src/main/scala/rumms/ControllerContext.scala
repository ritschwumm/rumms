package rumms

import scutil.lang._

import scjson.JSONValue

trait ControllerContext {
	def sendMessage(receiver:ConversationId, message:JSONValue)
	def broadcastMessage(receiver:Predicate[ConversationId], message:JSONValue)
	def downloadURL(receiver:ConversationId, message:JSONValue):String
	def remoteUser(conversationId:ConversationId):Option[String]
}
