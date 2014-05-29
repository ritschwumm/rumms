name			:= "rumms"

organization	:= "de.djini"

version			:= "0.66.0"

scalaVersion	:= "2.11.0"

libraryDependencies	++= Seq(
	"de.djini"			%%	"scutil-core"			% "0.44.0"	% "compile",
	"de.djini"			%%	"scjson"				% "0.49.0"	% "compile",
	"de.djini"			%%	"scwebapp"				% "0.51.0"	% "compile",
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
