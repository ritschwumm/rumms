package rumms.impl

import scutil.lang.Charsets
import scutil.time.implicits._

object Constants {
	val version			= 1
	val encoding		= Charsets.utf_8
	val continuationTTL	= 1.minutes
	val clientTTL		= continuationTTL	*! 2
	val conversationTTL	= clientTTL			*! 3
	val sendDelay		= 100.millis

	object paths {
		val code	= "/code"
		val hi		= "/hi"
		val comm	= "/comm"
	}
}
