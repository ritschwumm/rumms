package rumms.http

import java.io.File
import javax.servlet.http._

// import org.eclipse.jetty.util.resource.FileResource

import scutil.Resource._
import scutil.ext.InputStreamImplicits._

import rumms.Download
import rumms.ContentType
import rumms.Encoding

final class HttpResponseExtension(delegate:HttpServletResponse) {
	def setEncoding(encoding:Encoding) {
		delegate setCharacterEncoding encoding.name
	}
	
	def setContentType(contentType:ContentType) {
		delegate setContentType contentType.headerValue
	}
	
	def setContentLength(size:Long) {
		// TODO why is toInt necessary here?
		delegate setContentLength size.toInt
	}
	
	def setNoCache() {
		delegate addHeader ("Cache-Control",	"no-cache, must-revalidate")
		delegate addHeader ("Expires",			"1 Jan 1971")
	}
	
	def setStatusNotFound() {
		delegate setStatus HttpServletResponse.SC_NOT_FOUND // 404
	}

	def setStatusIllegalParams() {
		delegate setStatus HttpServletResponse.SC_FORBIDDEN	// 403
	}
	
	def sendString(contentType:ContentType, str:String) {
		setContentType(contentType)
		delegate.getWriter write str
	}
	
	/*
	def okFile(typ:ContentType, file:File) {
		contentType(typ)
		
		val length		= file.length.toInt
		contentLength(length)
		
		val ost			= delegate.getOutputStream
		val	resource	= new FileResource(file.toURI.toURL)
		resource.writeTo(ost, 0, length)
		ost.flush()
	}
	*/
	
	def sendDownload(download:Download) {
		setContentType(download.contentType)
		setContentLength(download.contentLength)
		// TODO handle exceptions
		download.inputStream copyTo delegate.getOutputStream
		delegate.getOutputStream.flush()
	}
}
