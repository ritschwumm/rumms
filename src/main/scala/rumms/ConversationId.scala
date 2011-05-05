package rumms

import rumms.util.IdGenerator

object ConversationId {
	private val gen	= new IdGenerator
	
	def next:ConversationId	= synchronized {
		ConversationId(gen.nextId()) 
	}
}

case class ConversationId(idval:String)
