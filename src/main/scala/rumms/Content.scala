package rumms

import java.io.InputStream
import java.io.File
import java.io.FileInputStream

import scwebapp.MimeType

object Content {
	def file(mimeType:MimeType, file:File):Content	= Content(
			mimeType,
			file.length,
			new FileInputStream(file))
}

case class Content(mimeType:MimeType, contentLength:Long, inputStream:InputStream)
