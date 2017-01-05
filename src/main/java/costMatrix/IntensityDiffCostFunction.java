package costMatrix;

import mpicbg.spim.segmentation.SnakeObject;;

public class IntensityDiffCostFunction implements CostFunction< SnakeObject, SnakeObject >
	{

		
	

	@Override
	public double linkingCost( final SnakeObject source, final SnakeObject target )
	{
		return source.IntensityweightedsquareDistanceTo(target );
	}
		

	
}
