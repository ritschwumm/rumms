name			:= "rumms"

organization	:= "de.djini"

version			:= "0.0.4"

scalaVersion	:= "2.9.0-1"

//publishArtifact in (Compile, packageBin)	:= false

publishArtifact in (Compile, packageDoc)	:= false

publishArtifact in (Compile, packageSrc)	:= false

libraryDependencies	++= Seq(
	"de.djini"				%%	"scutil"				% "0.0.4"			% "compile",
	"de.djini"				%%	"scjson"				% "0.0.4"			% "compile",
	"de.djini"				%%	"scwebapp"				% "0.0.1"			% "compile",
	"javax.servlet"			%	"servlet-api"			% "2.5"				% "provided",
	"org.eclipse.jetty"		%	"jetty-continuation"	% "7.4.2.v20110526"	% "compile",
	"org.eclipse.jetty"		%	"jetty-util"			% "7.4.2.v20110526"	% "compile",
	"commons-fileupload"	%	"commons-fileupload"	% "1.2.1"			% "compile"
)

scalacOptions	++= Seq("-deprecation", "-unchecked")
