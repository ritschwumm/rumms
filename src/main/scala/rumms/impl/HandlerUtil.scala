package rumms.impl

import java.io.IOException

import scutil.lang._
import scutil.implicits._

import scwebapp._

private object HandlerUtil {
	type Action[T]	= Tried[(HttpResponder,Problem),T]
	
	def actionLog(action:Action[Any]):Option[ISeq[Any]]	=
			action.swap.toOption map { _._2.loggable }
		
	def actionResponder(action:Action[HttpResponder]):HttpResponder	=
			action cata (_._1, identity)
	
	sealed trait Problem {
		def loggable:ISeq[Any]
	}
	case class PlainProblem(message:String) extends Problem {
		def loggable:ISeq[Any]	= ISeq(message)
	}
	case class ExceptionProblem(message:String, exception:Exception) extends Problem {
		def loggable:ISeq[Any]	= ISeq(message, exception)
	}
	
	implicit class ProblematicTriedException[F<:Exception,W](peer:Tried[F,W]) {
		def toUse(responder:HttpResponder, text:String):Action[W]	=
				peer mapFail { e => (responder, ExceptionProblem(text, e)) }
	}
	
	implicit class ProblematicTriedMessage[W](peer:Tried[String,W]) {
		def toUse(responder:HttpResponder):Action[W]	=
				peer mapFail { e => (responder, PlainProblem(e)) }
	}
	
	implicit class ProblematicOption[W](peer:Option[W]) {
		def toUse(responder:HttpResponder, text:String):Action[W]	=
				peer toWin (responder, PlainProblem(text))
	}
}
