def
	length( [] )							= 0
	length( _:t )						= 1 + length( t )
	
	foldl( f, z, [] )				= z
	foldl( f, z, x:xs )			= foldl( f, f(z, x), xs )

	foldl1( f, x:xs )				= foldl( f, x, xs )
	foldl1( _, [] )					= error( "foldl1: empty list" )

	foldr( f, z, [] )				= z
	foldr( f, z, x:xs )			= f( x, foldr(f, z, xs) )

	foldr1( f, [x] )					= x
	foldr1( f, x:xs )				= f( x, foldr1(f, xs) )
	foldr1( _, [] )					= error( "foldr1: empty list" )
	
	sum( l )									= foldl( (a, b) -> a + b, 0, l )
	product( l )							= foldl( (a, b) -> a * b, 1, l )
	
	map( f, [] )							= []
	map( f, x:xs )						= f( x ) : map( f, xs )
	
	splitAt( n, xs )					= (take( n, xs ), drop( n, xs ))
	
	take( _, [] )						= []
	take( n, _ ) | n <= 0		= []
	take( n, x:xs )					= x : take( n - 1, xs )
	
	drop( n, xs ) | n <= 0		= xs
	drop( _, [] )						= []
	drop( n, _:xs )					= drop( n - 1, xs )
	
	concat( [], ys )					= ys
	concat( x:xs, ys )				= x : concat( xs, ys )
	
	head( x:_ )							= x
	head( [] )								= error( "head: empty list" )

	tail( _:xs )							= xs
	tail( [] )								= error( "tail: empty list" )

	last( [x] )							= x
	last( _:xs )							= last( xs )
	last( [] )								= error( "last: empty list" )
	
	filter( p, [] )					= []
	filter( p, x:xs	)
		| p( x )								= x : filter( p, xs )
		| otherwise						= filter( p, xs )

main
	println( sum([1, 2, 3]) )
	println( foldl1((x, y) -> x^y, [7, 2, 5]), (7^2)^5 )
	println( foldr1((x, y) -> x^y, [7, 2, 5]), 7^(2^5) )
	println( length([1, 2, 3]) )
	println( take(2, [1, 2, 3]) )
	println( map(a -> 2*a, [1, 2, 3]) )
	println( concat([1, 2, 3], [4, 5, 6]) )
	println( last([1, 2, 3, 4]) )
	println( filter(x -> x < 50, [27, 95, 35, 65, 26, 37, 32, 68, 64, 34, 5, 48, 22, 78, 86, 10, 47, 24, 54, 2]) )