name			:= "rumms"

organization	:= "de.djini"

version			:= "0.77.0"

scalaVersion	:= "2.11.4"

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

conflictManager	:= ConflictManager.strict

libraryDependencies	++= Seq(
	"de.djini"			%%	"scutil-core"			% "0.55.0"	% "compile",
	"de.djini"			%%	"scjson"				% "0.60.0"	% "compile",
	"de.djini"			%%	"scwebapp"				% "0.62.0"	% "compile",
	"javax.servlet"		%   "javax.servlet-api"		% "3.0.1"	% "provided"
)
