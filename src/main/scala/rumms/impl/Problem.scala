package rumms.impl

import scutil.log._

object Problem {
	final case class Plain(message:String) extends Problem {
		def logValue:Seq[LogValue]	= Seq(LogValue.string(message))
	}
	final case class Exceptional(message:String, exception:Exception) extends Problem {
		def logValue:Seq[LogValue]	= Seq[LogValue](LogValue.string(message), LogValue.throwable(exception))
	}
}

sealed trait Problem {
	def logValue:Seq[LogValue]
}
