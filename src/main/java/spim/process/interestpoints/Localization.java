package spim.process.interestpoints;

import java.util.ArrayList;
import java.util.Date;

import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.algorithm.scalespace.SubpixelLocalization;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussian.SpecialPoint;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.Point;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.segmentation.SimplePeak;

public class Localization
{
	public static ArrayList< Point > noLocalization( final ArrayList< SimplePeak > peaks, final boolean findMin, final boolean findMax )
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): NO subpixel localization" );					

		final int n = peaks.get( 0 ).location.length;
		final ArrayList< Point > peaks2 = new ArrayList< Point >();
		
        for ( final SimplePeak peak : peaks )
        {
        	if ( ( peak.isMax && findMax ) || ( peak.isMin && findMin ) )
        	{
	        	final float[] pos = new float[ n ];
	        	
	        	for ( int d = 0; d < n; ++d )
	        		pos[ d ] = peak.location[ d ];
	        	
	    		peaks2.add( new Point( pos ) );
        	}
        }
        
        return peaks2;		
	}

	public static ArrayList< Point > computeQuadraticLocalization( final ArrayList< SimplePeak > peaks, final Image< FloatType > domImg, final boolean findMin, final boolean findMax )
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Subpixel localization using quadratic n-dimensional fit");					

        final ArrayList< DifferenceOfGaussianPeak<FloatType> > peakList = new ArrayList<DifferenceOfGaussianPeak<FloatType>>();

        for ( final SimplePeak peak : peaks )
        	if ( ( peak.isMax && findMax ) || ( peak.isMin && findMin ) )
        		peakList.add( new DifferenceOfGaussianPeak<FloatType>( peak.location, new FloatType( peak.intensity ), SpecialPoint.MAX ) );
		
        final SubpixelLocalization<FloatType> spl = new SubpixelLocalization<FloatType>( domImg, peakList );
		spl.setAllowMaximaTolerance( true );
		spl.setMaxNumMoves( 10 );
		
		if ( !spl.checkInput() || !spl.process() )
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Warning! Failed to compute subpixel localization " + spl.getErrorMessage() );
		
		final float[] pos = new float[ domImg.getNumDimensions() ];
		
		final ArrayList< Point > peaks2 = new ArrayList< Point >();
		
        for ( DifferenceOfGaussianPeak<FloatType> detection : peakList )
        {
    		detection.getSubPixelPosition( pos );
    		peaks2.add( new Point( pos.clone() ) );
        }
        
        return peaks2;
	}
	
	public static ArrayList< Point > computeGaussLocalization( final ArrayList< SimplePeak > peaks, final Image< FloatType > domImg, final double sigma, final boolean findMin, final boolean findMax )
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Subpixel localization using Gaussian Mask Localization");					

		// TODO: implement gauss fit
		throw new RuntimeException( "Gauss fit not implemented yet" );
	}	
}