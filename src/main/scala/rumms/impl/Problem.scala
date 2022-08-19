package rumms.impl

import scutil.log.*

enum Problem {
	case Plain(message:String)
	case Exceptional(message:String, exception:Exception)

	def logValue:Seq[LogValue]	=
		this match {
			case Plain(message)						=> Seq(LogValue.string(message))
			case Exceptional(message, exception)	=> Seq(LogValue.string(message), LogValue.throwable(exception))
		}
}
