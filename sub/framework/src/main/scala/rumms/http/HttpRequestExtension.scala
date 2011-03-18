package rumms.http

import javax.servlet.http._

import scala.util.control.Exception._

import scutil.ext.AnyRefImplicits._

import rumms.Encoding

final class HttpRequestExtension(delegate:HttpServletRequest) {
	def setEncoding(encoding:Encoding) {
		delegate.setCharacterEncoding(encoding.name)
	}

	def paramString(name:String):Option[String] =
			delegate getParameter name nullOption
	
	def paramInt(name:String):Option[Int] =
			paramString(name) flatMap parseInt
		
	def paramLong(name:String):Option[Long] =
			paramString(name) flatMap parseLong
			
	/*
	def attrFile(name:String):Option[File] =
			delegate.getAttribute(name).asInstanceOf[File] nullOption
	*/
			
	def headerString(name:String):Option[String] = 
			delegate.getHeader(name).nullOption
			
	def headerInt(name:String):Option[Int] = 
			headerString(name) flatMap parseInt
			
	def headerLong(name:String):Option[Long] = 
			headerString(name) flatMap parseLong
			
	private def parseInt(str:String):Option[Int] =
			catching(classOf[NumberFormatException]) opt str.toInt
			
	private def parseLong(str:String):Option[Long] =
			catching(classOf[NumberFormatException]) opt str.toLong
			
	/*
	val names	= delegate.getHeaderNames
	while (names.hasMoreElements) {
		val	name	= names.nextElement
		val values	= delegate getHeaders name.asInstanceOf[String]
		while (values.hasMoreElements) {
			val	value	= values.nextElement
			println(name + "=" + value)
		}
	}
	*/
}
