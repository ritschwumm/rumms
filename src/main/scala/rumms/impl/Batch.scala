package rumms.impl

import scjson.ast._

final case class Batch(serverCont:Long, messages:Seq[JsonValue])
