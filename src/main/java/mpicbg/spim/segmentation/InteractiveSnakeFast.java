
package mpicbg.spim.segmentation;

/*
 *  Active Contours ( "Snakes")
 *  Initiali1ation by a rectangle, oval or closed area
 *  Deformation of the snake towards the nearest edges along the
 *  normals, edges computed by a deriche filter and above the threshold
 *  value.
 *  Regulari1ation of the shape by constraing the points to form a
 *  smooth curve (depending on the value of regulari1ation)
 *  Works in 3D by propagating the snake found in slice i
 *  to slice i+1
 */
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.*;
import ij.process.*;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.Normalize;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.RealSum;
import net.imglib2.view.Views;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

/**
 * ABSnake_ plugin interface
 *
 * @author thomas.boudier@snv.jussieu.fr and Philippe Andrey
 * @created 26 aout 2003
 * @modified Dec 2015 by Varun Kapoor
 */
public class InteractiveSnakeFast implements PlugInFilter {
	// Sauvegarde de la fenetre d'image :

	ImagePlus imp;
	ImagePlus Intensityimp;

	RandomAccessibleInterval<FloatType> originalimage;
	RandomAccessibleInterval<FloatType> originalIntensityimage;

	ImageStack pile = null;
	ImageStack Intensitypile = null;

	RandomAccessibleInterval<FloatType> resultoriginalimage;

	ImageStack pile_resultat = null;
	ImageStack Intensitypile_resultat = null;

	RandomAccessibleInterval<FloatType> currentimg;
	ImageStack pile_seg = null;

	int currentSlice = -1;
	// Dimensions of the stck :
	int slicesize = 0;
	int length = 0;
	int height = 0;
	private int ndims;
	// ROI original
	int nbRois;
	Roi rorig = null;
	Roi processRoi = null;
	Color colorDraw = Color.RED;
	ArrayList<SnakeObject> snakeList;

	int channel;
	int Frame;

	public InteractiveSnakeFast(RandomAccessibleInterval<FloatType> originalimage, RandomAccessibleInterval<FloatType> originalIntensityimage, final int Frame) {

		this.originalimage = originalimage;
		this.originalIntensityimage = originalIntensityimage;
		this.Frame = Frame;

		imp = ImageJFunctions.wrap(originalimage, "curr");
		Intensityimp = ImageJFunctions.wrap(originalIntensityimage, "curr");
		ndims = originalimage.numDimensions();
	}

	public InteractiveSnakeFast(RandomAccessibleInterval<FloatType> originalimage, RandomAccessibleInterval<FloatType> originalIntensityimage, final int channel, final int Frame) {

		
		

		this.originalimage = originalimage;
		this.originalIntensityimage = originalIntensityimage;
		this.Frame = Frame;

		imp = ImageJFunctions.wrap(originalimage, "curr");
		Intensityimp = ImageJFunctions.wrap(originalIntensityimage, "curr");
		
		this.channel = channel;
	}
	
	

	Overlay overlay;
	
	

	/**
	 * Parametres of Snake :
	 */
	SnakeConfigDriver configDriver;
	// number of iterations
	int ite = 200;
	// step to display snake
	int step = ite - 1;
	// threshold of edges
	int Gradthresh = 2;
	// how far to look for edges
	int DistMAX = 100;

	double Displacement_min = 0.1;
	double Displacement_max = 2.0;
	double Threshold_dist_positive = 100;
	double Threshold_dist_negative = 100;
	double Inv_alpha_min = 0.2;
	double Inv_alpha_max = 10.0;
	double Reg_min = 1;
	double Reg_max = 5;
	double Mul_factor = 0.99;

	// maximum displacement
	double force = 10;
	// regulari1ation factors, min and max
	double reg = 5;
	double regmin, regmax;
	// first and last slice to process
	// misc options
	boolean showgrad = false;
	boolean createsegimage = false;
	boolean advanced = false;
	boolean propagate = true;
	boolean Auto = false;
	boolean saverois = false;
	boolean saveIntensity = true;
	boolean useroinames = false;
	boolean nosi1elessrois = true;
	boolean differentfolder = false;
	String usefolder = IJ.getDirectory("imagej");
	String addToName = "";
	// String[] RoisNames;

