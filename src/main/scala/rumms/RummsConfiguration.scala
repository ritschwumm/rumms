package rumms

import scjson.JSONValue

case class RummsConfiguration(
	/** where the servlet is mounted */
	path:String,
	
	/** used for client-side version checking */
	version:String
) {
	require(path matches "^/[a-zA-Z0-9]+$", "path must be alphanumeric chars after a slash")
}
