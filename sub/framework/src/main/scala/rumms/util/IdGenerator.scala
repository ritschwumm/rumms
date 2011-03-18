package rumms.util

import java.security.SecureRandom

import scutil.Sequence

/** creates randomized, unique ids */
final class IdGenerator {
	val maxLen	= 16*2+1	// 2 longs as hex and one separator
	
	private val sequence	= new Sequence
	private val	random		= SecureRandom getInstance "SHA1PRNG"
	
	def nextId():String	= random.nextLong().toHexString + "-" + sequence.next().toHexString
}
