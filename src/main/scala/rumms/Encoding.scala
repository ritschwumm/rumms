package rumms

object Encoding {
	val	utf8		= Encoding("UTF-8")
	val	iso88591	= Encoding("ISO-8859-1")
	val	iso885915	= Encoding("ISO-8859-15")
}

// TODO use Charsets
case class Encoding(name:String)
