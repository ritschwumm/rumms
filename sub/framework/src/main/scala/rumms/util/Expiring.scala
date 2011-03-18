package rumms.util

import scutil.time._

final class Expiring[K,V](ttl:Duration) {
	private class Entry[K,V](val id:K, var modified:Instant, val item:V)
	
	private var entries:List[Entry[K,V]]	= Nil
	
	/** add a new item */
	def put(id:K, item:V) {
		require(!(entries exists { _.id == id }), "duplicate entry")
		entries	= new Entry(id, Instant.now, item) :: entries
	}
	
	/** get an item by id and keep it alive */
	def get(id:K):Option[V] = {
		val	entry	= entries find { _.id == id }
		entry foreach { _.modified	= Instant.now }
		entry map { _.item }
	}
	
	/** get an item by id and remove it */
	def remove(id:K):Option[V] = {
		val (found,rest)	= entries partition { _.id == id }
		entries	= rest
		found.headOption map { _.item }
	}
	
	/** returns expired items */
	def expire():List[V] = {
		// val (fresh,stale)	= entries partition { now - _.modified < ttl }
		val (fresh,stale)	= entries partition { it => it.modified + ttl > Instant.now }
		entries	= fresh
		stale map { _.item }
	}
	
	/** returns all existing items and removes all entries */
	def clear():List[V]	= {
		val out	= entries map { _.item }
		entries	= Nil
		out
	}
}
