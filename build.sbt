name			:= "rumms"

organization	:= "de.djini"

version			:= "0.30.0"

scalaVersion	:= "2.10.2"

libraryDependencies	++= Seq(
	"de.djini"				%%	"scutil"				% "0.26.0"	% "compile",
	"de.djini"				%%	"scjson"				% "0.29.0"	% "compile",
	"de.djini"				%%	"scwebapp"				% "0.23.0"	% "compile",
	"commons-fileupload"	%	"commons-fileupload"	% "1.3"		% "compile",
	"javax.servlet"			%   "javax.servlet-api"		% "3.0.1"	% "provided"
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
