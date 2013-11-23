package rumms

import scutil.lang._
import scutil.time._

final class ConversationManager {
	private var entries:Seq[Conversation]	= Vector.empty
	
	/** find Conversations whose ids match a Predicate */
	def find(id:Predicate[ConversationId]):Iterable[Conversation]	=
			synchronized {
				entries filter { it => id(it.id) }
			}
	
	/** get a Conversation by id */
	def get(id:ConversationId):Option[Conversation] =
			synchronized {
				entries find { _.id == id }
			}
	
	/** insert a new Conversation */
	def put(conversation:Conversation):Unit =
			synchronized {
				conversation.touch()
				entries	= entries :+ conversation
			}
	
	/** get a Conversation by id and keep it alive */
	def use(id:ConversationId):Option[Conversation] =
			synchronized {
				val	conversation	= entries find { _.id == id }
				conversation foreach { _.touch() }
				conversation
			}
	
	/** expire old Conversations and return them */
	def expire():Iterable[Conversation] =
			synchronized {
				val (alive, dead)	= entries partition { _.alive }
				entries	= alive
				dead
			}
}
