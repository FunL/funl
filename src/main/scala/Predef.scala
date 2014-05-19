/*     ______            __                                      *\
**    / ____/_  __ ___  / /     FunL Programming Language        **
**   / __/ / / / / __ \/ /      (c) 2014, Edward A. Maxedon, Sr. **
**  / /   / /_/ / / / / /__     http://funl-lang.org/            **
** /_/    \____/_/ /_/____/                                      **
\*                                                               */

package funl

import collection.mutable.{HashSet, HashMap, ArrayBuffer}
import util.Random.{nextInt, nextDouble}

import interp.Interpreter._


object Predef
{
	def println( a: List[Any] ) = Console.println( a map (display(_)) mkString(", ") )
	
	def print( a: List[Any] ) = Console.print( a map (display(_)) mkString(", ") )

	def printf( a: List[Any] ) = Console.printf( a.head.asInstanceOf[String], a.tail: _* )

	def error( a: List[Any] ) {error( a.head.toString )}

	def error( msg: String )
	{
		Console.err.println( msg )
		sys.exit( 1 )
	}
	
	def require( a: List[Any] ) =
		if (!a.head.asInstanceOf[Boolean])
			error( a.last.toString )

	def list( a: List[Any] ) =
		a match
		{
			case Nil => new ArrayBuffer[Any]
			case List( n: Int ) => ArrayBuffer.fill[Any]( n )( null )
			case List( init: List[Any] ) => ArrayBuffer[Any]( init: _* )
		}

	def set( a: List[Any] ) =
		if (a isEmpty)
			new HashSet[Any]
		else if (a.head.isInstanceOf[collection.Set[Any]])
			HashSet( a.head.asInstanceOf[collection.Set[Any]].toArray: _* )
		else
			HashSet( a: _* )
	
	def dict( a: List[Any] ) = 
		if (a isEmpty)
			new HashMap[Any, Any]
		else
			HashMap( a.head.asInstanceOf[collection.Map[Any, Any]].toArray: _* )

	def tuple( a: List[Any] ) =
		a match
		{
			case Nil => ()
			case List( c: Iterable[_] ) => c.toVector
			case List( a: Array[_] ) => a.toVector
		}
		
	def rnd( a: List[Any] ): Any =
		a match
		{
			case Nil => nextDouble
			case List( n: Int ) => nextInt( n )
			case List( l: Int, u: Int ) if l <= u => nextInt( u - l ) + l
			case List( r: collection.immutable.Range ) => nextInt( r.last + 1 - r.start ) + r.start
		}
}