package rumms.impl

import java.nio.ByteBuffer

import scutil.lang._
import scutil.implicits._

object IdPrisms {
	val IdBytes:Prism[Array[Byte],Id]	=
			Prism(
				(it:Array[Byte])	=>
						it.size == 4*8 guard {
							val bb	= ByteBuffer wrap it
							Id(
								machine	= bb.getLong,
								counter	= bb.getLong,
								time	= bb.getLong,
								random	= bb.getLong
							)
						},
				(it:Id)	=>
						ByteBuffer allocate 4*8 putLong it.counter putLong it.time  putLong it.random array()
			)

	val IdString:Prism[String,Id]	=
			Prism(
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
						},
				(it:Id)	=>
						"%016x%016x%016x%016x" format (
							it.machine,
							it.counter,
							it.time,
							it.random
						)
			)
}
