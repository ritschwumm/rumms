package rumms

object ConversationId {
	private val gen	= new IdGenerator(Config.secureIds)
	
	def next:ConversationId	= synchronized {
		ConversationId(IdString write gen.next) 
	}
}

case class ConversationId(idval:String)
