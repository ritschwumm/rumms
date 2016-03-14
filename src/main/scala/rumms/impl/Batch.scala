package rumms.impl

import scutil.lang.ISeq

import scjson._

final case class Batch(serverCont:Long, messages:ISeq[JSONValue])
