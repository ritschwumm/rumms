package rumms

import java.util.Random
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

// TODO not safe for use in a cluster

/** creates randomized, unique ids. no synchronization necessary. */
final class IdGenerator(secure:Boolean) {
	private val counter:AtomicLong	= new AtomicLong
	
	private val	random	= 
			if (secure)	SecureRandom getInstance "SHA1PRNG"
			else		new Random
	
	def next():Id	= Id(
			counter.incrementAndGet,
			System.currentTimeMillis,
			random.nextLong())
}
