import sbt._

final class RummsProject(info:ProjectInfo) extends DefaultProject(info) {
	val scutil				= "de.djini"				%% "scutil"				% "0.0.2"			% "compile"
	val scjson				= "de.djini"				%% "scjson"				% "0.0.2"			% "compile"

	val servlet_api			= "javax.servlet"			% "servlet-api"			% "2.5"				% "provided"

	val jetty7_continuation = "org.eclipse.jetty"		% "jetty-continuation"	% "7.3.0.v20110203" % "compile"
	val jetty7_util			= "org.eclipse.jetty"		% "jetty-util"			% "7.3.0.v20110203" % "compile"

	val commons_fileupload	= "commons-fileupload"		% "commons-fileupload"	% "1.2.1"			% "compile"
	
	// issue compiler warnings
	override def compileOptions	= super.compileOptions ++ Seq(Unchecked)
}
