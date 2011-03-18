import sbt._

final class RummsProject(info:ProjectInfo) extends ParentProject(info) {
	lazy val librarySub	= project("sub" / "framework",	"rumms-framework",	new FrameworkProject(_))
	lazy val demoSub	= project("sub" / "demo",		"rumms-demo",		new DemoProject(_),			librarySub)
	
	class FrameworkProject(info:ProjectInfo) extends DefaultProject(info) {
		val scutil				= "de.djini"				%% "scutil"				% "0.0.1"			% "compile"
		val scjson				= "de.djini"				%% "scjson"				% "0.0.1"			% "compile"
	
		val servlet_api			= "javax.servlet"			% "servlet-api"			% "2.5"				% "provided"
	
		val jetty7_continuation = "org.eclipse.jetty"		% "jetty-continuation"	% "7.3.0.v20110203" % "compile"
		val jetty7_util			= "org.eclipse.jetty"		% "jetty-util"			% "7.3.0.v20110203" % "compile"
	
		val commons_fileupload	= "commons-fileupload"		% "commons-fileupload"	% "1.2.1"			% "compile"
	}
	
	class DemoProject(info:ProjectInfo) extends DefaultWebProject(info) {
		val jetty7_server		= "org.eclipse.jetty"		% "jetty-server"		% "7.3.0.v20110203" % "test"
		val jetty7_webapp		= "org.eclipse.jetty"		% "jetty-webapp"		% "7.3.0.v20110203" % "test"
		val jetty7_servlets		= "org.eclipse.jetty"		% "jetty-servlets"		% "7.3.0.v20110203" % "test"
	}
}