	/**
	 * Main processing method for the Snake_deriche_ object
	 *
	 * @param ip
	 *            image
	 */
	public void run(ImageProcessor ip) {

		configDriver = new SnakeConfigDriver();
		AdvancedParameters();

		// original stack

		snakeList = new ArrayList<SnakeObject>();

	

		boolean dialog;
		boolean dialogAdvanced;
		if (Auto)
			dialog = false;
		else
			dialog = Dialogue();
		// many rois
		RoiManager roimanager = RoiManager.getInstance();
		if (roimanager == null) {
			roimanager = new RoiManager();
			roimanager.setVisible(true);
			rorig = imp.getRoi();
			if (rorig == null) {
				IJ.showMessage("Roi required");
			} else {
				roimanager.add(imp, rorig, 0);
			}
		}
		nbRois = roimanager.getCount();
		IJ.log("processing " + nbRois + "rois");
		Roi[] RoisOrig = roimanager.getRoisAsArray();
		Roi[] RoisCurrent = new Roi[nbRois];
		Roi[] RoisResult = new Roi[nbRois];
		System.arraycopy(RoisOrig, 0, RoisCurrent, 0, nbRois);

		if (advanced)
			dialogAdvanced = AdvancedDialog();

		regmin = reg / 2.0;
		regmax = reg;

		final FloatType type = originalimage.randomAccess().get().createVariable();
		final ImgFactory<FloatType> factory = net.imglib2.util.Util.getArrayOrCellImgFactory(originalimage, type);
		resultoriginalimage = factory.create(originalimage, type);

		


		Roi roi;
		ABSnakeFast snake;

		currentimg = originalimage;

		// Expand the image by 5 pixels
		Interval interval = Intervals.expand(currentimg, 5);
		currentimg = Views.interval(Views.extendBorder(currentimg), interval);
		
		overlay = imp.getOverlay();

		if( overlay == null )
		{
			overlay = new Overlay();
			
		}

		overlay.clear();
		

		for (int i = 0; i < RoisOrig.length; i++) {

			
				roi = RoisOrig[i];
			
			IJ.log("processing Frame no. " + Frame + " with roi " + i);
			IJ.selectWindow("Log");

			IJ.saveAs("Text", usefolder + "//" + "Logsnakerun.txt");
			snake = processSnake(roi, i + 1);
			snake.killImages();

			snake.DrawSnake(overlay, originalimage, colorDraw, 1);
			RoisResult[i] = snake.createRoi();
			RoisResult[i].setName("res-" + i);
			RoisCurrent[i] = snake.createRoi();

			
			if (RoisResult[i] != null) {
				double IntensityBio = getIntensity(RoisResult[i]);
				double IntensityCherry = getIntensity(RoisResult[i]);
				double[] center = getCentreofMass(RoisResult[i]);
				SnakeObject currentsnake = new SnakeObject(Frame, i, RoisResult[i], center, IntensityCherry,
						IntensityBio);
				snakeList.add(currentsnake);
			}
			
			 imp.updateAndDraw();
		}
		

		
		

		System.gc();
	}

	public Overlay getResult(){
  	  
  	  return overlay;
    }

	public ArrayList<SnakeObject> getRoiList() {

		return snakeList;
	}

