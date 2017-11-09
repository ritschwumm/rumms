package rumms.impl

import scutil.lang.ISeq

import scjson.ast._

final case class Batch(serverCont:Long, messages:ISeq[JsonValue])
