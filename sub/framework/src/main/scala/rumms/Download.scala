package rumms

import java.io.InputStream
import java.io.File
import java.io.FileInputStream

object Download {
	def file(contentType:ContentType, file:File):Download	= Download(
			contentType,
			file.length,
			new FileInputStream(file))
}

case class Download(contentType:ContentType, contentLength:Long, inputStream:InputStream)
