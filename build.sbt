name			:= "rumms"
organization	:= "de.djini"
version			:= "0.185.0"

scalaVersion	:= "2.12.4"
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
	"-Xfatal-warnings",
	"-Xlint"
)

conflictManager	:= ConflictManager.strict
libraryDependencies	++= Seq(
	"de.djini"			%%	"scutil-core"		% "0.123.0"	% "compile",
	"de.djini"			%%	"scutil-uid"		% "0.123.0"	% "compile",
	"de.djini"			%%	"scjson-codec"		% "0.136.0"	% "compile",
	"de.djini"			%%	"scjson-pickle"		% "0.136.0"	% "compile",
	"de.djini"			%%	"scwebapp-core"		% "0.160.0"	% "compile",
	"de.djini"			%%	"scwebapp-servlet"	% "0.160.0"	% "compile",
	"javax.servlet"		%   "javax.servlet-api"	% "3.1.0"	% "provided"
)

wartremoverErrors ++= Seq(
	Wart.StringPlusAny,
	Wart.EitherProjectionPartial,
	Wart.OptionPartial,
	Wart.Enumeration,
	Wart.FinalCaseClass,
	Wart.JavaConversions,
	Wart.Option2Iterable,
	Wart.TryPartial
)
