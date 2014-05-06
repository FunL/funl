package funl.interp

import java.lang.reflect.{Method, Modifier}

import collection.mutable.{ArrayBuffer, ArrayStack, ListBuffer, HashMap, HashSet}
import collection.immutable.ListMap
import util.parsing.input.{Reader, CharSequenceReader}
import math._
import compat.Platform._

import funl.lia.{Complex, Math}


class Evaluator
{
	type Function = List[Any] => Any
	type SymbolMap = HashMap[Symbol, Any]
	
	class Datatype( name: String )
	
	case class Constructor( datatype: Symbol, name: Symbol, fields: List[Symbol] )

	case class ClassMethodCall( c: Class[Any], m: List[Method] )
	case class ObjectMethodCall( o: Any, m: List[Method] )
	
	class Record( val datatype: Symbol, val name: Symbol, val fields: List[Symbol], val args: List[Any] )
	{
		private val map = ListMap[Symbol, Any]( (fields zip args): _* )

		def get( key: Symbol ) = map.get( key )

		override def hashCode = datatype.hashCode ^ name.hashCode ^ fields.hashCode ^ args.hashCode

		override def equals( o: Any ) =
			o match
			{
				case r: Record => name == r.name && args == r.args
				case _ => false
			}
			
		override def toString = name + (if (args isEmpty) "" else args.mkString("( ", ", ", " )"))
	}

	class HolderMap( init: collection.Map[Any, Any] ) extends collection.Map[Any, Holder]
	{
		private val map = new HashMap[Any, Holder]

		if (init ne null)
			for ((k, v) <- init)
				map(k) = new Holder( v )

		override def default( key: Any ) =
		{
		val value = new Holder( null )

			map(key) = value
			value
		}

 		def get( key: Any ) = map.get( key )

		def iterator = map.iterator

		override def size = map.size
		
		def + [B1 >: Holder]( kv: (Any, B1) ) =
		{
			map + kv
			this
		}

		def -( key: Any ) =
		{
			map - key
			this
		}

		override def empty = new HolderMap( null )

		override def toString = map.toList.map( kv => _display(kv._1) + ": " + _display(deref(kv._2)) ).mkString( "{", ", ", "}" )
	}

	class HolderArray( dim: Int ) extends collection.Seq[Holder]
	{
		private val array = new Array[Holder]( dim )

		for (i <- 0 until dim)
			array(i) = new Holder( null )

		def apply( index: Int ) = array( index )

		def length = dim

		def iterator = array.iterator

		override def hashCode = array.hashCode

		override def equals( that: Any ) = that.isInstanceOf[HolderArray] &&
			((that.asInstanceOf[HolderArray] eq this) || that.asInstanceOf[HolderArray].array == array)

		override def toString = array map (a => display(deref( a ))) mkString( "Array(", ", ", ")" )
	}

	class Holder( var v: Any )

	class Activation( val closure: Closure, val module: Module )
	{
		val scope = new ArrayStack[SymbolMap]
		
		override def toString = "Activation( " + closure + ", " + scope + " )"
	}
	
	class Closure( val calling: Activation, val func: List[FunctionExprAST] )
	{
		override def toString = "<closure>"
	}

	class BreakThrowable extends Throwable

	class ContinueThrowable extends Throwable

	class Module( name: String, symbols: SymbolMap )

	val symbols = new SymbolMap
	val datatypes = new HashSet[Symbol]
	val stack = new StackArray[Any]
	val activations = new ArrayStack[Activation]
	
	var last: Any = null

	def assign( key: Symbol, value: Any )
	{
		if (variables contains key)
			sys.error( "already declared: " + key )
		else
			variables(key) = value
	}
	
	def assign( vs: (Symbol, Any)* ): Evaluator =
	{
		for ((k, v) <- vs)
			assign( k, v )

		this
	}

	def _display( a: Any ): String =
		a match
		{
			case l: List[_] => l.mkString( "[", ", ", "]" )
			case s: Set[_] => s.mkString( "{", ", ", "}" )
			case m: Map[_, _] => m.toList.map( e => _display(e._1) + ": " + _display(e._2) ).mkString( "{", ", ", "}" )
			case t: Vector[_] => t.mkString( "(", ", ", ")" )
			case s: String => "\"" + s + '"'
			case _ => String.valueOf( a )
		}

