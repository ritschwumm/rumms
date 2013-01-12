name			:= "rumms"

organization	:= "de.djini"

version			:= "0.11.0"

scalaVersion	:= "2.9.2"

libraryDependencies	++= Seq(
	"de.djini"					%%	"scutil"				% "0.12.0"				% "compile",
	"de.djini"					%%	"scjson"				% "0.12.0"				% "compile",
	"de.djini"					%%	"scwebapp"				% "0.9.0"				% "compile",
	"org.scalaz"				%%	"scalaz-core"			% "6.0.4"				% "compile",
	"commons-fileupload"		%	"commons-fileupload"	% "1.2.2"				% "compile",
	"org.eclipse.jetty"			%	"jetty-continuation"	% "8.1.7.v20120910"		% "compile",
	"org.eclipse.jetty"			%	"jetty-util"			% "8.1.7.v20120910"		% "compile",
	"javax.servlet"				%   "javax.servlet-api"		% "3.0.1"				% "provided"
	// @see https://github.com/harrah/xsbt/issues/499
	// "org.eclipse.jetty.orbit"	%	"javax.servlet"			% "3.0.0.v201112011016"	% "compile"	artifacts Artifact("javax.servlet", "jar", "jar"),
)

scalacOptions	++= Seq("-deprecation", "-unchecked")
