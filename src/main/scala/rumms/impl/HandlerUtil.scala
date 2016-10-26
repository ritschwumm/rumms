package rumms.impl

import scutil.base.implicits._
import scutil.lang._

import scjson.codec._
import scwebapp._

private object HandlerUtil {
	//------------------------------------------------------------------------------
	//## actions
	
	type Action[T]	= Tried[(HttpResponder, Problem), T]
	
	def actionLog(action:Action[_]):Option[ISeq[Any]]	=
			action.swap.toOption map { _._2.loggable }
		
	def actionResponder(action:Action[HttpResponder]):HttpResponder	=
			action cata (_._1, identity)
		
	//------------------------------------------------------------------------------
	//## problems
	
	sealed trait Problem {
		def loggable:ISeq[Any]
	}
	final case class PlainProblem(message:String) extends Problem {
		def loggable:ISeq[Any]	= ISeq(message)
	}
	final case class ExceptionProblem(message:String, exception:Exception) extends Problem {
		def loggable:ISeq[Any]	= ISeq(message, exception)
	}
	
	//------------------------------------------------------------------------------
	//## syntax
	
	implicit class ProblematicTriedJSONDecodeFailure[W](peer:Tried[JSONDecodeFailure, W]) {
		def toUse(responder:HttpResponder, text:String):Action[W]	=
				peer mapFail { e => (responder, PlainProblem(so"${text}: expected ${e.expectation} at ${e.offset.toString}")) }
	}
	
	implicit class ProblematicTriedException[F<:Exception, W](peer:Tried[F, W]) {
		def toUse(responder:HttpResponder, text:String):Action[W]	=
				peer mapFail { e => (responder, ExceptionProblem(text, e)) }
	}
	
	implicit class ProblematicOption[W](peer:Option[W]) {
		def toUse(responder:HttpResponder, text:String):Action[W]	=
				peer toWin ((responder, PlainProblem(text)))
	}
}
