name			:= "rumms"
organization	:= "de.djini"
version			:= "0.84.0"

scalaVersion	:= "2.11.5"
scalacOptions	++= Seq(
	"-deprecation",
	"-unchecked",
	"-language:implicitConversions",
	// "-language:existentials",
	// "-language:higherKinds",
	// "-language:reflectiveCalls",
	// "-language:dynamics",
	// "-language:postfixOps",
	// "-language:experimental.macros"
	"-feature",
	"-Ywarn-unused-import",
	"-Xfatal-warnings"
)

conflictManager	:= ConflictManager.strict
libraryDependencies	++= Seq(
	"de.djini"			%%	"scutil-core"			% "0.62.0"	% "compile",
	"de.djini"			%%	"scjson"				% "0.67.0"	% "compile",
	"de.djini"			%%	"scwebapp"				% "0.69.0"	% "compile",
	"javax.servlet"		%   "javax.servlet-api"		% "3.0.1"	% "provided"
)
