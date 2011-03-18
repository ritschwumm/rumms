package rumms.http

import javax.servlet.http._

object HttpImplicits {
	implicit def extendRequest(request:HttpServletRequest):HttpRequestExtension		= new HttpRequestExtension(request)
	implicit def extendResponse(response:HttpServletResponse):HttpResponseExtension	= new HttpResponseExtension(response)
}
