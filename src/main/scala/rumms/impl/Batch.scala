package rumms.impl

import scutil.lang.ISeq

import scjson._

case class Batch(serverCont:Long, messages:ISeq[JSONValue])
