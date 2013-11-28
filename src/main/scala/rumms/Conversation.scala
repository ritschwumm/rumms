package rumms

import java.io.InputStream

import scutil.lang._
import scutil.Implicits._
import scutil.time._
import scutil.log._

import scjson._

final class Conversation(val id:ConversationId, controller:Controller) extends Logging {
	//------------------------------------------------------------------------------
	//## state
	
	// TODO ugly
	@volatile 
	private[rumms] var remoteUser:Option[String]	= None
	
	@volatile 
	private var touched:MilliInstant	= MilliInstant.zero
	def alive:Boolean	= touched + Config.conversationTTL > MilliInstant.now
	def touch()			{ touched = MilliInstant.now }
	
	//------------------------------------------------------------------------------
	//## client -> server
	
	private var lastClientCont	= 0L
	
	/** client -> server */
	def handleIncoming(incoming:Seq[JSONValue], clientCont:Long):Unit	=
			// after an aborted request there may be messages in incoming already seen before
			synchronized {
				val expected	= (clientCont - lastClientCont)
				val count		= incoming.size
				val relevant	= incoming.size min expected.toInt
				lastClientCont	= clientCont
				incoming drop (count-relevant) 
			} 
			.foreach { it => 
				controller receiveMessage (id, it) 
			}
	
	def downloadContent(message:JSONValue):Option[Content]	=
			controller downloadContent	(id, message)
		
	def uploadContent(message:JSONValue, content:Content):Boolean	= 
			controller uploadContent		(id, message, content)
	
	def uploadBatchBegin()	{ controller uploadBatchBegin	id }
	def uploadBatchEnd()	{ controller uploadBatchEnd		id }
	
	//------------------------------------------------------------------------------
	//## server -> client
	
	private case class Entry(id:Long, message:JSONValue)
	
	private var	nextId	= 0
	
	private var	entries:Vector[Entry]	= Vector.empty
	private var publishers:Option[Task]	= None
	private var lastAppend:MilliInstant	= MilliInstant.now
	
	/** server -> client */
	def appendOutgoing(message:JSONValue):Unit	=
			synchronized {
				val entry	= Entry(nextId, message)
				
				nextId		+= 1
				entries		+:= entry
				
				lastAppend	= MilliInstant.now
				
				// val out	= publishers
				// publishers	= None
				// out
			} 
			// .foreach { it =>
			// 	try { it() }
			// 	catch { case e:Exception => ERROR(e) }
			// }
	
	/** called when the browser wants to receive some messages */
	def fetchOutgoing(serverCont:Long):Batch =
			synchronized { 
				entries	= entries.filter { _.id >= serverCont }
				val	messages	= entries.reverse map { _.message }
				Batch(nextId, messages)
			}
			
	// NOTE was done directly in appendOutgoing and is now done in a worker thread
	// to give the queue a chance to grow a little bit before sending
	def maybePublish() {
		synchronized {
			(MilliInstant.now - lastAppend >= Config.sendDelay) &&
			entries.nonEmpty flatGuard {
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
	def onHasOutgoing(publisher:Task):Unit =
			synchronized {
				// NOTE if there already is an expired continuation, it is silently overwritten
				publishers	= Some(publisher)
			}
}
