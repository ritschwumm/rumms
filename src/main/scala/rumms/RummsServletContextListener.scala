package rumms

import javax.servlet._

import scutil.implicits._
import scutil.log._

import scwebapp._
import scwebapp.implicits._

object RummsServletContextListener {
	val controllerFactoryClass	= "rumms-controller-factory"
	val configurationPrefix		= "rumms-configuration-"
	
	def attribute(sc:ServletContext):HttpAttribute[RummsApplication]	=
			sc.attribute[RummsApplication]("rumms-application")
}

/** use this with a context parameter rumms-controller-factory containing the class name of a ControllerFactory class */
final class RummsServletContextListener extends ServletContextListener with Logging {
	def contextInitialized(ev:ServletContextEvent) {
		val sc		= ev.getServletContext
		val params	= sc.initParameters
		val config	=
				params.all
				.flatMap { case (k, v) => 
					k cutPrefix RummsServletContextListener.configurationPrefix map ((_, v))
				}
				.toMap
		
		val className	=
				params
				.firstString	(RummsServletContextListener.controllerFactoryClass)
				.getOrError		(s"missing init parameter ${RummsServletContextListener.controllerFactoryClass}")
		INFO("loading controller factory", className)
		val factory	=
				try {
					((Class forName className).newInstance).asInstanceOf[ControllerFactory]
				}
				catch { case e:Exception	=>
					ERROR("cannot load controller factory", e)
					throw e
				}
				
		INFO("starting application")
		val application	= new RummsApplication(factory, config)
		application.start()
				
		RummsServletContextListener attribute sc set Some(application)
	}
	
	def contextDestroyed(ev:ServletContextEvent) {
		val sc	= ev.getServletContext
		
		INFO("stopping application")
		(RummsServletContextListener attribute sc).get foreach { _.dispose() }
	}
}
