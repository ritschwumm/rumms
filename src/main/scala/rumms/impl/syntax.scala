package rumms.impl

import scutil.core.implicits._

import scjson.codec._
import scwebapp._

private object syntax {
	implicit class ProblematicEitherJsonDecodeFailure[W](peer:Either[JsonDecodeFailure, W]) {
		def toAction(responder:HttpResponder, text:String):Action[W]	=
			Action(
				peer leftMap { e => (responder, Problem.Plain(show"${text}: expected ${e.expectation} at ${e.offset}")) }
			)
	}

	implicit class ProblematicEitherException[F<:Exception, W](peer:Either[F, W]) {
		def toAction(responder:HttpResponder, text:String):Action[W]	=
			Action(
				peer leftMap { e => (responder, Problem.Exceptional(text, e)) }
			)
	}

	implicit class ProblematicOption[W](peer:Option[W]) {
		def toAction(responder:HttpResponder, text:String):Action[W]	=
			Action(
				peer toRight ((responder, Problem.Plain(text)))
			)
	}
}
