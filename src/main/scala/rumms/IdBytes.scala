package rumms

import java.nio.ByteBuffer
import java.lang.{ Long=>JLong }

import scutil.lang._
import scutil.Implicits._

object IdBytes extends Marshaller[Id,Array[Byte]] {
	def write(it:Id):Array[Byte]	= 
			ByteBuffer allocate 3*8 putLong it.counter putLong it.time  putLong it.random array()
					
	def read(it:Array[Byte]):Option[Id]	= {
		if (it.size == 3*8) {
			val bb	= ByteBuffer wrap it
			Some(Id(bb.getLong, bb.getLong, bb.getLong))
		}
		else None
	}
}
