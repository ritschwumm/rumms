package rumms

import scwebapp.MimeType

object MimeTypes {
	val textPlain		= MimeType("text", 			"plain",		Map("charset" -> Config.encoding.name))
	val textJavascript	= MimeType("text", 			"javascript",	Map("charset" -> Config.encoding.name))
	val applicationJSON	= MimeType("application",	"json")
	val unknown			= MimeType("*",				"*")
}
