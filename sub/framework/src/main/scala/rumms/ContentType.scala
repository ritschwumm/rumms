package rumms

object ContentType {
	val unknown	= ContentType("*")
	
	def apply(major:String, minor:String):ContentType						= new ContentType(major + "/" + minor)
	def apply(major:String, minor:String, encoding:Encoding):ContentType	= new ContentType(major + "/" + minor + "; charset=" + encoding.name)
}

case class ContentType(headerValue:String)

/*
case class ContentType(major:String, minor:String, encoding:Option[Encoding]) {
	def toHeaderString:String = 
			major + "/" + minor + 
			(encoding map { it => "; charset=" + it.name } getOrElse "") 
}
*/
