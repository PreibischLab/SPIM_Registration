package graphconstructs;



import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.imglib2.algorithm.Benchmark;
import net.imglib2.algorithm.OutputAlgorithm;
import mpicbg.spim.segmentation.SnakeObject;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

public class FromContinuousBranches implements OutputAlgorithm< SimpleWeightedGraph< SnakeObject, DefaultWeightedEdge > >, Benchmark
{

	private static final String BASE_ERROR_MSG = "[FromContinuousBranches] ";

	private long processingTime;

	private final Collection< List< SnakeObject >> branches;

	private final Collection< List< SnakeObject >> links;

	private String errorMessage;

	private SimpleWeightedGraph< SnakeObject, DefaultWeightedEdge > graph;

	public FromContinuousBranches( final Collection< List< SnakeObject >> branches, final Collection< List< SnakeObject >> links )
	{
		this.branches = branches;
		this.links = links;
	}

	@Override
	public long getProcessingTime()
	{
		return processingTime;
	}

	@Override
	public boolean checkInput()
	{
		final long start = System.currentTimeMillis();
		if ( null == branches )
		{
			errorMessage = BASE_ERROR_MSG + "branches are null.";
			return false;
		}
		if ( null == links )
		{
			errorMessage = BASE_ERROR_MSG + "links are null.";
			return false;
		}
		for ( final List< SnakeObject > link : links )
		{
			if ( link.size() != 2 )
			{
				errorMessage = BASE_ERROR_MSG + "A link is not made of two SnakeObjects.";
				return false;
			}
			if ( !checkIfInBranches( link.get( 0 ) ) )
			{
				errorMessage = BASE_ERROR_MSG + "A SnakeObject in a link is not present in the branch collection: " + link.get( 0 ) + " in the link " + link.get( 0 ) + "-" + link.get( 1 ) + ".";
				return false;
			}
			if ( !checkIfInBranches( link.get( 1 ) ) )
			{
				errorMessage = BASE_ERROR_MSG + "A SnakeObject in a link is not present in the branch collection: " + link.get( 1 ) + " in the link " + link.get( 0 ) + "-" + link.get( 1 ) + ".";
				return false;
			}
		}
		final long end = System.currentTimeMillis();
		processingTime = end - start;
		return true;
	}

	@Override
	public boolean process()
	{
		final long start = System.currentTimeMillis();

		graph = new SimpleWeightedGraph< SnakeObject, DefaultWeightedEdge >( DefaultWeightedEdge.class );
		for ( final List< SnakeObject > branch : branches )
		{
			for ( final SnakeObject SnakeObject : branch )
			{
				graph.addVertex( SnakeObject );
			}
		}

		for ( final List< SnakeObject > branch : branches )
		{
			final Iterator< SnakeObject > it = branch.iterator();
			SnakeObject previous = it.next();
			while ( it.hasNext() )
			{
				final SnakeObject SnakeObject = it.next();
				graph.addEdge( previous, SnakeObject );
				previous = SnakeObject;
			}
		}

		for ( final List< SnakeObject > link : links )
		{
			graph.addEdge( link.get( 0 ), link.get( 1 ) );
		}

		final long end = System.currentTimeMillis();
		processingTime = end - start;
		return true;
	}

	@Override
	public String getErrorMessage()
	{
		return errorMessage;
	}

	@Override
	public SimpleWeightedGraph< SnakeObject, DefaultWeightedEdge > getResult()
	{
		return graph;
	}

	private final boolean checkIfInBranches( final SnakeObject SnakeObject )
	{
		for ( final List< SnakeObject > branch : branches )
		{
			if ( branch.contains( SnakeObject ) ) { return true; }
		}
		return false;
	}

}
