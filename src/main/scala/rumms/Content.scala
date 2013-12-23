package rumms

import java.io._

import scutil.lang._
import scutil.implicits._

import scwebapp.MimeType

object Content {
	def file(mimeType:MimeType, file:File):Content	=
			Content(
				mimeType		= mimeType,
				contentLength	= file.length,
				fileName		= Some(file.getName),
				inputStream	= thunk {
					Catch.byType[IOException] in {
						new FileInputStream(file)
					}
				}
			)
}

case class Content(
	mimeType:MimeType,
	contentLength:Long,
	fileName:Option[String],
	// TODO questionable
	inputStream:Thunk[Tried[IOException,InputStream]]
)
