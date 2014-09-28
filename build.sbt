name			:= "rumms"

organization	:= "de.djini"

version			:= "0.73.1"

scalaVersion	:= "2.11.2"

libraryDependencies	++= Seq(
	"de.djini"			%%	"scutil-core"			% "0.51.1"	% "compile",
	"de.djini"			%%	"scjson"				% "0.56.1"	% "compile",
	"de.djini"			%%	"scwebapp"				% "0.58.1"	% "compile",
	"javax.servlet"		%   "javax.servlet-api"		% "3.0.1"	% "provided"
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
