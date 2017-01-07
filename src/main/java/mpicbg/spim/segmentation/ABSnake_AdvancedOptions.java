package mpicbg.spim.segmentation;

	import ij.*;
	import ij.plugin.*;
	import ij.gui.*;

	public class ABSnake_AdvancedOptions implements PlugIn {

	    @Override
	    public void run(String arg) {
	        // dialog
	    	 // dialog
	        GenericDialog gd = new GenericDialog("Snake Advanced");
	        gd.addNumericField("Distance_Search", 100, 0);
	        gd.addNumericField("Displacement_min",  0.1, 2);
	        gd.addNumericField("Displacement_max", 5.0 , 2);
	        gd.addNumericField("Threshold_dist_positive", 100, 0);
	        gd.addNumericField("Threshold_dist_negative", 100, 0);
	        gd.addNumericField("Inv_alpha_min",  0.1, 2);
	        gd.addNumericField("Inv_alpha_max", 10.0, 2);
	        gd.addNumericField("Reg_min", 1, 2);
	        gd.addNumericField("Reg_max",  2, 2);
	        gd.addNumericField("Mul_factor", 0.99, 4);
	        // show dialog
	        gd.showDialog();
	        Prefs.set("ABSnake_DistSearch.int", (int) gd.getNextNumber());
	        Prefs.set("ABSnake_DisplMin.double", gd.getNextNumber());
	        Prefs.set("ABSnake_DisplMax.double", gd.getNextNumber());
	        Prefs.set("ABSnake_ThreshDistPos.double", gd.getNextNumber());
	        Prefs.set("ABSnake_ThreshDistNeg.double", gd.getNextNumber());
	        Prefs.set("ABSnake_InvAlphaMin.double", gd.getNextNumber());
	        Prefs.set("ABSnake_InvAlphaMax.double", gd.getNextNumber());
	        Prefs.set("ABSnake_RegMin.double", gd.getNextNumber());
	        Prefs.set("ABSnake_RegMax.double", gd.getNextNumber());
	        Prefs.set("ABSnake_MulFactor.double", gd.getNextNumber());
	       
	    }
	}

