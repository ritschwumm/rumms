name			:= "rumms"
organization	:= "de.djini"
version			:= "0.133.0"

scalaVersion	:= "2.11.8"
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
	"de.djini"			%%	"scutil-core"			% "0.83.0"	% "compile",
	"de.djini"			%%	"scutil-uid"			% "0.83.0"	% "compile",
	"de.djini"			%%	"scjson"				% "0.88.0"	% "compile",
	"de.djini"			%%	"scwebapp-core"			% "0.113.0"	% "compile",
	"de.djini"			%%	"scwebapp-servlet"		% "0.113.0"	% "compile",
	"javax.servlet"		%   "javax.servlet-api"		% "3.1.0"	% "provided"
)

wartremoverErrors ++= Seq(
	Wart.Any2StringAdd,
	Wart.EitherProjectionPartial,
	Wart.OptionPartial,
	Wart.Enumeration,
	Wart.FinalCaseClass,
	Wart.JavaConversions,
	Wart.Option2Iterable,
	Wart.TryPartial
)
