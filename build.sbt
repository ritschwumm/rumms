name			:= "rumms"
organization	:= "de.djini"
version			:= "0.207.0"

scalaVersion	:= "2.12.6"
scalacOptions	++= Seq(
	"-deprecation",
	"-unchecked",
	// "-language:implicitConversions",
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
	"de.djini"			%%	"scutil-core"		% "0.144.0"	% "compile",
	"de.djini"			%%	"scutil-guid"		% "0.144.0"	% "compile",
	"de.djini"			%%	"scjson-codec"		% "0.158.0"	% "compile",
	"de.djini"			%%	"scjson-pickle"		% "0.158.0"	% "compile",
	"de.djini"			%%	"scwebapp-core"		% "0.181.0"	% "compile",
	"de.djini"			%%	"scwebapp-servlet"	% "0.181.0"	% "compile",
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
	Wart.TryPartial,
	Wart.JavaSerializable,
	//Wart.Any,
	Wart.AnyVal,
	//Wart.Nothing,
	Wart.ArrayEquals,
	Wart.ExplicitImplicitTypes,
	Wart.LeakingSealed
	//Wart.Overloading
	//Wart.PublicInference,
	//Wart.TraversableOps
)
