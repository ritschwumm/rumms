package rumms

import java.io.IOException

import scutil.lang._
import scutil.implicits._

import scwebapp._

private object HandlerUtil {
	type Action[T]	= Tried[(HttpResponder,Problem),T]
	
	def actionLog(action:Action[Any]):Option[Seq[Any]]	=
			action.swap.toOption map { _._2.loggable }
		
	def actionResponder(action:Action[HttpResponder]):HttpResponder	=
			action cata (_._1, identity)
	
	sealed trait Problem {
		def loggable:Seq[Any]
	}
	case class PlainProblem(message:String) extends Problem {
		def loggable:Seq[Any]	= Seq(message)
	}
	case class ExceptionProblem(message:String, exception:Exception) extends Problem {
		def loggable:Seq[Any]	= Seq(message, exception)
	}
	
	def HttpPartsProblem(it:HttpPartsProblem):Problem	=
			it match {
				case NotMultipart(e)		=> ExceptionProblem("not a multipart request", e)
				case InputOutputFailed(e)	=> ExceptionProblem("io failure", e)
				case SizeLimitExceeded(e)	=> ExceptionProblem("size limits exceeded", e)
			}
				
	implicit class ProblematicTriedParts[W](peer:Tried[HttpPartsProblem,W]) {
		def toUse(responder:HttpResponder):Action[W]	=
				peer mapFail { pp => (responder, HttpPartsProblem(pp)) }
	} 
	
	implicit class ProblematicTried[F<:Exception,W](peer:Tried[F,W]) {
		def toUse(responder:HttpResponder, text:String):Action[W]	=
				peer mapFail { e => (responder, ExceptionProblem(text, e)) }
	} 
	
	implicit class ProblematicOption[W](peer:Option[W]) {
		def toUse(responder:HttpResponder, text:String):Action[W]	=
				peer toWin (responder, PlainProblem(text))
	} 
}
