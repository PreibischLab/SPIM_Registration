package overlaytrack;

import java.awt.Color;
import java.util.ArrayList;

import org.jgrapht.graph.DefaultWeightedEdge;

import blobObjects.Subgraphs;
import fiji.tool.SliceListener;
import fiji.tool.SliceObserver;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.io.FileSaver;
import overlaytrack.DisplayGraph.ImagePlusListener;
import mpicbg.spim.segmentation.SnakeObject;

public class DisplaysubGraph {
	
	private final ImagePlus imp;
	private final ArrayList<Subgraphs> subgraph;
    private final int ndims;
	
	public DisplaysubGraph(final ImagePlus imp, final ArrayList<Subgraphs> subgraph){
		
		this.imp = imp;
		this.subgraph = subgraph;
		ndims = imp.getNDimensions();
		

		// add listener to the imageplus slice slider
				SliceObserver sliceObserver = new SliceObserver( imp, new ImagePlusListener() );
	}
	
public ImagePlus getImp() { return this.imp; } 
	
	
	protected  class ImagePlusListener implements SliceListener
	{
		@Override
		public void sliceChanged(ImagePlus arg0)
		{
			
			
			int maxSlice = subgraph.get(subgraph.size() - 1).Currentframe + 1;
			
			imp.show();
			
			
			Overlay o = imp.getOverlay();
			
			if( getImp().getOverlay() == null )
			{
				o = new Overlay();
				getImp().setOverlay( o ); 
			}

			o.clear();
			
			int currentSlice = getImp().getCurrentSlice();
			for (int index = 0; index < subgraph.size(); ++index){
			
				if (currentSlice == subgraph.get(index).Previousframe + 1 && currentSlice < maxSlice){
					for (DefaultWeightedEdge e : subgraph.get(index).subgraph.edgeSet()){
						 SnakeObject Spotbase = subgraph.get(index).subgraph.getEdgeSource(e);
					        SnakeObject Spottarget = subgraph.get(index).subgraph.getEdgeTarget(e);
					        
					        
					        
					        final double[] startedge = new double[ndims];
					        final double[] targetedge = new double[ndims];
					        for (int d = 0; d < ndims - 1; ++d){
					        	
					        	startedge[d] = Spotbase.centreofMass[d];
					        	
					        	targetedge[d] = Spottarget.centreofMass[d];
					        	
					        }
					        
					       
					        Line newline = new Line(startedge[0], startedge[1], targetedge[0], targetedge[1]);
							newline.setStrokeColor(Color.GREEN);
							newline.setStrokeWidth(subgraph.get(index).subgraph.degreeOf(Spottarget));

							o.add(newline);
							
						
					}
				}
				
				imp.updateAndDraw();
			}
			
			
			final FileSaver savestart = new FileSaver(imp);
			savestart.saveAsJpeg("mCherry_subgraph"+arg0.getCurrentSlice());
			
			System.out.println( arg0.getCurrentSlice() );
		}		
	}
	
}
