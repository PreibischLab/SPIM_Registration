package mpicbg.spim.segmentation;

import ij.gui.Roi;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import trackerType.BlobTracker;

public class SnakeObject implements RealLocalizable, Comparable< SnakeObject > {

	
	/**
	 * @param Framenumber
	 *                the current frame
	 * 
	 * @param Label
	 *                the label of the blob 
	 * @param centreofMass 
	 *                the co-ordinates for center of mass
	 *                of the current blobs
	 * @param Intensity
	 *                the total intensity of the blob in luciferas.
	 * 
	 */
	public final int Framenumber;
	public final int Label;
	public final Roi roi;
	public final double[] centreofMass;
	public final double Intensity;
	
	// Parameter for the cost function to decide how much weight to give to Intensity and to distance
	/*
	* CONSTRUCTORS
	*/

	/**
	 * Creates a new SnakeObject.
	 *  @param Framenumber
	 *                the current frame
	 * 
	 * @param Label
	 *                the label of the blob 
	 *                
	 * @param centreofMass 
	 *                the co-ordinates for center of mass
	 *                of the current blobs
	 * @param Intensity
	 *                the total intensity of the blob in luciferas.
	 * 
	 */
	
	
	public SnakeObject(final int Framenumber, final int Label, final Roi roi, final double[] centreofMass, final double Intensity){
		
		this.Framenumber = Framenumber;
		this.Label = Label;
		this.roi =  roi;
		this.centreofMass = centreofMass;
		this.Intensity = Intensity;
		
	}
	
	

	/**
	 * Returns the squared distance between two blobs.
	 *
	 * @param target
	 *            the Blob to compare to.
	 *
	 * @return the distance to the current blob to target blob specified.
	 */
	
	public double squareDistanceTo(SnakeObject target) {
		// Returns squared distance between the source Blob and the target Blob.
		
		final double[] sourceLocation = centreofMass;
		final double[] targetLocation = target.centreofMass;
		
		double distance = 0;
		
		for (int d = 0; d < sourceLocation.length; ++d){
			
			distance += (sourceLocation[d] - targetLocation[d]) * (sourceLocation[d] - targetLocation[d]);
		}
		
		
		return distance;
	}
	private static final class ComparableRealPoint extends RealPoint implements Comparable<ComparableRealPoint> {
		public ComparableRealPoint(final double[] A) {
			// Wrap array.
			super(A, false);
		}

		/**
		 * Sort based on X, Y
		 */
		@Override
		public int compareTo(final ComparableRealPoint o) {
			int i = 0;
			while (i < n) {
				if (getDoublePosition(i) != o.getDoublePosition(i)) {
					return (int) Math.signum(getDoublePosition(i) - o.getDoublePosition(i));
				}
				i++;
			}
			return hashCode() - o.hashCode();
		}
	}
	/**
	 * Returns the Intnesity weighted squared distance between two blobs.
	 *
	 * @param target
	 *            the Blob to compare to.
	 *
	 * @return the Intensity weighted distance to the current blob to target blob specified.
	 */
	
	public double IntensityweightedsquareDistanceTo(SnakeObject target) {
		// Returns squared distance between the source Blob and the target Blob.
		
		final double[] sourceLocation = centreofMass;
		final double[] targetLocation = target.centreofMass;
		
		double distance = 0;
		
		for (int d = 0; d < sourceLocation.length; ++d){
			
			distance += (sourceLocation[d]  - targetLocation[d])  * (sourceLocation[d]  - targetLocation[d] );
		}
		
		double IntensityweightedDistance =  (distance )* Math.pow((Intensity / target.Intensity), 2) ;
		
		return IntensityweightedDistance;
	}
	/**
	 * Returns the difference between the location of two blobs, this operation
	 * returns (
	 * <code>A.diffTo(B) = - B.diffTo(A)</code>)
	 *
	 * @param target
	 *            the Blob to compare to.
	 * @param int n
	 *            n = 0 for X- coordinate, n = 1 for Y- coordinate
	 * @return the difference in co-ordinate specified.
	 */
	public double diffTo( final SnakeObject target, int n )
	{
		
		final double thisBloblocation = centreofMass[n];
		final double targetBloblocation = target.centreofMass[n];
		return thisBloblocation - targetBloblocation;
	}
	
	/**
	 * Returns the difference between the Intensity of two blobs, this operation
	 * returns (
	 * <code>A.diffTo(B) = - B.diffTo(A)</code>)
	 *
	 * @param target
	 *            the Blob to compare to.
	 * 
	 * @return the difference in Intensity of Blobs.
	 */
	public double diffTo( final SnakeObject target)
	{
		final double thisBloblocation = Intensity;
		final double targetBloblocation = target.Intensity;
		return thisBloblocation - targetBloblocation;
	}
	
	
	


	@Override
	public int compareTo(SnakeObject o) {
		
		return hashCode() - o.hashCode();
	}


	@Override
	public void localize(float[] position) {
		int n = position.length;
		for ( int d = 0; d < n; ++d )
			position[ d ] = getFloatPosition( d );
		
	}


	@Override
	public void localize(double[] position) {
		int n = position.length;
		for ( int d = 0; d < n; ++d )
			position[ d ] = getDoublePosition( d );			
	}


	@Override
	public float getFloatPosition(int d) {
		return ( float ) getDoublePosition( d );
	}


	@Override
	public double getDoublePosition(int d) {
		return getDoublePosition( d );
	}


	@Override
	public int numDimensions() {
		
		return centreofMass.length;
	}
	
	
	
}
