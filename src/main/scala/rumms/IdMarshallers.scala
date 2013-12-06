package rumms

import java.nio.ByteBuffer

import scutil.lang._
import scutil.implicits._

object IdMarshallers {
	val IdBytes:Marshaller[Id,Array[Byte]]	= Marshaller(
			(it:Id)	=>
					ByteBuffer allocate 4*8 putLong it.counter putLong it.time  putLong it.random array(),
			(it:Array[Byte])	=>
					it.size == 4*8 guard {
						val bb	= ByteBuffer wrap it
						Id(
							machine	= bb.getLong,
							counter	= bb.getLong,
							time	= bb.getLong,
							random	= bb.getLong
						)
					})

	val IdString:Marshaller[Id,String]	= Marshaller(
			(it:Id)	=> 
					"%016x%016x%016x%016x" format (
							it.machine,
							it.counter,
							it.time,
							it.random),
			(it:String)	=>
					try {
						val number	= BigInt(it, 16)
						Some(Id(
							machine	= (number >> 192).toLong,
							counter	= (number >> 128).toLong,
							time	= (number >>  64).toLong,
							random	= (number >>   0).toLong
						))
					}
					catch { case e:NumberFormatException	=> 
						None
					})
}
