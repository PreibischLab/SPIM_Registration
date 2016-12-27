package mpicbg.spim.segmentation;

import ij.gui.Roi;

public class SnakeObject {

	
	public final int Label;
	public final Roi roi;
	public final double[] centreofMass;
	public final double Intensity;
	
	public SnakeObject( final int Label, final Roi roi, final double[] centreofMass, final double Intensity){
		
		this.Label = Label;
		this.roi =  roi;
		this.centreofMass = centreofMass;
		this.Intensity = Intensity;
		
	}
	
	
	
}
