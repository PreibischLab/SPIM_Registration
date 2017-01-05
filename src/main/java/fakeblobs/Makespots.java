package fakeblobs;

import java.util.Random;

import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;

public class Makespots {
	public static void Createspots(final RandomAccessibleInterval<FloatType> blobimage, final double[] sigma,  final int rate,
			final Interval range,final int numblobs){
		
		final int ndims = blobimage.numDimensions();
		final Random rnd = new Random(20);
		final Random velocity = new Random(-10);
		final Random rndsigma = new Random(blobimage.dimension(0));
		final double[] spotlocation = new double[ndims];
		final double[] newsigma = {rndsigma.nextInt((int) sigma[0]  ) + 1,rndsigma.nextInt((int) sigma[1]  ) + 1 };
		for (int i = 0; i < numblobs; ++i){
			
		for (int d = 0; d < ndims; ++d){
			
			spotlocation[d] = rnd.nextDouble() * (range.max(d) - range.min(d)) + range.min(d) + 10 *Math.sin(rate *velocity.nextDouble());
			
		}
		
		
		double spotIntensity = rnd.nextDouble();
		spotIntensity*= 2 + Math.sin(rate*velocity.nextDouble());
		AddGaussian.addGaussian(blobimage, spotIntensity,  spotlocation, newsigma);
		
		}
	}
}
