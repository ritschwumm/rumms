package rumms

import java.util.Random
import java.security.SecureRandom

/** creates randomized, unique ids. unsynchronized. */
final class IdGenerator(secure:Boolean) {
	private var counterValue:Long	= -1
	
	private val	randomGenerator		= 
			if (secure)	SecureRandom getInstance "SHA1PRNG"
			else		new Random
	
	def next:Id	= Id(
			{ counterValue += 1; counterValue },
			System.currentTimeMillis,
			randomGenerator.nextLong())
}
