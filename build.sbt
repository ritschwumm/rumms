Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / versionScheme := Some("early-semver")

name			:= "rumms"
organization	:= "de.djini"
version			:= "0.336.0"

scalaVersion	:= "3.3.0"
scalacOptions	++= Seq(
	"-feature",
	"-deprecation",
	"-unchecked",
	"-Wunused:all",
	"-Xfatal-warnings",
	"-Ykind-projector:underscores",
)

libraryDependencies	++= Seq(
	"de.djini"			%%	"scutil-jdk"			% "0.238.0"	% "compile",
	"de.djini"			%%	"scutil-guid"			% "0.238.0"	% "compile",
	"de.djini"			%%	"scjson-codec"			% "0.266.0"	% "compile",
	"de.djini"			%%	"scjson-converter"		% "0.266.0"	% "compile",
	"de.djini"			%%	"scwebapp-core"			% "0.298.0"	% "compile",
	"de.djini"			%%	"scwebapp-servlet"		% "0.298.0"	% "compile",
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
