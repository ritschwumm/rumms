name			:= "rumms"

organization	:= "de.djini"

version			:= "0.19.0"

scalaVersion	:= "2.10.1"

libraryDependencies	++= Seq(
	"de.djini"				%%	"scutil"				% "0.18.0"	% "compile",
	"de.djini"				%%	"scjson"				% "0.19.0"	% "compile",
	"de.djini"				%%	"scwebapp"				% "0.15.0"	% "compile",
	"commons-fileupload"	%	"commons-fileupload"	% "1.2.2"	% "compile",
	"javax.servlet"			%   "javax.servlet-api"		% "3.0.1"	% "provided"
)

scalacOptions	++= Seq(
	"-deprecation",
	"-unchecked",
	"-language:implicitConversions",
	// "-language:existentials",
	// "-language:higherKinds",
	// "-language:reflectiveCalls",
	// "-language:dynamics",
	"-language:postfixOps",
	// "-language:experimental.macros"
	"-feature"
)
