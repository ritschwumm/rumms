Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / versionScheme := Some("early-semver")

name			:= "rumms"
organization	:= "de.djini"
version			:= "0.328.0"

scalaVersion	:= "3.2.0"
scalacOptions	++= Seq(
	"-feature",
	"-deprecation",
	"-unchecked",
	"-Wunused:all",
	"-Xfatal-warnings",
	"-Ykind-projector:underscores",
)

conflictManager		:= ConflictManager.strict withOrganization "^(?!(org\\.scala-lang|org\\.scala-js)(\\..*)?)$"
libraryDependencies	++= Seq(
	"de.djini"			%%	"scutil-jdk"			% "0.232.0"	% "compile",
	"de.djini"			%%	"scutil-guid"			% "0.232.0"	% "compile",
	"de.djini"			%%	"scjson-codec"			% "0.260.0"	% "compile",
	"de.djini"			%%	"scjson-converter"		% "0.260.0"	% "compile",
	"de.djini"			%%	"scwebapp-core"			% "0.290.0"	% "compile",
	"de.djini"			%%	"scwebapp-servlet"		% "0.290.0"	% "compile",
	"jakarta.servlet"	%   "jakarta.servlet-api"	% "5.0.0"	% "provided"
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
	//Wart.TraversableOps,
)