	/**
	 * Dialog
	 *
	 * @return dialog ok ?
	 */
	private boolean Dialogue() {
		// array of colors
		String[] colors = { "Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "Black", "White" };
		int indexcol = 0;
		// create dialog
		GenericDialog gd = new GenericDialog("Snake");
		gd.addNumericField("Gradient_threshold:", Gradthresh, 0);
		gd.addNumericField("Number_of_iterations:", ite, 0);
		gd.addNumericField("Step_result_show:", step, 0);
		// if (stacksi1e == 1) {
		// }

		gd.addChoice("Draw_color:", colors, colors[indexcol]);
		gd.addCheckbox("Create_seg_image:", createsegimage);
		gd.addCheckbox("Save_rois:", saverois);
		gd.addCheckbox("Save_RoiIntensities:", saveIntensity);
		// gd.addCheckbox("Use_roi_names:", useroinames);
		gd.addCheckbox("No_sizeless_rois:", nosi1elessrois);
		gd.addCheckbox("Use_different_folder", differentfolder);
		gd.addStringField("Use_folder:", usefolder);
		gd.addCheckbox("Advanced_options", advanced);

		// show dialog
		gd.showDialog();

		// threshold of edge
		Gradthresh = (int) gd.getNextNumber();

		// number of iterations
		ite = (int) gd.getNextNumber();
		// step of display
		step = (int) gd.getNextNumber();
		// if (stacksi1e == 1) {
		// }
		if (step > ite - 1) {
			IJ.showStatus("Warning : show step too big\n\t step assignation 1");
			step = 1;
		}

		// color choice of display
		indexcol = gd.getNextChoiceIndex();
		switch (indexcol) {
		case 0:
			colorDraw = Color.red;
			break;
		case 1:
			colorDraw = Color.green;
			break;
		case 2:
			colorDraw = Color.blue;
			break;
		case 3:
			colorDraw = Color.cyan;
			break;
		case 4:
			colorDraw = Color.magenta;
			break;
		case 5:
			colorDraw = Color.yellow;
			break;
		case 6:
			colorDraw = Color.black;
			break;
		case 7:
			colorDraw = Color.white;
			break;
		default:
			colorDraw = Color.yellow;
		}

		createsegimage = gd.getNextBoolean();
		saverois = gd.getNextBoolean();
		saveIntensity = gd.getNextBoolean();
		// useroinames=gd.getNextBoolean();
		nosi1elessrois = gd.getNextBoolean();
		differentfolder = gd.getNextBoolean();
		// Vector<?> stringFields=gd.getStringFields();
		// usefolder=((TextField) stringFields.get(0)).getText();
		usefolder = gd.getNextString();
		advanced = gd.getNextBoolean();

		return !gd.wasCanceled();
	}

	/**
	 * Dialog advanced
	 *
	 * @return dialog ok ?
	 */

	private boolean AdvancedDialog() {

		// dialog
		GenericDialog gd = new GenericDialog("Snake Advanced");
		gd.addNumericField("Distance_Search", DistMAX, 0);
		gd.addNumericField("Displacement_min", Displacement_min, 2);
		gd.addNumericField("Displacement_max", Displacement_max, 2);
		gd.addNumericField("Threshold_dist_positive", Threshold_dist_positive, 0);
		gd.addNumericField("Threshold_dist_negative", Threshold_dist_negative, 0);
		gd.addNumericField("Inv_alpha_min", Inv_alpha_min, 2);
		gd.addNumericField("Inv_alpha_max", Inv_alpha_max, 2);
		gd.addNumericField("Reg_min", Reg_min, 2);
		gd.addNumericField("Reg_max", Reg_max, 2);
		gd.addNumericField("Mul_factor", Mul_factor, 4);
		// show dialog
		gd.showDialog();

		DistMAX = (int) gd.getNextNumber();
		Displacement_min = gd.getNextNumber();
		Displacement_max = gd.getNextNumber();
		Threshold_dist_positive = gd.getNextNumber();
		Threshold_dist_negative = gd.getNextNumber();
		Inv_alpha_min = gd.getNextNumber();
		Inv_alpha_max = gd.getNextNumber();
		Reg_min = gd.getNextNumber();
		Reg_max = gd.getNextNumber();
		Mul_factor = gd.getNextNumber();

		return !gd.wasCanceled();

	}

	private void AdvancedParameters() {
		// see advanced dialog class
		configDriver.setMaxDisplacement(Displacement_min, Displacement_max);
		configDriver.setInvAlphaD(Inv_alpha_min, Inv_alpha_max);
		configDriver.setReg(Reg_min, Reg_max);
		configDriver.setStep(Mul_factor);
	}

	public ABSnakeFast processSnake(Roi roi, int numRoi) {

		int i;

		SnakeConfig config;

		processRoi = roi;

		// initialisation of the snake
		ABSnakeFast snake = new ABSnakeFast();
		snake.Init(processRoi);

		snake.setOriginalImage(currentimg);

		// start of computation
		IJ.showStatus("Calculating snake...");

		

		double InvAlphaD = configDriver.getInvAlphaD(false);
		double regMax = configDriver.getReg(false);
		double regMin = configDriver.getReg(true);
		double DisplMax = configDriver.getMaxDisplacement(false);
		double mul = configDriver.getStep();

		config = new SnakeConfig(Gradthresh, DisplMax, DistMAX, regMin, regMax, 1.0 / InvAlphaD);
		snake.setConfig(config);
		// compute image gradient
		snake.computeGrad(currentimg);

		double dist0 = 0.0;
		double dist;

		for (i = 0; i < ite; i++) {
			if (IJ.escapePressed()) {
				break;
			}
			// each iteration
			dist = snake.process();

			if ((dist >= dist0) && (dist < force)) {
				// System.out.println("update " + config.getAlpha());
				snake.computeGrad(currentimg);
				config.update(mul);
			}
			dist0 = dist;

			// display of the snake
			if ((step > 0) && ((i % step) == 0)) {
				IJ.showStatus("Show intermediate result (iteration n" + (i + 1) + ")");

			}
		}

		snake.setOriginalImage(null);

		return snake;
	}

	public double getIntensity(Roi roi) {

		double Intensity = 0;

		Cursor<FloatType> currentcursor = Views.iterable(originalimage).localizingCursor();

		final double[] position = new double[originalimage.numDimensions()];

		while (currentcursor.hasNext()) {

			currentcursor.fwd();

			currentcursor.localize(position);

			int x = (int) position[0];
			int y = (int) position[1];

			if (roi.contains(x, y)) {

				Intensity += currentcursor.get().getRealDouble();

			}

		}

		return Intensity;

	}

	public double[] getCentreofMass(Roi roi) {

		double[] center = new double[originalimage.numDimensions()];

		double Intensity = 0;
		double SumX = 0;
		double SumY = 0;
		Cursor<FloatType> currentcursor = Views.iterable(originalimage).localizingCursor();

		final double[] position = new double[originalimage.numDimensions()];

		while (currentcursor.hasNext()) {

			currentcursor.fwd();

			currentcursor.localize(position);

			int x = (int) position[0];
			int y = (int) position[1];

			if (roi.contains(x, y)) {

				
				SumX += currentcursor.getDoublePosition(0) * currentcursor.get().getRealDouble();
				SumY += currentcursor.getDoublePosition(1) * currentcursor.get().getRealDouble();
				Intensity += currentcursor.get().getRealDouble();
			}

		}
		
		center[ 0 ] = SumX / Intensity;
		center[ 1 ] = SumY / Intensity;
		

		return center;

	}

	public RandomAccessibleInterval<FloatType> copy(final RandomAccessibleInterval<FloatType> input) {

		final FloatType type = new FloatType();
		final ImgFactory<FloatType> factory = net.imglib2.util.Util.getArrayOrCellImgFactory(input, type);
		final RandomAccessibleInterval<FloatType> output = factory.create(input, type);
		// create a cursor for both images
		Cursor<FloatType> cursorInput = Views.iterable(input).cursor();
		RandomAccess<FloatType> ranOutput = output.randomAccess();

		// iterate over the input
		while (cursorInput.hasNext()) {
			// move both cursors forward by one pixel
			cursorInput.fwd();
			ranOutput.setPosition(cursorInput);

			ranOutput.get().set(cursorInput.get().get());
		}

		// return the copy
		return output;
	}

	/**
	 * setup
	 *
	 * @param arg
	 *            arguments
	 * @param imp
	 *            image plus
	 * @return setup
	 */
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_8G + DOES_16 + DOES_32 + NO_CHANGES;
	}

}
