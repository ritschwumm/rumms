package rumms

import scutil.Charsets
import scutil.time._

// TODO hardcoded
object Config {
	val version			= 1
	val encoding		= Charsets.utf_8
	val continuationTTL	= Duration.minute	* 1
	val clientTTL		= continuationTTL	* 2
	val conversationTTL	= clientTTL			* 3
}
