name			:= "rumms"

organization	:= "de.djini"

version			:= "0.76.0"

scalaVersion	:= "2.11.2"

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
	"de.djini"			%%	"scutil-core"			% "0.54.0"	% "compile",
	"de.djini"			%%	"scjson"				% "0.59.0"	% "compile",
	"de.djini"			%%	"scwebapp"				% "0.61.0"	% "compile",
	"javax.servlet"		%   "javax.servlet-api"		% "3.0.1"	% "provided"
)
