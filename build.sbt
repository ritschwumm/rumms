name			:= "rumms"
organization	:= "de.djini"
version			:= "0.183.1"

scalaVersion	:= "2.12.3"
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
	"de.djini"			%%	"scutil-core"		% "0.121.0"	% "compile",
	"de.djini"			%%	"scutil-uid"		% "0.121.0"	% "compile",
	"de.djini"			%%	"scjson-codec"		% "0.134.0"	% "compile",
	"de.djini"			%%	"scjson-pickle"		% "0.134.0"	% "compile",
	"de.djini"			%%	"scwebapp-core"		% "0.158.0"	% "compile",
	"de.djini"			%%	"scwebapp-servlet"	% "0.158.0"	% "compile",
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
