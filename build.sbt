Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / versionScheme := Some("early-semver")

name			:= "rumms"
organization	:= "de.djini"
version			:= "0.365.0"

scalaVersion	:= "3.7.3"
scalacOptions	++= Seq(
	"-preview",
	"-feature",
	"-deprecation",
	"-unchecked",
	"-source:future",
	"-language:experimental.into",
	"-Wunused:all",
	// TODO enable this
	// "-Wsafe-init",
	"-Wshadow:all",
	"-Xfatal-warnings",
	"-Xkind-projector:underscores",
)

libraryDependencies	++= Seq(
	"de.djini"			%%	"scutil-jdk"			% "0.266.0"	% "compile",
	"de.djini"			%%	"scutil-guid"			% "0.266.0"	% "compile",
	"de.djini"			%%	"scjson-codec"			% "0.294.0"	% "compile",
	"de.djini"			%%	"scjson-converter"		% "0.294.0"	% "compile",
	"de.djini"			%%	"scwebapp-core"			% "0.327.0"	% "compile",
	"de.djini"			%%	"scwebapp-servlet"		% "0.327.0"	% "compile",
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
	Wart.Option2Iterable,
	//Wart.Any,
	Wart.AnyVal,
	//Wart.Nothing,
	Wart.ArrayEquals,
	Wart.LeakingSealed,
	Wart.DefaultArguments,
	Wart.Overloading,
	//Wart.IterableOps,
)
