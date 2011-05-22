package rumms

import javax.servlet._
import javax.servlet.http._

import scutil.ext.AnyRefImplicits._

/** overrides jetty's default charset (iso-8859-1) for requests and responses */
final class CharsetFilter extends Filter {
	@volatile private var filterConfig:Option[FilterConfig] 	= None
	
	def init(filterConfig:FilterConfig) {
		this.filterConfig	= Some(filterConfig)
	}
	
	def destroy() {
		this.filterConfig	= None
	}
	
	def doFilter(request:ServletRequest, response:ServletResponse, filterChain:FilterChain) {
		for {
			config	<- filterConfig
			charset	<- config getInitParameter "charset" guardNotNull
		}
		yield {
			// config.getServletContext log ("filter: " + response.getContentType)
			request  setCharacterEncoding charset
			response setCharacterEncoding charset
			filterChain doFilter (request, response)
		}
	}
}
