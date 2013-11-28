package rumms

import scutil.lang._
import scutil.Implicits._
import scutil.log._
import scutil.time._

final class Worker(delay:MilliDuration, task:Task) extends Disposable with Logging {
	@volatile
	private var alive	= true
	
	@volatile
	private var thread:Thread	= 
			new Thread {
				override def run() {
					while (alive) {
						try {
							task()
						}
						catch { case e:Exception	=>
							ERROR("worker error", e)
						}
						Thread sleep delay.millis
					}
				}
			}
			
	def start() {
		alive	= true
		thread.start()
	}
			
	def dispose() {
		alive	= false
		thread.join()
	}
}
