name			:= "rumms"
organization	:= "de.djini"
version			:= "0.96.0"

scalaVersion	:= "2.11.7"
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
	"de.djini"			%%	"scutil-core"			% "0.71.0"	% "compile",
	"de.djini"			%%	"scjson"				% "0.76.0"	% "compile",
	"de.djini"			%%	"scwebapp"				% "0.79.0"	% "compile",
	"javax.servlet"		%   "javax.servlet-api"		% "3.0.1"	% "provided"
)
