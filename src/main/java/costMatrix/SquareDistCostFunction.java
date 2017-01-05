package costMatrix;

import mpicbg.spim.segmentation.SnakeObject;

/**
 * Implementation of various cost functions
 * 
 * 
 */

// Cost function base don minimizing the squared distances

public class SquareDistCostFunction implements CostFunction< SnakeObject, SnakeObject >
{

	@Override
	public double linkingCost( final SnakeObject source, final SnakeObject target )
	{
		return source.squareDistanceTo(target );
	}
	
	
	
	

}
