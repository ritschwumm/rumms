package rumms

import java.io.InputStream

case class Upload(contentType:ContentType, contentLengthLimit:Long, inputStream:InputStream, fileName:String)
