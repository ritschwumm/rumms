package rumms

import java.io.InputStream

import scutil.time._
import scutil.log._

import scjson._

final class Conversation(val id:ConversationId, controller:Controller) extends Logging {
	//------------------------------------------------------------------------------
	//## state
	
	@volatile private[rumms] var remoteUser:Option[String]	= None
	
	@volatile private var touched:Instant	= Instant.zero
	def alive:Boolean	= touched + Config.conversationTTL > Instant.now
	def touch()			{ touched = Instant.now }
	
	//------------------------------------------------------------------------------
	//## client -> server
	
	private var lastClientCont	= 0L
	
	/** client -> server */
	def handleIncoming(incoming:Seq[JSONValue], clientCont:Long) { 
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
	}
	
	def downloadContent(message:JSONValue):Option[Content]							= controller downloadContent	(id, message)
	def uploadContent(message:JSONValue, content:Content, fileName:String):Boolean	= controller uploadContent		(id, message, content, fileName)
	def uploadBatchCompleted() { controller uploadBatchCompleted id }
	
	//------------------------------------------------------------------------------
	//## server -> client
	
	private case class Entry(id:Long, message:JSONValue)
	
	private var	nextId	= 0
	
	private var	entries:List[Entry]			= Nil
	private var publishers:Option[()=>Unit]	= None
	
	/** server -> client */
	def appendOutgoing(message:JSONValue) {
		synchronized {
			val entry	= Entry(nextId, message)
			
			nextId		= nextId + 1
			entries		= entry :: entries
			
			val out	= publishers
			publishers	= None
			out
		} 
		.foreach { it =>
			try { it() }
			catch { case e:Exception => ERROR(e) }
		}
	}
	
	/** called when the browser wants to receive some messages */
	def fetchOutgoing(serverCont:Long):Batch = synchronized { 
		entries	= entries.filter { _.id >= serverCont }
		val	messages	= entries.reverse map { _.message }
		Batch(nextId, messages)
	}
	
	/** used by the webserver to be notified there is data to fetch */
	def onHasOutgoing(publisher:()=>Unit):Unit = synchronized {
		// NOTE if there already is an expired continuation, it is silently overwritten
		publishers	= Some(publisher)
	}
}
