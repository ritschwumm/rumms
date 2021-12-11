package rumms.impl

import scjson.ast.*

final case class Batch(serverCont:Long, messages:Seq[JsonValue])
