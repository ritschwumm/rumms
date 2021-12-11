package rumms.impl

import scutil.core.implicits.*
import scutil.log.*
import scwebapp.*

final case class Action[T](value:Either[(HttpResponder, Problem), T]) {
	def log:Option[Seq[LogValue]]	=
		value.swap.toOption map { _._2.logValue }

	def responder(implicit ev:T <:< HttpResponder):HttpResponder	=
		value.cata(_._1, ev)

	def map[U](func:T=>U):Action[U]	=
		Action(value map func)

	def flatMap[U](func:T=>Action[U]):Action[U]	=
		Action(value flatMap (it => func(it).value))
}
