name			:= "rumms"

organization	:= "de.djini"

version			:= "0.64.0"

scalaVersion	:= "2.10.3"

libraryDependencies	++= Seq(
	"de.djini"			%%	"scutil-core"			% "0.42.0"	% "compile",
	"de.djini"			%%	"scjson"				% "0.47.0"	% "compile",
	"de.djini"			%%	"scwebapp"				% "0.49.0"	% "compile",
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
