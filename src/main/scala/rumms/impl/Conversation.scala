package rumms
package impl

import scutil.core.implicits._
import scutil.lang._
import scutil.time._
import scutil.log._

import scjson.ast._

final class Conversation(val id:ConversationId, callbacks:RummsCallbacks) extends Logging {
	//------------------------------------------------------------------------------
	//## state

	@volatile
	private var touched:MilliInstant	= MilliInstant.now()
	def alive():Boolean	= touched +! Constants.conversationTTL > MilliInstant.now()
	def touch():Unit	= { touched = MilliInstant.now() }

	//------------------------------------------------------------------------------
	//## client -> server

	private var lastClientCont	= 0L

	def handleHeartbeat():Unit	=
			callbacks conversationAlive id

	/** client -> server */
	def handleIncoming(incoming:Seq[JsonValue], clientCont:Long):Unit	=
		// after an aborted request there may be messages in incoming already seen before
		synchronized {
			// TODO how can it happen that requests overtake each other?
			if (clientCont < lastClientCont) {
				WARN(show"clientCont: ${lastClientCont}->${clientCont}")
				return
			}
			val expected	= (clientCont - lastClientCont)
			val count		= incoming.size
			val relevant	= incoming.size min expected.toInt
			lastClientCont	= clientCont
			incoming drop (count-relevant)
		}
		.foreach { it =>
			callbacks.messageReceived(id, it)
		}

	//------------------------------------------------------------------------------
	//## server -> client

	private case class Entry(id:Long, message:JsonValue)

	private var	nextId	= 0

	private var	entries:Vector[Entry]			= Vector.empty
	private var publishers:Option[Thunk[Unit]]	= None
	private var lastAppend:MilliInstant			= MilliInstant.now()

	/** server -> client */
	def appendOutgoing(message:JsonValue):Unit	=
		synchronized {
			val entry	= Entry(nextId, message)
			nextId		+= 1
			entries		+:= entry
			lastAppend	= MilliInstant.now()
		}

	/** called when the browser wants to receive some messages */
	def fetchOutgoing(serverCont:Long):Batch =
		synchronized {
			entries	= entries.filter { _.id >= serverCont }
			val	messages	= entries.reverse map { _.message }
			Batch(nextId, messages)
		}

	// NOTE was done directly in appendOutgoing and is now done in a worker thread
	// to give the queue a chance to grow a little bit before sending
	def maybePublish():Unit	= {
		synchronized {
			(MilliInstant.now() - lastAppend >= Constants.sendDelay) &&
			entries.nonEmpty flatOption {
				val out	= publishers
				publishers	= None
				out
			}
		}
		.foreach { it =>
			try { it() }
			catch { case e:Exception => ERROR("cannot publish", e) }
		}
	}

	/** used by the webserver to be notified there is data to fetch */
	def onHasOutgoing(publisher:Thunk[Unit]):Unit =
		synchronized {
			// NOTE if there already is an expired continuation, it is silently overwritten
			publishers	= Some(publisher)
		}
}
