package rumms

import java.io.InputStream

import scutil.log.Logging

import scjson._

case class Batch(serverCont:Long, messages:List[JSValue])

final class Conversation(val id:ConversationId, controller:Controller) extends Logging {
	//------------------------------------------------------------------------------
	//## client -> server
	
	private var lastClientCont	= 0L
	
	/** client -> server */
	def handleIncoming(incoming:Seq[JSValue], clientCont:Long) { 
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
	
	def handleUpload(message:JSValue, upload:Upload):Boolean	= controller handleUpload	(id, message, upload)
	def handleDownload(message:JSValue):Option[Download]		= controller handleDownload	(id, message)
	
	def uploadBatchCompleted() { controller uploadBatchCompleted id }
	
	//------------------------------------------------------------------------------
	//## server -> client
	
	private case class Entry(id:Long, message:JSValue)
	
	private var	nextId	= 0
	
	private var	entries:List[Entry]			= Nil
	private var publishers:Option[()=>Unit]	= None
	
	/** server -> client */
	def appendOutgoing(message:JSValue) {
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
