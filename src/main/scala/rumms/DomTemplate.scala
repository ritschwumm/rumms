package rumms

import java.io.File

import scala.collection.mutable
import scala.io.Source
import scala.xml._
import scala.xml.parsing.ConstructingParser

import scalaz.{ Node => ZNode, Source => ZSource, _ }
import Scalaz._

object DomTemplate {
	type Safe[T]	= ValidationNEL[String,T]
	
	def compile(file:File):Safe[String]   =
			for {
				xml		<- loadXML(file)
				comp	<- DomTemplate compile xml
			} 
			yield comp
			
	def compile(string:String):Safe[String]   =
			for {
				xml		<- parseXML(string)
				comp	<- DomTemplate compile xml
			} 
			yield comp
	
	def loadXML(file:File):Safe[Node] =
			try {
				ConstructingParser.fromFile(file, true).document.docElem.success
			}
			catch {
				case e	=> 	("loading xml failed: " + file.getPath + " cause: " + e.getMessage).fail.liftFailNel
			}
			
	def parseXML(string:String):Safe[Node]	=
			try {
				ConstructingParser.fromSource(Source fromString string, true).document.docElem.success
			}
			catch {
				case e	=> ("parsing xml failed: " + e.getMessage).fail.liftFailNel
			}
			
	//------------------------------------------------------------------------------
			
	val hash		= "xid"
	val toplevel	= "$"
	
	/** compiles a template element with name and hash attributes into either some error messages or a JS function */
	def compile(node:Node):Safe[String] =
			for {
				template	<- toplevelElem(node)
				name		<- attrText(template, hash)
				body		<- statements(template, hash)
				checked		<- check(body)
			} 
			yield {
				function(name, body)
			}
	
	private case class Compiled(code:String, varName:Option[String], refs:List[VarRef])
	private case class VarRef(xid:String, varName:String)
	
	/** compiles a node into either some error messages or a dom-constructing JS function */
	private def statements(elem:Elem, hash:String):Safe[Compiled]	= {
		var	nextId		= 0
		def genId():Int	= { nextId += 1; nextId } 
		def freshName()	= "x" + genId()
		
		def compileNode(node:Node):Safe[Compiled] = node match {
			case x:Elem	=>
				val	varName		= freshName()
				val	ownCreate	= "var " + varName + " = document.createElement(" + escape(x.label) + ");"
				val ownAttrs	= x.attributes filter { _.key != hash } map { it:MetaData => varName + ".setAttribute(" + escape(it.key) + ", " + escape(it.value.text) + ");" }
				val ownRef		= attrText(x, hash).toOption map { VarRef(_, varName) } 
				
				val	subResults	= x.child map compileNode
				// TODO use applicative accumulation somehow
				val subProblems:Seq[NonEmptyList[String]]	= subResults flatMap { _.fail.toOption }
				subProblems.toList.toNel match {
					case Some(nel)	=> 	nel flatMap identity fail;
					case None		=>
						val sub			= subResults flatMap { _.toOption }
						val subCodes	= sub map { _.code }
						val subRefs		= sub.toList flatMap { _.refs }
						val appends		= sub flatMap { it => it.varName map { jt => varName + ".appendChild(" + jt + ");" } }
						
						val code	= (subCodes ++ List(ownCreate) ++ ownAttrs ++ appends) mkString "\n"
						val refs	= ownRef.toList ++ subRefs
						Compiled(code, Some(varName), refs).success
				}
				
			case x:Text	=>
				val	varName	= freshName()
				val	create	= "var " + varName + " = document.createTextNode(" + escape(x.text) + ");"
				Compiled(create, Some(varName), Nil).success
			
			case Comment(text)	=>
				val	comment	= "/* " + text + " */"	// TODO escape */
				Compiled(comment, None, Nil).success
				
			case _ => 
				("unexpected node: " + node).fail.liftFailNel 
		}
		
		compileNode(elem)
	}
	
	private def check(compiled:Compiled):Safe[Compiled] = {
		val hasDollar	= compiled.refs find { _.xid == toplevel } map { "forbidden id: " + _.xid }
		var seen	= new mutable.HashSet[String]
		var dups	= new mutable.ListBuffer[String]
		compiled.refs foreach { 
			case VarRef(k,_) if seen contains k	=> dups	+= k
			case VarRef(k,_) 					=> seen	+= k
			case _								=> ()
		}
		val	duplicates	= dups map { "duplicate id: " + _ }
		val problems:List[String]	= hasDollar.toList ++ duplicates
		problems.toNel toFailure compiled
	}	
	
	private def function(name:String, compiled:Compiled):String	=
			List(
				"function " + name + "() {",
				indent("\t", compiled.code),
				indent("\t", "return {"),
				indent("\t\t", 
					(VarRef(toplevel, "x1") :: compiled.refs) map {
						case VarRef(xid,varName)	=> escape(xid) + ":\t" + varName
					} mkString ",\n"
				),
				indent("\t", "};"),
				"}"
			) mkString "\n"
			
	//------------------------------------------------------------------------------
	
	private def toplevelElem(node:Node):Safe[Elem]	=
			node match {
				case x:Elem => x.success
				case _		=> "expected element at toplevel".fail.liftFailNel
			}
			
	private def attrText(elem:Elem, key:String):Safe[String]	=
			elem.attributes find (_.key == key) map (_.value.text) toSuccess nel("missing attribute: " + key)
	
	private def escape(s:String) = 
			s map {
				_ match {
					case '"' 	=> "\\\""
					case '\\'	=>	"\\\\"
					// this would be allowed but is ugly
					//case '/'	=> "\\/"
					// these are optional
					case '\b'	=> "\\b"
					case '\f'	=> "\\f"
					case '\n'	=> "\\n"
					case '\r'	=> "\\r"
					case '\t'	=> "\\t"
					case c 
					if c < 32	=> "\\u%04x".format(c.toInt)
					case c 		=> c.toString
				}
			} mkString("\"","","\"") 
	
	private def indent(prefix:String, s:String):String =
			s.replaceAll("(?m)^", prefix) 
}
