Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / versionScheme := Some("early-semver")

name			:= "rumms"
organization	:= "de.djini"
version			:= "0.296.0"

scalaVersion	:= "2.13.5"
scalacOptions	++= Seq(
	"-feature",
	"-deprecation",
	"-unchecked",
	"-Werror",
	"-Xlint",
)

conflictManager		:= ConflictManager.strict withOrganization "^(?!(org\\.scala-lang|org\\.scala-js)(\\..*)?)$"
libraryDependencies	++= Seq(
	"de.djini"		%%	"scutil-jdk"		% "0.205.0"	% "compile",
	"de.djini"		%%	"scutil-guid"		% "0.205.0"	% "compile",
	"de.djini"		%%	"scjson-codec"		% "0.230.0"	% "compile",
	"de.djini"		%%	"scjson-converter"	% "0.230.0"	% "compile",
	"de.djini"		%%	"scwebapp-core"		% "0.259.0"	% "compile",
	"de.djini"		%%	"scwebapp-servlet"	% "0.259.0"	% "compile",
	"javax.servlet"	%   "javax.servlet-api"	% "3.1.0"	% "provided"
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
