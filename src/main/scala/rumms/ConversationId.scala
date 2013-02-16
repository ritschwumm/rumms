package rumms

object ConversationId {
	private val gen	= new IdGenerator(Config.secureIds)
	
	def next:ConversationId	= ConversationId(IdMarshallers.IdString write gen.next) 
}

case class ConversationId(idval:String)
