name			:= "rumms"
organization	:= "de.djini"
version			:= "0.129.0"

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
	"de.djini"			%%	"scutil-core"			% "0.80.0"	% "compile",
	"de.djini"			%%	"scutil-uid"			% "0.80.0"	% "compile",
	"de.djini"			%%	"scjson"				% "0.85.0"	% "compile",
	"de.djini"			%%	"scwebapp-core"			% "0.109.0"	% "compile",
	"de.djini"			%%	"scwebapp-servlet"		% "0.109.0"	% "compile",
	"javax.servlet"		%   "javax.servlet-api"		% "3.1.0"	% "provided"
)
