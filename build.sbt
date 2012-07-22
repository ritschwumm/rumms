name			:= "rumms"

organization	:= "de.djini"

version			:= "0.7.0"

scalaVersion	:= "2.9.2"

libraryDependencies	++= Seq(
	"de.djini"				%%	"scutil"				% "0.8.0"			% "compile",
	"de.djini"				%%	"scmirror"				% "0.4.0"			% "compile",
	"de.djini"				%%	"scjson"				% "0.8.0"			% "compile",
	"de.djini"				%%	"scwebapp"				% "0.5.0"			% "compile",
	"org.scalaz"			%%	"scalaz-core"			% "6.0.4"			% "compile",
	"javax.servlet"			%	"servlet-api"			% "2.5"				% "provided",
	"org.eclipse.jetty"		%	"jetty-continuation"	% "7.6.0.v20120127"	% "compile",
	"org.eclipse.jetty"		%	"jetty-util"			% "7.6.0.v20120127"	% "compile",
	"commons-fileupload"	%	"commons-fileupload"	% "1.2.2"			% "compile"
)

scalacOptions	++= Seq("-deprecation", "-unchecked")