	def display( a: Any ) =
		if (a != null && a.isInstanceOf[String])
			a.toString
		else
			_display( a )
	
	assign(
		'i -> Complex( 0, 1 ),
		'sys -> this
		)

	function( 'println, a => println(a map (display(_)) mkString(", ")) )
	function( 'print, a => print(a map (display(_)) mkString(", ")) )
	function( 'Array, a => new HolderArray( a.head.asInstanceOf[Int]) )
	function( 'Map, a =>
		if (a isEmpty)
			new HolderMap( null )
		else
			new HolderMap( a.head.asInstanceOf[collection.Map[Any, Any]] ) )
	function( 'class, a => a.head.getClass )
	function( 'error, a => {println(a.head.toString); sys.exit( 1 )} )
	function( 'require, a => require(a.head.asInstanceOf[Boolean], a.last.toString) )
	function( 'symbol, a => Symbol(String.valueOf(a.head)) )
	
	def function( n: Symbol, f: Function ) = variables(n) = f

	def eval( t: AST ) =
	{
		apply( t )
		pop
	}

	def neval( t: AST ) = eval( t ).asInstanceOf[Number]
	
	def ieval( t: AST ) = eval( t ).asInstanceOf[Int]
	
	def deval( t: AST ) = eval( t ).asInstanceOf[Number].doubleValue
	
	def beval( t: AST ) = eval( t ).asInstanceOf[Boolean]
	
	def heval( t: AST ) =
	{
		apply( t, true )
		hpop
	}
	
	def pop = deref( stack.pop )
		
	def push( v: Any ) = stack.push( if (v.isInstanceOf[Long]) BigInt(v.asInstanceOf[Long]) else v )
	
	def hpop = stack.pop.asInstanceOf[Holder]
	
	def dpop = pop.asInstanceOf[Double]
	
	def bpop = pop.asInstanceOf[Boolean]
	
	def void = stack push ()
	
	def list( len: Int ) =
	{
	val buf = new ListBuffer[Any]
	
		for (i <- 1 to len)
			buf prepend pop
			
		buf.toList
	}
	
	def enterEnvironment( closure: Closure, module: Module )
	{
		activations push new Activation( closure, module )
		enterScope
	}
	
	def exitEnvironment = activations.pop
	
	def enterScope = activations.top.scope push new SymbolMap

// 	def topScope
// 	{
// 		activations.top.scope.clear
// 		enterScope
// 	}

	def exitScope = activations.top.scope pop
	
	def assignOperation( h: Holder, op: Symbol, n: Number ) =
	{
	val v = Math( op, h.v, n )
		
		h.v = v
		v
	}
	
	def apply( l: List[AST] ): Any =
		for (s <- l)
			apply( s )

	def apply( t: AST, creatvars: Boolean = false ): Any =
	{
		def vars( name: Symbol ) =
		{
			def vars( a: Activation ): Any =
			{
				a.scope find (_ contains name) match
				{
					case None =>
						if (a.closure != null && a.closure.calling != null)
							vars( a.closure.calling )
						else if (variables contains name)
							variables(name)
						else if (creatvars)
							newVar( name )
						else
							sys.error( "unknown variable: " + name )
					case Some( m ) => m(name)
				}
			}
			
			vars( activations.top )
		}
		
		def newVar( name: Symbol ) =
		{
			val holder = new Holder( null )
			
			activations.top.scope.top(name) = holder
			
			holder
		}
		
		t match
		{
			case SourceAST( cs ) =>
				apply( cs )
			case NativeAST( pkg, names ) =>
				for ((n, a) <- names)
					assign( if (a == None) Symbol(n) else a.get, Class.forName( pkg + '.' + n ) )
			case ConstAST( name, expr ) =>
				assign( name, eval(expr) )
			case VarAST( n, v ) =>
				if (variables contains n)
					sys.error( "already declared: " + n )
				else
					variables(n) = new Holder( if (v == None) null else eval(v.get) )
			case DataAST( n, cs ) =>
				if (datatypes contains n) sys.error( "already declared: " + n )
				
				datatypes.add( n )

				for ((name, fields) <- cs)
					if (fields isEmpty)
						assign( name, new Record(n, name, Nil, Nil) )
					else
						assign( name, Constructor(n, name, fields) )
			case DefAST( name, func ) =>
				if (variables contains name)
				{
					if (variables(name).isInstanceOf[Closure])
						variables(name) = new Closure( null, variables(name).asInstanceOf[Closure].func :+ func )
					else
						sys.error( "already declared: " + name )
				}
				else
					variables( name ) = new Closure( null, List(func) )
			case MainAST( l ) =>
				enterEnvironment( null )
				apply( l )
			case ExpressionStatementAST( e ) =>
				last = eval( e )
			case ValStatementAST( p, e ) =>
				last = eval( e )
				clear( activations.top.scope.top, p )

				if (!unify( activations.top.scope.top, last, p ))
					sys.error( "unification failure" )
			case BreakExprAST =>
				throw new BreakThrowable
			case ContinueExprAST =>
				throw new ContinueThrowable
			case DoubleLiteralExprAST( d ) => push( d )
			case IntegerLiteralExprAST( i ) => push( i )
			case BooleanLiteralExprAST( b ) => push( b )
			case StringLiteralExprAST( s ) =>
				val buf = new StringBuilder
				
				def chr( r: Reader[Char] )
				{
						if (!r.atEnd)
						{
							if (r.first == '\\')
							{
								if (r.rest.atEnd)
									sys.error( "unexpected end of string" )

								if (r.rest.first == 'u')
								{
								var u = r.rest.rest
								
									def nextc =
										if (u.atEnd)
											sys.error( "unexpected end of string inside unicode sequence" )
										else
										{
										val res = u.first

											u = u.rest
											res
										}

									buf append Integer.valueOf( new String(Array(nextc, nextc, nextc, nextc)) ).toChar
									chr( u )
								}
								else
								{
									buf.append(
										Map (
											'\\' -> '\\', '\'' -> '\'', '/' -> '/', 'b' -> '\b', 'f' -> '\f', 'n' -> '\n', 'r' -> '\r', 't' -> '\t'
										).get(r.rest.first) match
										{
											case Some( c ) => c
											case _ => sys.error( "illegal escape character " + r.rest.first )
										} )

									chr( r.rest.rest )
								}
							}
							else
							{
								buf append r.first	
								chr( r.rest )
							}
						}
				}

				chr( new CharSequenceReader(s) )
				push( buf.toString )
			case BinaryExprAST( left, op, right ) =>
				val l = eval( left )
				val r = eval( right )

				op match
				{
					case 'in | 'notin =>
						val res = r match
						{
							case seq: collection.Seq[Any] => seq contains l
							case set: collection.Set[Any] => set contains l
							case map: collection.Map[Any, Any] => map contains l
							case _ => sys.error( "illegal use of 'in' predicate" )
						}

						push( (op == 'notin)^res )
					case '+ =>
						if (l.isInstanceOf[String] || r.isInstanceOf[String])
							push( l.toString + r.toString )
						else if (l.isInstanceOf[Iterable[_]] && r.isInstanceOf[Iterable[_]])
							push( l.asInstanceOf[Iterable[_]] ++ r.asInstanceOf[Iterable[_]] )
						else if (l.isInstanceOf[Number] && r.isInstanceOf[Number])
							push( Math(op, l, r) )
						else
							sys.error( "operation '" + op.name + "' not applicable to values: '" + l + "', '" + r + "'" )
					case '< | '> | '<= | '>= =>
						if (l.isInstanceOf[String] || r.isInstanceOf[String])
						{
						val c = l.toString.compare( r.toString )
						val res =
							op match
							{
								case '< => c < 0
								case '> => c > 0
								case '<= => c <= 0
								case '>= => c >= 0
							}
							
							push( res )
						}
						else
							push( Math(op, l, r) )
					case '== | '/= =>
						if (l.isInstanceOf[Number] && r.isInstanceOf[Number])
							push( Math(op, l, r) )
						else
							push( if (op == '==) l == r else l != r )
					case _ =>//'- | '^ | '/ | '* | `FLOORDIV` =>
						push( Math(op, l, r) )
				}
			case BooleanConnectiveExprAST( left, op, right ) =>
				op match
				{
					case 'or =>
						if (beval( left ))
							push( true )
						else
							push( beval(right) )
					case 'xor =>
						push( beval(left) ^ beval(right) )
					case 'and =>
						if (!beval( left ))
							push( false )
						else
							push( beval(right) )
				}
			case NotExprAST( e ) => push( !beval(e) )
			case VariableExprAST( v ) => push( vars(v) )
			case c: CaseFunctionExprAST => push( new Closure(if (activations isEmpty) null else activations.top, c.cases) )
			case f: FunctionExprAST => push( new Closure(if (activations isEmpty) null else activations.top, List(f)) )
			case ApplyExprAST( f, args, tailrecursive ) =>
				apply( f )
				apply( args )

			val argList = list( args.length )

				pop match
				{
					case m: collection.Map[Any, Any] => push( m(argList.head) )
					case s: collection.Seq[_] => push( s(argList.head.asInstanceOf[Int]) )
					case s: collection.Set[Any] => push( s(argList.head) )
					case c: Closure =>
						def occur( argList: List[Any] )
						{
							def findPart: Option[FunctionPartExprAST] =
							{
								for (alt <- c.func)
									if (pattern( activations.top.scope.top, argList, alt.parms ))
									{
										for (part <- alt.parts)
											part.cond match
											{
												case None => return Some( part )
												case Some( cond ) =>
													if (beval( cond ))
														return Some( part )
											}

										activations.top.scope.top.clear
									}

								None
							}

							findPart match
							{
								case None => sys.error( "function match failure: " + c.func + " applied to " + argList )
								case Some( part ) =>
									part.locals match
									{
										case None =>
										case Some( l ) =>
											for (k <- l)
												activations.top.scope.top(k) = new Holder( null )
									}

									apply( part.body ) match
									{
										case a: List[Any] =>
											activations.top.scope.top.clear
											occur( a )
										case _ =>
									}
							}
						}

 						if (tailrecursive)
							argList
						else
						{
							enterEnvironment( c )
							occur( argList )
							exitEnvironment
						}
					case b: Function =>
						push( b(argList) )
					case Constructor( t, n, fields ) =>
						if (fields.length != argList.length) sys.error( "argument list length does not match data declaration" )

						push( new Record(t, n, fields, argList) )
					case ClassMethodCall( c, m ) =>
						m.find( _.getParameterTypes.length == argList.length ) match
						{
							case None => sys.error( "wrong number of arguments: " + argList )
							case Some( cm ) => push( cm.invoke( null, argList.asInstanceOf[List[Object]]: _* ) )
						}
					case ObjectMethodCall( o, m ) =>
						m.find( _.getParameterTypes.length == argList.length ) match
						{
							case None => sys.error( "wrong number of arguments: " + argList )
							case Some( om ) => push( om.invoke( o, argList.asInstanceOf[List[Object]]: _* ) )
						}
					case c: Class[Any] =>
						c.getConstructors.toList.filter( _.getParameterTypes.length == argList.length ) match
						{
							case List( con ) =>
								push( con.newInstance( argList.asInstanceOf[List[Object]]: _* ) )
							case Nil => sys.error( "no constructor for: " + argList )
							case con :: _ => push( con.newInstance( argList.asInstanceOf[List[Object]]: _* ) )
							case _ => sys.error( "multiple constructors for: " + argList )
						}
					case o => sys.error( "not a function: " + o )
				}
			case UnaryExprAST( op, exp ) =>
				apply( exp )
				
				op match
				{
					case '- => push( Math(op, pop) )
					case Symbol( "pre--" ) =>
						push( assignOperation(hpop, '-, 1) )
					case Symbol( "pre++" ) =>
						push( assignOperation(hpop, '+, 1) )
					case Symbol( "post--" ) =>
						val h = hpop
						val v = h.v
						
						h.v = Math( '-, h.v, 1 )
						push( v )
					case Symbol( "post++" ) =>
						val h = hpop
						val v = h.v
						
						h.v = Math( '+, h.v, 1 )
						push( v )
				}
			case AssignmentExprAST( lhs, op, rhs ) =>
				if (lhs.length != rhs.length)
					sys.error( "left hand side must have the same number of elements as the right hand side" )

				var result: Any = null
				
				for ((l, r) <- (lhs map (heval)) zip (rhs map (eval)))
				{
					if (op == '=)
					{
						l.v = r
						result = r
					}
					else
						result = assignOperation( l, Symbol(op.toString.charAt(1).toString), r.asInstanceOf[Number] )
				}
				
				push( result )
			case VectorExprAST( l ) =>
				apply( l )
				push( list(l.length).toIndexedSeq )
			case TupleExprAST( l, r ) =>
				push( (eval(l), eval(r)) )
			case ListExprAST( l ) =>
				apply( l )
				push( list(l.length) )
			case ConsExprAST( head, tail ) =>
				eval( tail ) match
				{
					case t: List[Any] => push( eval(head) :: t )
					case t => sys.error( "list object expected: " + t )
				}
			case SetExprAST( l ) =>
				apply( l )
				push( list(l.length).toSet )
			case MapExprAST( l ) =>
				apply( l )
				push( list(l.length).asInstanceOf[List[(_, _)]].toMap )//new HolderMap(list(l.length).asInstanceOf[List[(_, _)]]) )
			case UnitExprAST => push( () )
			case NullExprAST => push( null )
			case BlockExprAST( Nil ) => push( () )
			case BlockExprAST( l ) =>
				val it = l.iterator
				var tro: Any = ()
				
				enterScope
				
				while (it hasNext)
				{
				val s = it next
				
					if (it.hasNext || !s.isInstanceOf[ExpressionStatementAST])
						apply( s )
					else
						tro = apply( s.asInstanceOf[ExpressionStatementAST].e )
				}

				exitScope
				tro
			case ConditionalExprAST( cond, no ) =>
				cond find (i => beval(i._1)) match
				{
					case Some( (_, t) ) => apply( t )
					case None =>
						if (no == None)
							void
						else
							apply( no.get )
				}
			case ForExprAST( p, r, body, e ) =>
				val o = eval( r )

				if (o.isInstanceOf[TraversableOnce[Any]])
				{
//				val it = o.asInstanceOf[Iterable[Any]].iterator
				
					enterScope
	
//				val h = newVar( v )
				
					void

				val stacksize = stack.size

					try
					{
						o.asInstanceOf[TraversableOnce[Any]].foreach
						{ elem =>
							clear( activations.top.scope.top, p )

							if (!unify( activations.top.scope.top, deref(elem), p ))
								sys.error( "unification error in for loop" )

							pop

							try
							{
								apply( body )
							}
							catch
							{
								case _: ContinueThrowable =>
									stack reduceToSize stacksize - 1		// because of the pop inside the while loop
									void
							}
						}
						
// 						while (it hasNext)
// 						{
// 							clear( activations.top.scope.top, p )
// 
// 							if (!unify( activations.top.scope.top, deref(it.next), p ))
// 								sys.error( "unification error in for loop" )
// 
// 	//						activations.top.scope.top(v) = deref( it.next )
// 	//						h.v = deref( it.next )
// 							pop
// 						
// 							try
// 							{
// 								apply( body )
// 							}
// 							catch
// 							{
// 								case _: ContinueThrowable =>
// 									stack reduceToSize stacksize - 1		// because of the pop inside the while loop
// 									void
// 							}
// 						}

						if (e != None)
						{
							pop
							apply( e.get )
						}
					}
					catch
					{
						case _: BreakThrowable =>
							stack reduceToSize stacksize - 1		// because of the pop inside the while loop
							void
					}
					
					exitScope
				}
				else
					sys.error( "non iterable object: " + o )
			case WhileExprAST( cond, body, e ) =>
				void

				val stacksize = stack.size

					try
					{
						while (beval( cond ))
						{
							pop

							try
							{
								apply( body )
							}
							catch
							{
								case _: ContinueThrowable =>
									stack reduceToSize stacksize - 1		// because of the pop inside the while loop
									void
							}
						}

						if (e != None)
						{
							pop
							apply( e.get )
						}
					}
					catch
					{
						case _: BreakThrowable =>
							stack reduceToSize stacksize - 1		// because of the pop inside the while loop
							void
					}
			case DoWhileExprAST( body, cond, e ) =>
				void

				val stacksize = stack.size

				try
				{
					do
					{
						pop

						try
						{
							apply( body )
						}
						catch
						{
							case _: ContinueThrowable =>
								stack reduceToSize stacksize - 1		// because of the pop inside the while loop
								void
						}
					}
					while (beval( cond ))

					if (e != None)
					{
						pop
						apply( e.get )
					}
				}
				catch
				{
					case _: BreakThrowable =>
						stack reduceToSize stacksize - 1		// because of the pop inside the while loop
						void
				}
			case RepeatExprAST( body, cond, e ) =>
				void

				val stacksize = stack.size

					try
					{
						do
						{
							pop
							
							try
							{
								apply( body )
							}
							catch
							{
								case _: ContinueThrowable =>
									stack reduceToSize stacksize - 1		// because of the pop inside the while loop
									void
							}
						}
						while (!beval( cond ))

						if (e != None)
						{
							pop
							apply( e.get )
						}
					}
					catch
					{
						case _: BreakThrowable =>
							stack reduceToSize stacksize - 1		// because of the pop inside the while loop
							void
					}
			case RangeExprAST( f, t, b, inclusive ) =>
				eval( f ) match
				{
					case i: Int if inclusive => push( i to ieval(t) by (if (b == None) 1 else ieval(b.get)) )
					case d: Double if inclusive => push( d to deval(t) by (if (b == None) 1 else deval(b.get)) )
					case i: Int => push( i until ieval(t) by (if (b == None) 1 else ieval(b.get)) )
					case d: Double => push( d until deval(t) by (if (b == None) 1 else deval(b.get)) )
					case _ => sys.error( "expected a number as the initial value of a range" )
				}
			case DotExprAST( e, f ) =>
				eval( e ) match
				{
					case r: Record =>
						r.get( f ) match
						{
							case None => sys.error( "unknown field: " + f )
							case Some( v ) => push( v )
						}
					case m: Map[Any, Any] =>
						push( m(f.name) )
					case c: Class[Any] =>
						val methods = c.getMethods.toList.filter( m => m.getName == f.name && (m.getModifiers&Modifier.STATIC) == Modifier.STATIC )

						if (methods isEmpty)
						{
							c.getFields.find( cf => cf.getName == f.name && (cf.getModifiers&Modifier.STATIC) == Modifier.STATIC ) match
							{
								case None => sys.error( "static method or field not found: " + f.name )
								case Some( field ) => push( field.get(null) )
							}
						}
						else
							push( ClassMethodCall(c, methods) )
					case o =>
						val c = o.getClass

						val methods = c.getMethods.toList.filter( m => m.getName == f.name && (m.getModifiers&Modifier.STATIC) != Modifier.STATIC )
						
						if (methods isEmpty)
							sys.error( "object method not found: " + f.name )

						push( ObjectMethodCall(o, methods) )
				}
		}
	}
	
	def clear( map: SymbolMap, p: PatternAST )
	{
		p match
		{
			case AliasPatternAST( alias, pat ) =>
				map remove alias
				clear( map, pat )
			case VariablePatternAST( n, _ ) =>
				map remove n
			case TuplePatternAST( t ) =>
				for (e <- t)
					clear( map, e )
			case RecordPatternAST( n, l ) =>
				for (e <- l)
					clear( map, e )
			case ListPatternAST( f ) =>
				for (e <- f)
					clear( map, e )
			case ConsPatternAST( head, tail ) =>
				clear( map, head )
				clear( map, tail )
			case _ =>
		}
	}

	def typecheck( a: Any, t: Option[Symbol] ) =
		t match
		{
			case None => true
			case Some( 'String ) => a.isInstanceOf[String]
			case Some( 'Int ) => a.isInstanceOf[Int]
			case Some( 'List ) => a.isInstanceOf[List[_]]
			case Some( datatype ) => datatypes.contains( datatype ) && a.isInstanceOf[Record] && a.asInstanceOf[Record].datatype == datatype
			case _ => sys.error( "unknown type" )
		}
	
	def unify( map: SymbolMap, a: Any, p: PatternAST ): Boolean =
		p match
		{
			case IntegerLiteralPatternAST( i ) => a == i
			case DoubleLiteralPatternAST( d ) => a == d
			case BooleanLiteralPatternAST( b ) => a == b
			case StringLiteralPatternAST( s ) => a == s
			case UnitPatternAST => a == ()
			case NullPatternAST => a == null
			case AliasPatternAST( alias, pat ) =>
				if (map contains alias)
					a == map(alias) && unify( map, a, pat )
				else
				{
					if (unify( map, a, pat ))
					{
						map(alias) = a
						true
					}
					else
						false
				}
			case VariablePatternAST( '_, t ) => typecheck( a, t )
			case VariablePatternAST( n, t ) =>
				if (map contains n)
					a == map(n)
				else
				{
					if (typecheck( a, t ))
					{
						map(n) = a
						true
					}
					else
						false
				}
			case TuplePatternAST( t ) =>
				a match
				{
					case v: Vector[Any] => v.length == t.length && (v zip t).forall( pair => unify(map, pair._1, pair._2) )
					case _ => false
				}
			case RecordPatternAST( n, l ) =>
				a match
				{
					case r: Record => r.name == n && r.args.length == l.length && (r.args zip l).forall( pair => unify(map, pair._1, pair._2) )
					case _ => false
				}
			case ListPatternAST( f ) =>
				a match
				{
					case Nil => f == Nil
					case l: List[Any] if l.length == f.length =>
						(l take f.length zip f).forall( pair => unify(map, pair._1, pair._2) )
					case _ => false
				}
			case ConsPatternAST( head, tail ) =>
				a match
				{
					case Nil => false
					case l: List[Any] => unify( map, l.head, head ) && unify( map, l.tail, tail )
					case _ => false
				}
			case AltPatternAST( alts ) =>
				alts exists (unify( map, a, _ ))
		}

	def pattern( map: SymbolMap, args: List[Any], parms: List[PatternAST] ) =
	{
		def pattern( ah: Any, at: List[Any], ph: PatternAST, pt: List[PatternAST] ): Boolean =
		{
			if (unify( map, ah, ph ))
			{
				if (at == Nil && pt == Nil)
					true
				else if (at != Nil && pt != Nil)
					pattern( at.head, at.tail, pt.head, pt.tail )
				else
				{
					map.clear
					false
				}
			}
			else
			{
				map.clear
				false
			}
		}

		if (args == Nil && parms == Nil)
			true
		else if (args == Nil && parms != Nil)
			pattern( (), Nil, parms.head, parms.tail )
		else
			pattern( args.head, args.tail, parms.head, parms.tail )
	}

	def deref( v: Any ) =
		if (v.isInstanceOf[Holder])
			v.asInstanceOf[Holder].v
		else
			v
}

object Evaluator
{
	case class PARSE_FAILURE( message: String )
	
	def expr( e: String, where: (Symbol, Any)* ) =
	{
		Parser.parseExpression( new CharSequenceReader(e) ) match
		{
			case Parser.Success( l, _ ) =>
				val e = new Evaluator
				
					e.assign( where: _* )
					e.enterEnvironment( null )
					e.eval( l )
			case Parser.Failure( m, r ) => PARSE_FAILURE( m )
			case Parser.Error( m, r ) => PARSE_FAILURE( m )
		}
	}

	def statement( s: String ): Any =
	{
	val eval = new Evaluator

		eval.enterEnvironment( null )
		statement( s, eval )
	}

	def statement( s: String, eval: Evaluator ) =
		Parser.parseStatement( new CharSequenceReader(s) ) match
		{
			case Parser.Success( l, _ ) =>
//				println( l )
				eval.apply( l )
				eval.last
			case Parser.Failure( m, r ) => PARSE_FAILURE( m )
			case Parser.Error( m, r ) => PARSE_FAILURE( m )
		}
}