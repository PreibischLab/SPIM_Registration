package fakeblobs;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class AddGaussian {


	final public static void addGaussian( final RandomAccessibleInterval< FloatType > image, final double[] location, final double[] sigma)
	{
	final int numDimensions = image.numDimensions();
	final int[] size = new int[ numDimensions ];

	final long[] min = new long[ numDimensions ];
	final long[] max = new long[ numDimensions ];

	final double[] two_sq_sigma = new double[ numDimensions ];

	for ( int d = 0; d < numDimensions; ++d )
	{
	size[ d ] = getSuggestedKernelDiameter( sigma[ d ] ) * 2;
	min[ d ] = (int)Math.round( location[ d ] ) - size[ d ]/2;
	max[ d ] = min[ d ] + size[ d ] - 1;
	two_sq_sigma[ d ] = 2 * sigma[ d ] * sigma[ d ];
	}

	final RandomAccessible< FloatType > infinite = Views.extendZero( image );
	final RandomAccessibleInterval< FloatType > interval = Views.interval( infinite, min, max );
	final IterableInterval< FloatType > iterable = Views.iterable( interval );
	final Cursor< FloatType > cursor = iterable.localizingCursor();
	while ( cursor.hasNext() )
	{
	cursor.fwd();

	double value = 1;

	for ( int d = 0; d < numDimensions; ++d )
	{
	final double x = location[ d ] - cursor.getIntPosition( d );
	value *= Math.exp( -(x * x) / two_sq_sigma[ d ] );
	}
	
	
	
	cursor.get().set( cursor.get().get() + (float)value );
	
	
	}
	
	
	
	
	}
	
	
	
	
	
	
	final public static void addGaussian( final RandomAccessibleInterval< FloatType > image, final double Amplitude,
			final double[] location, final double[] sigma)
	{
	final int numDimensions = image.numDimensions();
	final int[] size = new int[ numDimensions ];

	final long[] min = new long[ numDimensions ];
	final long[] max = new long[ numDimensions ];
	for ( int d = 0; d < numDimensions; ++d )
	{
	size[ d ] = getSuggestedKernelDiameter( sigma[ d ] ) * 2;
	min[ d ] = (int)Math.round( location[ d ] ) - size[ d ]/2;
	max[ d ] = min[ d ] + size[ d ] - 1;
	}

	final RandomAccessible< FloatType > infinite = Views.extendZero( image );
	final RandomAccessibleInterval< FloatType > interval = Views.interval( infinite, min, max );
	final IterableInterval< FloatType > iterable = Views.iterable( interval );
	final Cursor< FloatType > cursor = iterable.localizingCursor();
	
	while ( cursor.hasNext() )
	{
	cursor.fwd();

	double value = Amplitude;

	for ( int d = 0; d < numDimensions; ++d )
	{
	final double x = location[ d ] - cursor.getIntPosition( d );
	value *= Math.exp( -(x * x) / (sigma[ d ] *sigma[ d ] ) );
	}
	
	
	cursor.get().set( cursor.get().get() + (float)value );
	
	
	}
	
	}
	final public static void add2DGaussian( final RandomAccessibleInterval< FloatType > image, final double Amplitude,
			final double[] location, final double[] sigma, final double corr)
	{
	
		final int numDimensions = image.numDimensions();
		final int[] size = new int[ numDimensions ];

		final long[] min = new long[ numDimensions ];
		final long[] max = new long[ numDimensions ];
		for ( int d = 0; d < numDimensions; ++d )
		{
		size[ d ] = getSuggestedKernelDiameter( sigma[ d ] ) * 2;
		min[ d ] = (int)Math.round( location[ d ] ) - size[ d ]/2;
		max[ d ] = min[ d ] + size[ d ] - 1;
		}

		final RandomAccessible< FloatType > infinite = Views.extendZero( image );
		final RandomAccessibleInterval< FloatType > interval = Views.interval( infinite, min, max );
		final IterableInterval< FloatType > iterable = Views.iterable( interval );
		final Cursor< FloatType > cursor = iterable.localizingCursor();
	
	while ( cursor.hasNext() )
	{
	cursor.fwd();

	double value = Amplitude;
	final double x = location[0] - cursor.getIntPosition(0);
	final double y = location[1] - cursor.getIntPosition(1);
	value *=Math.exp( -(x * x) / (sigma[ 0 ] *sigma[ 0 ] ) - (y * y) / (sigma[ 1 ] *sigma[ 1 ] ) 
			+ (corr * x * y) /(sigma[ 0 ] * sigma[ 1 ]) )  ;
	
	cursor.get().set( cursor.get().get() +  (float)value );
	
	
	}
	
	}

	public static int getSuggestedKernelDiameter( final double sigma )
	{
	int size = 3;
    int cutoff = 5; // This number means cutoff is chosen to be cutoff times sigma. 
    if ( sigma > 0 )
	size = Math.max( cutoff, ( 2 * ( int ) ( cutoff * sigma + 0.5 ) + 1 ) );

	return size;
	}
	
	
	
	
}
