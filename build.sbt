name			:= "rumms"

organization	:= "de.djini"

version			:= "0.13.0"

scalaVersion	:= "2.9.2"

libraryDependencies	++= Seq(
	"de.djini"				%%	"scutil"				% "0.14.0"				% "compile",
	"de.djini"				%%	"scjson"				% "0.14.0"				% "compile",
	"de.djini"				%%	"scwebapp"				% "0.11.0"				% "compile",
	"org.scalaz"			%%	"scalaz-core"			% "6.0.4"				% "compile",
	"commons-fileupload"	%	"commons-fileupload"	% "1.2.2"				% "compile",
	"org.eclipse.jetty"		%	"jetty-continuation"	% "8.1.8.v20121106"		% "compile",
	"org.eclipse.jetty"		%	"jetty-util"			% "8.1.8.v20121106"		% "compile",
	"javax.servlet"			%   "javax.servlet-api"		% "3.0.1"				% "provided"
)

scalacOptions	++= Seq("-deprecation", "-unchecked")
