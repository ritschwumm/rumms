name			:= "rumms"

organization	:= "de.djini"

version			:= "0.68.0"

scalaVersion	:= "2.11.1"

libraryDependencies	++= Seq(
	"de.djini"			%%	"scutil-core"			% "0.46.0"	% "compile",
	"de.djini"			%%	"scjson"				% "0.51.0"	% "compile",
	"de.djini"			%%	"scwebapp"				% "0.53.0"	% "compile",
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
