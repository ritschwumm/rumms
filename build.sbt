name			:= "rumms"

organization	:= "de.djini"

version			:= "0.0.5"

scalaVersion	:= "2.9.1"

//publishArtifact in (Compile, packageBin)	:= false

publishArtifact in (Compile, packageDoc)	:= false

publishArtifact in (Compile, packageSrc)	:= false

libraryDependencies	++= Seq(
	"de.djini"				%%	"scutil"				% "0.0.5"			% "compile",
	"de.djini"				%%	"scfunk"				% "0.0.1"			% "compile",
	"de.djini"				%%	"scmirror"				% "0.0.1"			% "compile",
	"de.djini"				%%	"scjson"				% "0.0.5"			% "compile",
	"de.djini"				%%	"scwebapp"				% "0.0.2"			% "compile",
	"javax.servlet"			%	"servlet-api"			% "2.5"				% "provided",
	"org.eclipse.jetty"		%	"jetty-continuation"	% "7.6.0.v20120127"	% "compile",
	"org.eclipse.jetty"		%	"jetty-util"			% "7.6.0.v20120127"	% "compile",
	"commons-fileupload"	%	"commons-fileupload"	% "1.2.2"			% "compile"
)

scalacOptions	++= Seq("-deprecation", "-unchecked")
