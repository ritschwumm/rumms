package rumms.impl

import scutil.io.Charsets
import scutil.time.implicits._

// TODO hardcoded
object Constants {
	val version			= 1
	val encoding		= Charsets.utf_8
	val continuationTTL	= 1.minutes
	val clientTTL		= continuationTTL	*! 2
	val conversationTTL	= clientTTL			*! 3
	val sendDelay		= 100.millis
	val secureIds		= true
}
