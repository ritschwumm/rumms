package rumms

import scjson._

case class Batch(serverCont:Long, messages:Seq[JSONValue])
