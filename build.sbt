name			:= "rumms"
organization	:= "de.djini"
version			:= "0.240.0"

scalaVersion	:= "2.13.1"
scalacOptions	++= Seq(
	"-deprecation",
	"-unchecked",
	// "-language:implicitConversions",
	// "-language:existentials",
	// "-language:higherKinds",
	// "-language:reflectiveCalls",
	// "-language:dynamics",
	// "-language:experimental.macros"
	"-feature",
	"-Xfatal-warnings",
	"-Xlint"
)

conflictManager		:= ConflictManager.strict withOrganization "^(?!(org\\.scala-lang|org\\.scala-js)(\\..*)?)$"
libraryDependencies	++= Seq(
	"de.djini"			%%	"scutil-core"		% "0.167.0"	% "compile",
	"de.djini"			%%	"scutil-guid"		% "0.167.0"	% "compile",
	"de.djini"			%%	"scjson-codec"		% "0.185.0"	% "compile",
	"de.djini"			%%	"scjson-pickle"		% "0.185.0"	% "compile",
	"de.djini"			%%	"scwebapp-core"		% "0.212.0"	% "compile",
	"de.djini"			%%	"scwebapp-servlet"	% "0.212.0"	% "compile",
	"javax.servlet"		%   "javax.servlet-api"	% "3.1.0"	% "provided"
)

wartremoverErrors ++= Seq(
	Wart.AsInstanceOf,
	Wart.IsInstanceOf,
	Wart.StringPlusAny,
	Wart.ToString,
	Wart.EitherProjectionPartial,
	Wart.OptionPartial,
	Wart.TryPartial,
	Wart.Enumeration,
	Wart.FinalCaseClass,
	Wart.JavaConversions,
	Wart.Option2Iterable,
	Wart.JavaSerializable,
	//Wart.Any,
	Wart.AnyVal,
	//Wart.Nothing,
	Wart.ArrayEquals,
	Wart.ImplicitParameter,
	Wart.ExplicitImplicitTypes,
	Wart.LeakingSealed,
	Wart.DefaultArguments,
	Wart.Overloading,
	//Wart.PublicInference,
	Wart.TraversableOps,
)
