package fakeblobs;

import java.util.Random;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class Addnoise {
public static void SaltandPepperNoise(RandomAccessibleInterval<FloatType> inputimg){
		
		final  int saltandpepperlevel = 1;
		final Random rnd = new Random(saltandpepperlevel);
		
		Cursor<FloatType> cursor = Views.iterable(inputimg).localizingCursor();
		RandomAccess<FloatType> ranac = inputimg.randomAccess();
		long [] x = new long[inputimg.numDimensions()];
		
		while (cursor.hasNext()){
			
			cursor.fwd();
			
			for (int d = 0; d < inputimg.numDimensions(); ++d)
			x[d] = (long) (cursor.getDoublePosition(d));
			
			ranac.setPosition(x);
			ranac.get().setReal(0.2*rnd.nextDouble());
			
			
		}
		
	}
}
