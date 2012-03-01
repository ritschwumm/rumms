package rumms

import java.io._
import java.net._

import javax.servlet._
import javax.servlet.http._

import scutil.Implicits._
import scutil.Resource._
import scutil.log.Logging

import scfunk.validation._

import scwebapp.HttpStatusCodes._
import scwebapp.HttpImplicits._
import scwebapp.StandardMimeTypes._

final class DomServlet extends HttpServlet with Logging {
    private val text_javascript_charset	= text_javascript	attribute ("charset", Config.encoding.name)
	
    override def init() {}
	override def destroy() {}
    
    override def doGet(request:HttpServletRequest, response:HttpServletResponse) {
		handle(request, response)
	}
	
    override def doPost(request:HttpServletRequest, response:HttpServletResponse) {
		handle(request, response)
	}
	
	type Action	= HttpServletResponse=>Unit
	
	@volatile var cache:Map[String,Action]	= Map.empty
	
	private def handle(request:HttpServletRequest, response:HttpServletResponse) {
		request		setEncoding	Config.encoding
		response	setEncoding	Config.encoding
		
		/*
		println("### getContextPath="	+ request.getContextPath)	// ""
		println("### getServletPath="	+ request.getServletPath)	// "/rumms"
		println("### getPathInfo="		+ request.getPathInfo)		// "/comm"
		*/
		
		// TODO use validated? or either?
		def createAction:Action	= 
				request.getServletPath.guardNotNull match {
					case Some(path)	=>
						(path indexOf "..") match {
							case -1	=>
								(getServletConfig.getServletContext getResource path).guardNotNull match {
									case Some(url)	=>
										// TODO handle exceptions
										val text	= new InputStreamReader(url.openStream, Config.encoding.name) use { _.readFully }
										DomTemplate compile text match {
											case Valid(js)	=> 
												// DEBUG("compiled DOM for path", path)
												jsAction(js)
											case Invalid(err)  =>
												ERROR("cannot compile DOM for path", path)
												ERROR(err.toList:_*)
												errorAction(500)
										}
									case None		=>
										ERROR("resource not found for path", path)
										errorAction(400)
								}
							case _ =>
								ERROR("resource not found for invalid path: " + path)
								errorAction(400)
						}
					case None =>
						ERROR("resource not found for missing path")
						errorAction(400)
				}
		
		def errorAction(status:Int):Action	= 
				(response:HttpServletResponse) => {
					response setStatus status
				}
			
		def jsAction(code:String):Action	= 
				(response:HttpServletResponse) => {
					response setStatus		OK	
					response setContentType	text_javascript_charset
					// response setContentLength	bytes.size
					response.getWriter write code
				}
				
		val cachedAction	= cache get request.getServletPath getOrElse {
			val action	= createAction
			cache	+= (request.getServletPath -> action)
			action
		}
			
		response.noCache()	// TODO no!
		cachedAction(response)
	}
}
