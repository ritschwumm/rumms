package rumms

import java.nio.ByteBuffer
import java.lang.{ Long=>JLong }

import scutil.lang._

object IdString extends Marshaller[Id,String] {
	def write(it:Id):String	= 
			"%016x%016x%016x" format (
					it.time,
					it.counter,
					it.random)
					
	def read(it:String):Option[Id]	=
			try {
				Some(Id(
						JLong parseLong (it.substring (0,16),	16),
						JLong parseLong (it.substring (16,32),	16),
						JLong parseLong (it.substring (32,48),	16)))
			}
			catch {
				case e	=> None
			}
}
