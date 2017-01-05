package fakeblobs;

import java.util.Random;

import ij.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.region.hypersphere.HyperSphere;
import net.imglib2.algorithm.region.hypersphere.HyperSphereCursor;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class Sphereblobs {

	public static void drawSpheres( RandomAccessibleInterval<FloatType> blobimage, final double minValue, final double maxValue){
		
		  int ndims = blobimage.numDimensions();
		  
	        // define the center and radius
	        Point center = new Point( ndims );
	        long minSize = blobimage.dimension( 0 );
	 
	        for ( int d = 0; d < ndims; ++d )
	        {
	            long size = blobimage.dimension( d );
	 
	            center.setPosition( size / 2 , d );
	            minSize = Math.min( minSize, size );
	        }
	 
	        int maxRadius = 20;
	 
	        
	 
	        // instantiate a random number generator
	        Random rnd = new Random( System.currentTimeMillis() );
	    	// define an interval that is span number of pixel smaller on each side
			// in each dimension
			int span = maxRadius;

			Interval interval = Intervals.expand(blobimage, -span);

			// create a view on the source with this interval
			blobimage = Views.interval(blobimage, interval);
	     Cursor<FloatType> cursor = Views.iterable(blobimage).localizingCursor();
	 
	        while ( cursor.hasNext() )
	        {
	            cursor.fwd();
	 
	            // the random radius of the current small hypersphere
	            int radius = rnd.nextInt( maxRadius ) + 1;
	 
	            // instantiate a small hypersphere at the location of the current pixel
	            // in the large hypersphere
	            HyperSphere< FloatType > smallSphere =
	                new HyperSphere< FloatType >( blobimage, cursor, radius );
	 
	            // define the random intensity for this small sphere
	            double randomValue = rnd.nextDouble();
	 
	            cursor.jumpFwd(maxRadius);
	            // take only every 4^dimension'th pixel by chance so that it is not too crowded
	            if ( Math.round( randomValue * 100 ) % Math.pow( 100, ndims ) == 0 )
	            {
	            	
	            
	                // scale to right range
	                randomValue = rnd.nextDouble() * ( maxValue - minValue ) + minValue;
	 
	                // set the value to all pixels in the small sphere if the intensity is
	                // brighter than the existing one
	                for ( final FloatType value : smallSphere )
	                    value.setReal( Math.max( randomValue, value.getRealDouble() ) );
	            }
	        }
	    }

public static void main(String[] args){
	
	final FinalInterval range = new FinalInterval(512, 512);
	new ImageJ();
	
	RandomAccessibleInterval<FloatType> blobimage = new ArrayImgFactory<FloatType>().create(range, new FloatType());
	
	final int ndims = blobimage.numDimensions();
	
	final double minValue = 15;
	
	final double maxValue = 25;
	
	drawSpheres(blobimage, minValue, maxValue);
	
	ImageJFunctions.show(blobimage);
	
}




}
	
	

