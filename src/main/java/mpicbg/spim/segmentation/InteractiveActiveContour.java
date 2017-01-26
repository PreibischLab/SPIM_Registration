package mpicbg.spim.segmentation;

import fiji.tool.SliceListener;
import fiji.tool.SliceObserver;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.EllipseRoi;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.Opener;
import ij.io.RoiEncoder;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import costMatrix.CostFunction;
import costMatrix.IntensityDiffCostFunction;
import costMatrix.SquareDistCostFunction;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.util.Util;
import mpicbg.spim.registration.detection.DetectionSegmentation;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.Point;
import net.imglib2.PointSampleList;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.dog.DogDetection;
import net.imglib2.algorithm.localextrema.RefinedPeak;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.RealSum;
import net.imglib2.view.Views;
import overlaytrack.DisplayGraph;
import spim.process.fusion.FusionHelper;
import trackerType.BlobTracker;
import trackerType.KFsearch;
import trackerType.NNsearch;
import trackerType.TrackModel;

/**
 * An interactive tool for getting Intensity in ROI's using Active Contour
 * 
 * @author Varun Kapoor
 */

public class InteractiveActiveContour implements PlugIn {

	final int scrollbarSize = 1000;

	float sigma = 0.5f;
	float sigma2 = 0.5f;
	float threshold = 1f;

	// steps per octave
	public static int standardSensitivity = 4;
	int sensitivity = standardSensitivity;

	float imageSigma = 0.5f;
	float sigmaMin = 0.5f;
	float sigmaMax = 30f;
	int sigmaInit = 300;
	// ROI original
	int nbRois;
	Roi rorig = null;
	Roi processRoi = null;
	float thresholdMin = 0f;
	float thresholdMax = 1f;
	int thresholdInit = 1;
	int initialSearchradius = 10;
	int maxSearchradius = 15;
	int missedframes = 20;
	double minIntensityImage = Double.NaN;
	double maxIntensityImage = Double.NaN;
	String usefolder = IJ.getDirectory("imagej");
	String addToName = "BlobinFramezStackwBio";
	String addTrackToName = "TrackedBlobsID";
	Color colorDraw = null;
	FinalInterval interval;
	SliceObserver sliceObserver;
	RoiListener roiListener;
	ImagePlus imp, Intensityimp;

	int channel = 0;
	RandomAccessibleInterval<FloatType> img;
	RandomAccessibleInterval<FloatType> originalimg;
	

	// Dimensions of the stck :

	int totalframes = 0;
	int slicesize = 0;
	int length = 0;
	int height = 0;
	RandomAccessibleInterval<FloatType> CurrentView;
	ArrayList<RefinedPeak<Point>> peaks;
	int currentslice;
	// first and last slice to process
	int endFrame, currentframe;
	Color originalColor = new Color(0.8f, 0.8f, 0.8f);
	Color inactiveColor = new Color(0.95f, 0.95f, 0.95f);
	public Rectangle standardRectangle;
	public EllipseRoi standardEllipse;
	boolean isComputing = false;
	boolean isStarted = false;
	boolean enableSigma2 = false;
	boolean sigma2IsAdjustable = true;
	boolean propagate = true;
	boolean lookForMinima = false;
	boolean Auto = false;
	boolean lookForMaxima = true;
	ImagePlus impcopy;
	double CalibrationX;
	double CalibrationY;
	BlobTracker blobtracker;
	CostFunction<SnakeObject, SnakeObject> UserchosenCostFunction;
	ArrayList<ArrayList<SnakeObject>> AllFrameSnakes;

	public static enum ValueChange {
		SIGMA, THRESHOLD, ROI, MINMAX, ALL, FRAME
	}

	boolean isFinished = false;
	boolean wasCanceled = false;

	public boolean isFinished() {
		return isFinished;
	}

	public boolean wasCanceled() {
		return wasCanceled;
	}

	public double getInitialSigma() {
		return sigma;
	}

	public void setInitialSigma(final float value) {
		sigma = value;
		sigmaInit = computeScrollbarPositionFromValue(sigma, sigmaMin, sigmaMax, scrollbarSize);
	}

	public double getSigma2() {
		return sigma2;
	}

	public double getThreshold() {
		return threshold;
	}

	public double getThresholdMin() {
		return thresholdMin;
	}

	public double getThresholdMax() {
		return thresholdMax;
	}

	public void setthresholdMax(final float thresholdMax) {

		this.thresholdMax = thresholdMax;

	}

	public void setthresholdMin(final float thresholdMin) {

		this.thresholdMin = thresholdMin;

	}

	public void setThreshold(final float value) {
		threshold = value;
		thresholdInit = computeScrollbarPositionFromValue(threshold, thresholdMin, thresholdMax, scrollbarSize);
	}

	public boolean getSigma2WasAdjusted() {
		return enableSigma2;
	}

	public boolean getLookForMaxima() {
		return lookForMaxima;
	}

	public boolean getLookForMinima() {
		return lookForMinima;
	}

	public void setLookForMaxima(final boolean lookForMaxima) {
		this.lookForMaxima = lookForMaxima;
	}

	public void setLookForMinima(final boolean lookForMinima) {
		this.lookForMinima = lookForMinima;
	}

	public void setSigmaMax(final float sigmaMax) {
		this.sigmaMax = sigmaMax;
	}

	public void setSigma2isAdjustable(final boolean state) {
		sigma2IsAdjustable = state;
	}

	// for the case that it is needed again, we can save one conversion
	public RandomAccessibleInterval<FloatType> getConvertedImage() {
		return CurrentView;
	}

	public InteractiveActiveContour(final ImagePlus imp, final ImagePlus Intensityimp, final int channel) {
		this.imp = imp;
		this.Intensityimp = Intensityimp;
		this.channel = channel;
		originalimg = ImageJFunctions.convertFloat(imp.duplicate());
		standardRectangle = new Rectangle(20, 20, imp.getWidth() - 40, imp.getHeight() - 40);

	}

	public InteractiveActiveContour(final ImagePlus imp, final ImagePlus Intensityimp) {
		this.imp = imp;
		this.Intensityimp = Intensityimp;
		standardRectangle = new Rectangle(20, 20, imp.getWidth() - 40, imp.getHeight() - 40);
		originalimg = ImageJFunctions.convertFloat(imp.duplicate());
	}

	public void setMinIntensityImage(final double min) {
		this.minIntensityImage = min;
	}

	public void setMaxIntensityImage(final double max) {
		this.maxIntensityImage = max;
	}

	@Override
	public void run(String arg) {

		totalframes = imp.getNFrames();
		slicesize = imp.getNSlices();
		AllFrameSnakes = new ArrayList<ArrayList<SnakeObject>>();
		endFrame = totalframes;

		if (imp == null)
			imp = WindowManager.getCurrentImage();
		if (Intensityimp == null)
			Intensityimp = WindowManager.getCurrentImage();

		if (imp.getType() == ImagePlus.COLOR_RGB || imp.getType() == ImagePlus.COLOR_256) {
			IJ.log("Color images are not supported, please convert to 8, 16 or 32-bit grayscale");
			return;
		}

		Roi roi = imp.getRoi();

		if (roi == null) {
			// IJ.log( "A rectangular ROI is required to define the area..." );
			imp.setRoi(standardRectangle);
			roi = imp.getRoi();
		}

		if (roi.getType() != Roi.RECTANGLE) {
			IJ.log("Only rectangular rois are supported...");
			return;
		}

		currentframe = imp.getFrame();
		imp.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame());
		Intensityimp.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame());
		int z = currentframe;

		// copy the ImagePlus into an ArrayImage<FloatType> for faster access
		CurrentView = getCurrentView(currentslice - 1, currentframe - 1);

		final Float houghval = AutomaticThresholding(CurrentView);

		// Get local Minima in scale space to get Max rho-theta points
		float minPeakValue = houghval;

		setThreshold((float) (minPeakValue * 0.001));

		setthresholdMax((float) (minPeakValue));

		threshold = (float) (getThreshold());

		thresholdMax = (float) getThresholdMax();

		// show the interactive kit
		displaySliders();

		// add listener to the imageplus slice slider
		sliceObserver = new SliceObserver(imp, new ImagePlusListener());
		// compute first version#

		updatePreview(ValueChange.ALL);
		isStarted = true;

		// check whenever roi is modified to update accordingly
		roiListener = new RoiListener();
		imp.getCanvas().addMouseListener(roiListener);

	}

	private boolean Dialogue() {
		GenericDialog gd = new GenericDialog("Choose Frame");

		if (totalframes > 1) {
			gd.addNumericField("Move to frame", currentframe, 0);

		}

		gd.showDialog();
		if (totalframes > 1) {
			currentframe = (int) gd.getNextNumber();

		}
		return !gd.wasCanceled();
	}

	private boolean Dialoguesec() {
		GenericDialog gd = new GenericDialog("Choose Final Frame");

		if (totalframes > 1) {
			gd.addNumericField("Do till frame", totalframes, 0);

			assert (int) gd.getNextNumber() > 1;
		}

		gd.showDialog();
		if (totalframes > 1) {
			totalframes = (int) gd.getNextNumber();

		}
		return !gd.wasCanceled();
	}

	private boolean DialogueTracker() {

		String[] colors = { "Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "Black", "White" };
		String[] whichtracker = { "Kalman (recommended)", "Nearest Neighbour" };
		String[] whichcost = { "Distance based", "Intensity based" };
		int indexcol = 0;
		int trackertype = 0;
		int functiontype = 0;

		// Create dialog
		GenericDialog gd = new GenericDialog("Tracker");

		gd.addChoice("Choose your tracker :", whichtracker, whichtracker[trackertype]);
		gd.addChoice("Choose your Cost function (for Kalman) :", whichcost, whichcost[functiontype]);
		gd.addChoice("Draw tracks with this color :", colors, colors[indexcol]);

		gd.addNumericField("Initial Search Radius", 10, 0);
		gd.addNumericField("Max Movment of Blobs per frame", 15, 0);
		gd.addNumericField("Blobs allowed to be lost for #frames", 20, 0);

		gd.showDialog();

		initialSearchradius = (int) gd.getNextNumber();
		maxSearchradius = (int) gd.getNextNumber();
		missedframes = (int) gd.getNextNumber();
		// Choice of tracker
		trackertype = gd.getNextChoiceIndex();
		if (trackertype == 0) {

			functiontype = gd.getNextChoiceIndex();
			switch (functiontype) {

			case 0:
				UserchosenCostFunction = new SquareDistCostFunction();
				break;

			case 1:
				UserchosenCostFunction = new IntensityDiffCostFunction();
				break;

			default:
				UserchosenCostFunction = new SquareDistCostFunction();

			}
			blobtracker = new KFsearch(AllFrameSnakes, UserchosenCostFunction, maxSearchradius, initialSearchradius,
					slicesize, missedframes);
		}

		if (trackertype == 1)
			blobtracker = new NNsearch(AllFrameSnakes, maxSearchradius, slicesize);

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
		return !gd.wasCanceled();
	}

	/**
	 * Updates the Preview with the current parameters (sigma, threshold, roi,
	 * slicenumber)
	 * 
	 * @param change
	 *            - what did change
	 */

	protected void updatePreview(final ValueChange change) {

		// check if Roi changed
		boolean roiChanged = false;
		Roi roi = imp.getRoi();
		if (roi == null || roi.getType() != Roi.RECTANGLE) {
			imp.setRoi(new Rectangle(standardRectangle));
			roi = imp.getRoi();
			roiChanged = true;
		}

		final Rectangle rect = roi.getBounds();
		if (roiChanged || img == null || change == ValueChange.FRAME || rect.getMinX() != standardRectangle.getMinX()
				|| rect.getMaxX() != standardRectangle.getMaxX() || rect.getMinY() != standardRectangle.getMinY()
				|| rect.getMaxY() != standardRectangle.getMaxY()) {
			standardRectangle = rect;
			long[] min = { (long) standardRectangle.getMinX(), (long) standardRectangle.getMinY() };
			long[] max = { (long) standardRectangle.getMaxX(), (long) standardRectangle.getMaxY() };
			interval = new FinalInterval(min, max);
			img = extractImage(CurrentView);
			roiChanged = true;
		}

		// if we got some mouse click but the ROI did not change we can return
		if (!roiChanged && change == ValueChange.ROI) {
			isComputing = false;
			return;
		}

		// compute the Difference Of Gaussian if necessary
		if (peaks == null || roiChanged || change == ValueChange.SIGMA || change == ValueChange.FRAME
				|| change == ValueChange.THRESHOLD
				|| change == ValueChange.ALL) {
			//
			// Compute the Sigmas for the gaussian folding
			//

			final DogDetection.ExtremaType type;

			if (lookForMaxima)
				type = DogDetection.ExtremaType.MINIMA;
			else
				type = DogDetection.ExtremaType.MAXIMA;

			final DogDetection<FloatType> newdog = new DogDetection<FloatType>(Views.extendZero(img), interval,
					new double[] { 1, 1 }, sigma, sigma2, type, threshold, true);

			peaks = newdog.getSubpixelPeaks();

		}

		// extract peaks to show
		Overlay o = imp.getOverlay();

		if (o == null) {
			o = new Overlay();
			imp.setOverlay(o);
		}

		o.clear();

		RoiManager roimanager = RoiManager.getInstance();

		if (roimanager == null) {
			roimanager = new RoiManager();
		}

		MouseEvent mev = new MouseEvent(imp.getCanvas(), MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 0, 0,
				1, false);
		/*
		 * if ((change == ValueChange.ROI || change == ValueChange.SIGMA ||
		 * change == ValueChange.MINMAX || change == ValueChange.SLICE || change
		 * == ValueChange.THRESHOLD && RoisOrig != null)) {
		 */
		if (mev != null) {

			roimanager.close();

			roimanager = new RoiManager();

			// }
		}

		for (final RefinedPeak<Point> peak : peaks) {
			float x = (float) (peak.getFloatPosition(0));
			float y = (float) (peak.getFloatPosition(1));

			final OvalRoi or = new OvalRoi(Util.round(x - sigma), Util.round(y - sigma), Util.round(sigma + sigma2),
					Util.round(sigma + sigma2));

			if (lookForMaxima)
				or.setStrokeColor(Color.red);
			else
				or.setStrokeColor(Color.green);

			o.add(or);
			roimanager.addRoi(or);
		}

		imp.updateAndDraw();

		isComputing = false;
	}

	protected class moveNextListener implements ActionListener {
		@Override
		public void actionPerformed(final ActionEvent arg0) {

			// add listener to the imageplus slice slider
			sliceObserver = new SliceObserver(imp, new ImagePlusListener());

			imp.setPosition(channel, imp.getSlice(), imp.getFrame());
			Intensityimp.setPosition(channel, imp.getSlice(), imp.getFrame());
			if (imp.getFrame() + 1 <= totalframes) {
				imp.setPosition(channel, imp.getSlice(), imp.getFrame() + 1);
				Intensityimp.setPosition(channel, imp.getSlice(), imp.getFrame());
			} else {
				IJ.log("Max frame number exceeded, moving to last frame instead");
				imp.setPosition(channel, imp.getSlice(), totalframes);
				Intensityimp.setPosition(channel, imp.getSlice(), totalframes);
				currentframe = slicesize;
			}
			currentframe = imp.getFrame();

			if (imp.getType() == ImagePlus.COLOR_RGB || imp.getType() == ImagePlus.COLOR_256) {
				IJ.log("Color images are not supported, please convert to 8, 16 or 32-bit grayscale");
				return;
			}

			Roi roi = imp.getRoi();
			if (roi == null) {
				// IJ.log( "A rectangular ROI is required to define the area..."
				// );
				imp.setRoi(standardRectangle);
				roi = imp.getRoi();
			}

			// copy the ImagePlus into an ArrayImage<FloatType> for faster
			// access
			CurrentView = getCurrentView(currentslice - 1, currentframe - 1);

			updatePreview(ValueChange.FRAME);
			isStarted = true;

			// check whenever roi is modified to update accordingly
			roiListener = new RoiListener();
			imp.getCanvas().addMouseListener(roiListener);
			ImagePlus newimp = new ImagePlus("Currentslice " + currentslice,
					imp.getImageStack().getProcessor(currentslice).duplicate());
			final Rectangle rect = roi.getBounds();

			for (final RefinedPeak<Point> peak : peaks) {

				final float x = (float) peak.getDoublePosition(0);
				final float y = (float) peak.getDoublePosition(1);

				final OvalRoi or = new OvalRoi(Util.round(x - sigma), Util.round(y - sigma), Util.round(sigma + sigma2),
						Util.round(sigma + sigma2));

				if (lookForMaxima)
					or.setStrokeColor(Color.red);
				else if (lookForMinima)
					or.setStrokeColor(Color.green);

				newimp.setRoi(or);

			}

		}
	}

	protected class moveToFrameListener implements ActionListener {
		@Override
		public void actionPerformed(final ActionEvent arg0) {

			boolean dialog = Dialogue();
			if (dialog) {
				// add listener to the imageplus slice slider
				sliceObserver = new SliceObserver(imp, new ImagePlusListener());
				imp.setPosition(channel, imp.getSlice(), imp.getFrame());

				if (currentframe <= slicesize) {
					imp.setPosition(channel, imp.getSlice(), currentframe);
					Intensityimp.setPosition(channel, imp.getSlice(), currentframe);
				} else {
					IJ.log("Max frame number exceeded, moving to last frame instead");
					imp.setPosition(channel, imp.getSlice(), slicesize);
					Intensityimp.setPosition(channel, imp.getSlice(), slicesize);
					currentframe = slicesize;
				}

				if (imp.getType() == ImagePlus.COLOR_RGB || imp.getType() == ImagePlus.COLOR_256) {
					IJ.log("Color images are not supported, please convert to 8, 16 or 32-bit grayscale");
					return;
				}

				Roi roi = imp.getRoi();
				if (roi == null) {
					// IJ.log( "A rectangular ROI is required to define the
					// area..."
					// );
					imp.setRoi(standardRectangle);
					roi = imp.getRoi();
				}

				// copy the ImagePlus into an ArrayImage<FloatType> for faster
				// access
				CurrentView = getCurrentView(currentslice - 1, currentframe - 1);

				// compute first version
				updatePreview(ValueChange.FRAME);
				isStarted = true;

				// check whenever roi is modified to update accordingly
				roiListener = new RoiListener();
				imp.getCanvas().addMouseListener(roiListener);

				ImagePlus newimp = new ImagePlus("Currentslice " + currentslice,
						imp.getImageStack().getProcessor(currentslice).duplicate());

				final Rectangle rect = roi.getBounds();

				for (final RefinedPeak<Point> peak : peaks) {

					final float x = peak.getFloatPosition(0);
					final float y = peak.getFloatPosition(1);

					final OvalRoi or = new OvalRoi(Util.round(x - sigma), Util.round(y - sigma),
							Util.round(sigma + sigma2), Util.round(sigma + sigma2));

					if (lookForMaxima)
						or.setStrokeColor(Color.red);
					else if (lookForMinima)
						or.setStrokeColor(Color.green);

					newimp.setRoi(or);

				}
			}
		}
	}

	protected class moveAllListener implements ActionListener {
		@Override
		public void actionPerformed(final ActionEvent arg0) {

			// add listener to the imageplus slice slider
			sliceObserver = new SliceObserver(imp, new ImagePlusListener());

			boolean dialog = Dialoguesec();
			imp.setPosition(channel, imp.getSlice(), imp.getFrame());
			Intensityimp.setPosition(channel, imp.getSlice(), imp.getFrame());
			int next = imp.getFrame();
			
			for (int z = currentslice; z < slicesize; ++z ){	
				
			for (int index = next; index <= totalframes; ++index) {
			
			
				imp.setPosition(channel, z - 1, index);
				Intensityimp.setPosition(channel, z - 1, index);
				
				currentframe = imp.getFrame();
				
				CurrentView = getCurrentView(z - 1, index - 1);
				
				
				Roi roi = imp.getRoi();
				final Rectangle rect = roi.getBounds();
				InteractiveSnakeFast snake = new InteractiveSnakeFast(CurrentView, CurrentView, index - 1);

				RoiManager manager = RoiManager.getInstance();
				if (manager != null) {
					manager.getRoisAsArray();
				}

				// copy the ImagePlus into an ArrayImage<FloatType> for faster
				// access
				

				updatePreview(ValueChange.FRAME);

				isStarted = true;

				// check whenever roi is modified to update accordingly
				roiListener = new RoiListener();
				imp.getCanvas().addMouseListener(roiListener);

				for (final RefinedPeak<Point> peak : peaks) {

					final float x = (float) peak.getDoublePosition(0);
					final float y = (float) peak.getDoublePosition(1);

					final OvalRoi or = new OvalRoi(Util.round(x - sigma), Util.round(y - sigma),
							Util.round(sigma + sigma2), Util.round(sigma + sigma2));

					if (lookForMaxima)
						or.setStrokeColor(Color.red);
					else if (lookForMinima)
						or.setStrokeColor(Color.green);

					imp.setRoi(or);

				}
				ImageProcessor ip = imp.getProcessor();

				if (Auto) {
					if (index > next)
						snake.Auto = true;
				}
				snake.run(ip);

				Overlay result = snake.getResult();
				ImagePlus resultimp = ImageJFunctions.show(CurrentView); 
				resultimp.setOverlay( result ); 
				resultimp.show();
			
				ArrayList<SnakeObject> currentsnakes = snake.getRoiList();

				if (AllFrameSnakes != null) {

					for (int Listindex = 0; Listindex < AllFrameSnakes.size(); ++Listindex) {

						SnakeObject SnakeFrame = AllFrameSnakes.get(Listindex).get(0);
						int Frame = SnakeFrame.Framenumber;

						if (Frame == currentframe) {
							AllFrameSnakes.remove(Listindex);

						}
					}

				}
			
				AllFrameSnakes.add(currentsnakes);
				IJ.log(" Size of List for tracker: " + AllFrameSnakes.size());
				if (snake.saveIntensity) {

					writeIntensities(usefolder + "//" + addToName + "-z", currentframe, currentsnakes);

				}
				RoiEncoder saveRoi;
				if (snake.saverois) {
					for (int indexs = 0; indexs < currentsnakes.size(); ++indexs) {
						Roi roiToSave = currentsnakes.get(indexs).roi;
						int roiindex = currentsnakes.get(indexs).Label;
						saveRoi = new RoiEncoder(
								usefolder + "//" + "Roi" + addToName + roiindex + "-z" + currentframe + ".roi");
						try {
							saveRoi.write(roiToSave);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
			}
		}
	}

	protected class snakeButtonListener implements ActionListener {
		@Override
		public void actionPerformed(final ActionEvent arg0) {

			ImagePlus newimp = new ImagePlus("Currentslice " + currentframe,
					imp.getImageStack().getProcessor(currentslice).duplicate());
			ImagePlus Intensitynewimp = new ImagePlus("Currentslice " + currentframe,
					Intensityimp.getImageStack().getProcessor(currentslice).duplicate());
			Roi roi = imp.getRoi();
			final Rectangle rect = roi.getBounds();
			InteractiveSnakeFast snake = new InteractiveSnakeFast(CurrentView, CurrentView, currentframe);

			for (final RefinedPeak<Point> peak : peaks) {

				final float x = (float) peak.getDoublePosition(0);
				final float y = (float) peak.getDoublePosition(1);

				final OvalRoi or = new OvalRoi(Util.round(x - sigma), Util.round(y - sigma), Util.round(sigma + sigma2),
						Util.round(sigma + sigma2));

				if (lookForMaxima)
					or.setStrokeColor(Color.red);
				else if (lookForMinima)
					or.setStrokeColor(Color.green);

				newimp.setRoi(or);

			}

			ImageProcessor ip = newimp.getProcessor();

			snake.run(ip);
			Overlay result = snake.getResult();
			ImagePlus resultimp = ImageJFunctions.show(CurrentView); 
			resultimp.setOverlay( result ); 
			resultimp.show();
			ArrayList<SnakeObject> currentsnakes = snake.getRoiList();
			if (AllFrameSnakes != null) {

				for (int Listindex = 0; Listindex < AllFrameSnakes.size(); ++Listindex) {

					SnakeObject SnakeFrame = AllFrameSnakes.get(Listindex).get(0);
					int Frame = SnakeFrame.Framenumber;

					if (Frame == currentframe) {
						AllFrameSnakes.remove(Listindex);

					}
				}

			}

			AllFrameSnakes.add(currentsnakes);
			IJ.log("Size of list for tracker " + AllFrameSnakes.size());
			if (snake.saveIntensity) {

				writeIntensities(usefolder + "//" + addToName + "-t", currentframe, currentsnakes);

			}
			RoiEncoder saveRoi;
			if (snake.saverois) {
				for (int index = 0; index < currentsnakes.size(); ++index) {
					Roi roiToSave = currentsnakes.get(index).roi;
					int roiindex = currentsnakes.get(index).Label;
					saveRoi = new RoiEncoder(
							usefolder + "//" + "Roi" + addToName + roiindex + "-z" + currentframe + ".roi");
					try {
						saveRoi.write(roiToSave);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

		}
	}

	public static float computeSigma2(final float sigma1, final int sensitivity) {
		final float k = (float) DetectionSegmentation.computeK(sensitivity);
		final float[] sigma = DetectionSegmentation.computeSigma(k, sigma1);

		return sigma[1];
	}

	public static void writeIntensities(String nom, int nb, ArrayList<SnakeObject> currentsnakes) {
		NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
		nf.setMaximumFractionDigits(3);
		try {
			File fichier = new File(nom + nb + ".txt");
			FileWriter fw = new FileWriter(fichier);
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write("\tFramenumber\tRoiLabel\tCenterofMassX\tCenterofMassY\tIntensityCherry\tIntensityBio\n");
			for (int index = 0; index < currentsnakes.size(); ++index) {
				bw.write("\t" + nb + "\t" + "\t" + currentsnakes.get(index).Label + "\t" + "\t"
						+ nf.format(currentsnakes.get(index).centreofMass[0]) + "\t" + "\t"
						+ nf.format(currentsnakes.get(index).centreofMass[1]) + "\t" + "\t"
						+ nf.format(currentsnakes.get(index).IntensityCherry) + "\t" + "\t"
						+ nf.format(currentsnakes.get(index).IntensityBio) + "\n");
			}
			bw.close();
			fw.close();
		} catch (IOException e) {
		}
	}

	/**
	 * Extract the current 2d region of interest from the souce image
	 * 
	 * @param CurrentView
	 *            - the CurrentView image, a {@link Image} which is a copy of
	 *            the {@link ImagePlus}
	 * 
	 * @return
	 */

	protected RandomAccessibleInterval<FloatType> extractImage(final RandomAccessibleInterval<FloatType> intervalView) {

		final FloatType type = intervalView.randomAccess().get().createVariable();
		final ImgFactory<FloatType> factory = net.imglib2.util.Util.getArrayOrCellImgFactory(intervalView, type);
		RandomAccessibleInterval<FloatType> totalimg = factory.create(intervalView, type);

		final RandomAccessibleInterval<FloatType> img = Views.interval(intervalView, interval);
		totalimg = Views.interval(Views.extendBorder(img), intervalView);

		return totalimg;
	}

	

	public static Float AutomaticThresholding(RandomAccessibleInterval<FloatType> inputimg) {

		FloatType max = new FloatType();
		FloatType min = new FloatType();
		Float ThresholdNew, Thresholdupdate;

		max = computeMaxIntensity(inputimg);
		min = computeMinIntensity(inputimg);

		ThresholdNew = (max.get() - min.get()) / 2;

		// Get the new threshold value after segmenting the inputimage with
		// thresholdnew
		Thresholdupdate = SegmentbyThresholding(Views.iterable(inputimg), ThresholdNew);

		while (true) {

			ThresholdNew = SegmentbyThresholding(Views.iterable(inputimg), Thresholdupdate);

			// Check if the new threshold value is close to the previous value
			if (Math.abs(Thresholdupdate - ThresholdNew) < 1.0E-2)
				break;
			Thresholdupdate = ThresholdNew;
		}

		return ThresholdNew;

	}

	public static FloatType computeMaxIntensity(final RandomAccessibleInterval<FloatType> inputimg) {
		// create a cursor for the image (the order does not matter)
		final Cursor<FloatType> cursor = Views.iterable(inputimg).cursor();

		// initialize min and max with the first image value
		FloatType type = cursor.next();
		FloatType max = type.copy();

		// loop over the rest of the data and determine min and max value
		while (cursor.hasNext()) {
			// we need this type more than once
			type = cursor.next();

			if (type.compareTo(max) > 0) {
				max.set(type);

			}
		}

		return max;
	}

	public static FloatType computeMinIntensity(final RandomAccessibleInterval<FloatType> inputimg) {
		// create a cursor for the image (the order does not matter)
		final Cursor<FloatType> cursor = Views.iterable(inputimg).cursor();

		// initialize min and max with the first image value
		FloatType type = cursor.next();
		FloatType min = type.copy();

		// loop over the rest of the data and determine min and max value
		while (cursor.hasNext()) {
			// we need this type more than once
			type = cursor.next();

			if (type.compareTo(min) < 0) {
				min.set(type);

			}
		}

		return min;
	}

	// Segment image by thresholding, used to determine automatic thresholding
	// level
	public static Float SegmentbyThresholding(IterableInterval<FloatType> inputimg, Float Threshold) {

		int n = inputimg.numDimensions();
		Float ThresholdNew;
		PointSampleList<FloatType> listA = new PointSampleList<FloatType>(n);
		PointSampleList<FloatType> listB = new PointSampleList<FloatType>(n);
		Cursor<FloatType> cursor = inputimg.localizingCursor();
		while (cursor.hasNext()) {
			cursor.fwd();

			if (cursor.get().get() < Threshold) {
				Point newpointA = new Point(n);
				newpointA.setPosition(cursor);
				listA.add(newpointA, cursor.get().copy());
			} else {
				Point newpointB = new Point(n);
				newpointB.setPosition(cursor);
				listB.add(newpointB, cursor.get().copy());
			}
		}
		final RealSum realSumA = new RealSum();
		long countA = 0;

		for (final FloatType type : listA) {
			realSumA.add(type.getRealDouble());
			++countA;
		}

		final double sumA = realSumA.getSum() / countA;

		final RealSum realSumB = new RealSum();
		long countB = 0;

		for (final FloatType type : listB) {
			realSumB.add(type.getRealDouble());
			++countB;
		}

		final double sumB = realSumB.getSum() / countB;

		ThresholdNew = (float) (sumA + sumB) / 2;

		return ThresholdNew;

	}

	/**
	 * Instantiates the panel for adjusting the paramters
	 */

	protected void displaySliders() {
		final Frame frame = new Frame("Find Blobs and Track");
		frame.setSize(550, 550);

		/* Instantiation */
		final GridBagLayout layout = new GridBagLayout();
		final GridBagConstraints c = new GridBagConstraints();
		final Label DogText = new Label("Use DoG to find Blobs ", Label.CENTER);
		final Scrollbar sigma1 = new Scrollbar(Scrollbar.HORIZONTAL, sigmaInit, 10, 0, 10 + scrollbarSize);
		this.sigma = computeValueFromScrollbarPosition(sigmaInit, sigmaMin, sigmaMax, scrollbarSize);

		final Scrollbar threshold = new Scrollbar(Scrollbar.HORIZONTAL, thresholdInit, 10, 0, 10 + scrollbarSize);
		this.threshold = computeValueFromScrollbarPosition(thresholdInit, thresholdMin, thresholdMax, scrollbarSize);
		this.sigma2 = computeSigma2(this.sigma, this.sensitivity);
		final int sigma2init = computeScrollbarPositionFromValue(this.sigma2, sigmaMin, sigmaMax, scrollbarSize);
		final Scrollbar sigma2 = new Scrollbar(Scrollbar.HORIZONTAL, sigma2init, 10, 0, 10 + scrollbarSize);

		final Label sigmaText1 = new Label("Sigma 1 = " + this.sigma, Label.CENTER);
		final Label sigmaText2 = new Label("Sigma 2 = " + this.sigma2, Label.CENTER);

		final Label thresholdText = new Label("Threshold = " + this.threshold, Label.CENTER);

		final Button track = new Button("Start Tracking");
		final Button cancel = new Button("Cancel");
		final Button snakes = new Button("Apply snakes to current Frame selection");
		final Button moveNextListener = new Button("Move to next frame");
		final Button JumpFrame = new Button("Jump to frame number:");
		final Button AutomatedSake = new Button("Automated Snake run for all frames");
		final Checkbox Auto = new Checkbox("Constant parameters over Frames");
		final Checkbox sigma2Enable = new Checkbox("Enable Manual Adjustment of Sigma 2 ", enableSigma2);
		final Checkbox min = new Checkbox("Look for Minima (green)", lookForMinima);
		final Checkbox max = new Checkbox("Look for Maxima (red)", lookForMaxima);

		/* Location */
		frame.setLayout(layout);

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;

		frame.add(DogText, c);

		++c.gridy;
		frame.add(sigma1, c);

		++c.gridy;
		frame.add(sigmaText1, c);

		++c.gridy;
		frame.add(sigma2, c);

		++c.gridy;
		frame.add(sigmaText2, c);

		++c.gridy;
		c.insets = new Insets(0, 165, 0, 165);
		frame.add(sigma2Enable, c);

		++c.gridy;
		c.insets = new Insets(10, 0, 0, 0);
		frame.add(threshold, c);
		c.insets = new Insets(0, 0, 0, 0);

		++c.gridy;
		frame.add(thresholdText, c);

		++c.gridy;
		c.insets = new Insets(0, 170, 0, 75);
		frame.add(min, c);

		++c.gridy;
		c.insets = new Insets(0, 170, 0, 75);
		frame.add(max, c);

		++c.gridy;
		c.insets = new Insets(10, 175, 10, 175);
		frame.add(moveNextListener, c);

		++c.gridy;
		c.insets = new Insets(0, 175, 0, 175);
		frame.add(JumpFrame, c);

		++c.gridy;
		c.insets = new Insets(10, 120, 10, 120);
		frame.add(snakes, c);

		++c.gridy;
		c.insets = new Insets(0, 145, 0, 95);
		frame.add(Auto, c);

		++c.gridy;
		c.insets = new Insets(0, 145, 0, 145);
		frame.add(AutomatedSake, c);

		++c.gridy;
		c.insets = new Insets(10, 175, 0, 175);
		frame.add(track, c);

		++c.gridy;
		c.insets = new Insets(10, 175, 0, 175);
		frame.add(cancel, c);

		/* Configuration */
		sigma1.addAdjustmentListener(
				new SigmaListener(sigmaText1, sigmaMin, sigmaMax, scrollbarSize, sigma1, sigma2, sigmaText2));
		sigma2.addAdjustmentListener(new Sigma2Listener(sigmaMin, sigmaMax, scrollbarSize, sigma2, sigmaText2));
		threshold.addAdjustmentListener(new ThresholdListener(thresholdText, thresholdMin, thresholdMax));
		track.addActionListener(new TrackerButtonListener(frame));
		cancel.addActionListener(new CancelButtonListener(frame, true));
		snakes.addActionListener(new snakeButtonListener());
		moveNextListener.addActionListener(new moveNextListener());
		JumpFrame.addActionListener(new moveToFrameListener());
		AutomatedSake.addActionListener(new moveAllListener());
		min.addItemListener(new MinListener());
		max.addItemListener(new MaxListener());
		Auto.addItemListener(new AutoListener());
		sigma2Enable.addItemListener(new EnableListener(sigma2, sigmaText2));

		if (!sigma2IsAdjustable)
			sigma2Enable.setEnabled(false);

		frame.addWindowListener(new FrameListener(frame));

		frame.setVisible(true);

		originalColor = sigma2.getBackground();
		sigma2.setBackground(inactiveColor);
		sigmaText1.setFont(sigmaText1.getFont().deriveFont(Font.BOLD));
		thresholdText.setFont(thresholdText.getFont().deriveFont(Font.BOLD));
	}

	protected class EnableListener implements ItemListener {
		final Scrollbar sigma2;
		final Label sigmaText2;

		public EnableListener(final Scrollbar sigma2, final Label sigmaText2) {
			this.sigmaText2 = sigmaText2;
			this.sigma2 = sigma2;
		}

		@Override
		public void itemStateChanged(final ItemEvent arg0) {
			if (arg0.getStateChange() == ItemEvent.DESELECTED) {
				sigmaText2.setFont(sigmaText2.getFont().deriveFont(Font.PLAIN));
				sigma2.setBackground(inactiveColor);
				enableSigma2 = false;
			} else if (arg0.getStateChange() == ItemEvent.SELECTED) {
				sigmaText2.setFont(sigmaText2.getFont().deriveFont(Font.BOLD));
				sigma2.setBackground(originalColor);
				enableSigma2 = true;
			}
		}
	}

	protected class MinListener implements ItemListener {
		@Override
		public void itemStateChanged(final ItemEvent arg0) {
			boolean oldState = lookForMinima;

			if (arg0.getStateChange() == ItemEvent.DESELECTED)
				lookForMinima = false;
			else if (arg0.getStateChange() == ItemEvent.SELECTED)
				lookForMinima = true;

			if (lookForMinima != oldState) {
				while (isComputing)
					SimpleMultiThreading.threadWait(10);

				updatePreview(ValueChange.MINMAX);
			}
		}
	}

	protected class AutoListener implements ItemListener {
		@Override
		public void itemStateChanged(final ItemEvent arg0) {

			if (arg0.getStateChange() == ItemEvent.DESELECTED)
				Auto = false;
			else if (arg0.getStateChange() == ItemEvent.SELECTED)
				Auto = true;

		}
	}

	protected class MaxListener implements ItemListener {
		@Override
		public void itemStateChanged(final ItemEvent arg0) {
			boolean oldState = lookForMaxima;

			if (arg0.getStateChange() == ItemEvent.DESELECTED)
				lookForMaxima = false;
			else if (arg0.getStateChange() == ItemEvent.SELECTED)
				lookForMaxima = true;

			if (lookForMaxima != oldState) {
				while (isComputing)
					SimpleMultiThreading.threadWait(10);

				updatePreview(ValueChange.MINMAX);

			}
		}
	}

	public RandomAccessibleInterval<FloatType> getCurrentView(int Slice, int Frame) {

		RandomAccess<FloatType> ran = originalimg.randomAccess();

		final int ndims = originalimg.numDimensions();

		final FloatType type = originalimg.randomAccess().get().createVariable();
		long[] dim = { originalimg.dimension(0), originalimg.dimension(1) };
		final ImgFactory<FloatType> factory = net.imglib2.util.Util.getArrayOrCellImgFactory(originalimg, type);
		RandomAccessibleInterval<FloatType> totalimg = factory.create(dim, type);
		Cursor<FloatType> cursor = Views.iterable(totalimg).localizingCursor();

		if (ndims > 2 && ndims < 4) {

			// we have x,y,t or x,y,z image

			if (imp.getNSlices() > 1) {

				// We have x, y, z image

				ran.setPosition(Slice, ndims - 1);

				while (cursor.hasNext()) {

					cursor.fwd();

					long x = cursor.getLongPosition(0);
					long y = cursor.getLongPosition(1);

					ran.setPosition(x, 0);
					ran.setPosition(y, 1);

					cursor.get().set(ran.get());

				}

			}

			if (imp.getNFrames() > 1) {

				// We have x, y , t image

				ran.setPosition(Frame, ndims - 1);

				while (cursor.hasNext()) {

					cursor.fwd();

					long x = cursor.getLongPosition(0);
					long y = cursor.getLongPosition(1);

					ran.setPosition(x, 0);
					ran.setPosition(y, 1);

					cursor.get().set(ran.get());

				}

			}

		}

		return totalimg;

	}

	/**
	 * Tests whether the ROI was changed and will recompute the preview
	 * 
	 * @author Stephan Preibisch
	 */
	protected class RoiListener implements MouseListener {
		@Override
		public void mouseClicked(MouseEvent e) {
		}

		@Override
		public void mouseEntered(MouseEvent e) {
		}

		@Override
		public void mouseExited(MouseEvent e) {
		}

		@Override
		public void mousePressed(MouseEvent e) {
		}

		@Override
		public void mouseReleased(final MouseEvent e) {
			// here the ROI might have been modified, let's test for that
			final Roi roi = imp.getRoi();

			if (roi == null || roi.getType() != Roi.RECTANGLE)
				return;

			while (isComputing)
				SimpleMultiThreading.threadWait(10);

			updatePreview(ValueChange.ROI);
		}

	}

	protected class TrackerButtonListener implements ActionListener {
		final Frame parent;

		public TrackerButtonListener(Frame parent) {
			this.parent = parent;
		}

		public void actionPerformed(final ActionEvent arg0) {
			/*
			 * File fichier = new File(addToName + "All" + ".txt"); try {
			 * FileWriter fw = new FileWriter(fichier); BufferedWriter bw = new
			 * BufferedWriter(fw);
			 * 
			 * File folder = new File(usefolder); File[] files =
			 * folder.listFiles(); if (files!=null){ HashMap<Integer, File>
			 * filesmap = new HashMap<Integer, File>();
			 * 
			 * for (int i = 0; i < files.length; ++i) { File file = files[i]; if
			 * (file.isFile() && file.getName().contains(addToName)) {
			 * 
			 * filesmap.put(i, file); }
			 * 
			 * } IJ.log("Total files to be combined:" + filesmap.size()); //
			 * create your iterator for your map Iterator<Entry<Integer, File>>
			 * it = filesmap.entrySet().iterator();
			 * 
			 * while (it.hasNext()) {
			 * 
			 * // the key/value pair is stored here in pairs Map.Entry<Integer,
			 * File> pairs = it.next(); int c; FileInputStream in = new
			 * FileInputStream(pairs.getValue()); while ((c = in.read()) != -1)
			 * {
			 * 
			 * bw.write(c); } }
			 * 
			 * bw.close(); fw.close(); IJ.log(
			 * "Compiled the Object properties for all frames in the folder:" +
			 * usefolder); } } catch (IOException e) { // TODO Auto-generated
			 * catch block e.printStackTrace(); }
			 */
			IJ.log("Start Tracking");

			boolean dialog = DialogueTracker();
			if (dialog) {

				blobtracker.process();

				SimpleWeightedGraph<SnakeObject, DefaultWeightedEdge> graph = blobtracker.getResult();

				IJ.log("Tracking Complete " + " " + "Displaying results");

				DisplayGraph totaldisplaytracks = new DisplayGraph(impcopy, graph, colorDraw);
				totaldisplaytracks.getImp();

				TrackModel model = new TrackModel(graph);
				model.getDirectedNeighborIndex();

				// Get all the track id's
				for (final Integer id : model.trackIDs(true)) {

					// Get the corresponding set for each id
					model.setName(id, "Track" + id);

					final HashSet<SnakeObject> Snakeset = model.trackSnakeObjects(id);
					ArrayList<SnakeObject> list = new ArrayList<SnakeObject>();
					Comparator<SnakeObject> Framecomparison = new Comparator<SnakeObject>() {

						@Override
						public int compare(final SnakeObject A, final SnakeObject B) {

							return A.Framenumber - B.Framenumber;

						}

					};

					Iterator<SnakeObject> Snakeiter = Snakeset.iterator();

					// Write tracks with same track id to file
					try {
						File fichiertrack = new File(usefolder + "//" + addTrackToName + "test" + id + ".txt");
						FileWriter fwtr = new FileWriter(fichiertrack);
						BufferedWriter bwtr = new BufferedWriter(fwtr);
						NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
						nf.setMaximumFractionDigits(3);
						bwtr.write(
								"\tFramenumber\tTrackID\tCenterofMassX\tCenterofMassY\tIntensityCherry\tIntensityBio\n");

						while (Snakeiter.hasNext()) {

							SnakeObject currentsnake = Snakeiter.next();

							list.add(currentsnake);

						}
						Collections.sort(list, Framecomparison);
						for (int index = 0; index < list.size(); ++index) {
							bwtr.write("\t" + list.get(index).Framenumber + "\t" + "\t" + id + "\t" + "\t"
									+ nf.format(list.get(index).centreofMass[0]) + "\t" + "\t"
									+ nf.format(list.get(index).centreofMass[1]) + "\t" + "\t"
									+ nf.format(list.get(index).IntensityCherry) + "\t" + "\t"
									+ nf.format(list.get(index).IntensityBio) + "\n");
						}
						bwtr.close();
						fwtr.close();
					} catch (IOException e) {
					}

				}
			}

		}

	}

	public static void writeTracks(String nom, int Framenumber, int Trackid, SnakeObject currentsnake) {
		NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
		nf.setMaximumFractionDigits(3);
		try {
			File fichier = new File(nom + Trackid + ".txt");
			FileWriter fw = new FileWriter(fichier);
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write("\tFramenumber\tTrackID\tCenterofMassX\tCenterofMassY\tIntensityCherry\tIntensityBio\n");

			bw.write("\t" + Framenumber + "\t" + "\t" + Trackid + "\t" + "\t" + nf.format(currentsnake.centreofMass[0])
					+ "\t" + "\t" + nf.format(currentsnake.centreofMass[1]) + "\t" + "\t"
					+ nf.format(currentsnake.IntensityCherry) + "\t" + "\t" + nf.format(currentsnake.IntensityBio)
					+ "\n");

			bw.close();
			fw.close();
		} catch (IOException e) {
		}
	}

	protected class CancelButtonListener implements ActionListener {
		final Frame parent;
		final boolean cancel;

		// usefolder + "//" + "StaticPropertieszStack" + "-z", currentslice
		public CancelButtonListener(Frame parent, final boolean cancel) {
			this.parent = parent;
			this.cancel = cancel;
		}

		@Override
		public void actionPerformed(final ActionEvent arg0) {
			wasCanceled = cancel;
			close(parent, sliceObserver, imp, roiListener);
		}
	}

	protected class FrameListener extends WindowAdapter {
		final Frame parent;

		public FrameListener(Frame parent) {
			super();
			this.parent = parent;
		}

		@Override
		public void windowClosing(WindowEvent e) {
			close(parent, sliceObserver, imp, roiListener);
		}
	}

	protected final void close(final Frame parent, final SliceObserver sliceObserver, final ImagePlus imp,
			final RoiListener roiListener) {
		if (parent != null)
			parent.dispose();

		if (sliceObserver != null)
			sliceObserver.unregister();

		if (imp != null) {
			if (roiListener != null)
				imp.getCanvas().removeMouseListener(roiListener);

			imp.getOverlay().clear();
			imp.updateAndDraw();
		}

		isFinished = true;
	}

	protected class Sigma2Listener implements AdjustmentListener {
		final float min, max;
		final int scrollbarSize;

		final Scrollbar sigmaScrollbar2;
		final Label sigma2Label;

		public Sigma2Listener(final float min, final float max, final int scrollbarSize,
				final Scrollbar sigmaScrollbar2, final Label sigma2Label) {
			this.min = min;
			this.max = max;
			this.scrollbarSize = scrollbarSize;

			this.sigmaScrollbar2 = sigmaScrollbar2;
			this.sigma2Label = sigma2Label;
		}

		@Override
		public void adjustmentValueChanged(final AdjustmentEvent event) {
			if (enableSigma2) {
				sigma2 = computeValueFromScrollbarPosition(event.getValue(), min, max, scrollbarSize);

				if (sigma2 < sigma) {
					sigma2 = sigma + 0.001f;
					sigmaScrollbar2.setValue(computeScrollbarPositionFromValue(sigma2, min, max, scrollbarSize));
				}

				sigma2Label.setText("Sigma 2 = " + sigma2);

				if (!event.getValueIsAdjusting()) {
					while (isComputing) {
						SimpleMultiThreading.threadWait(10);
					}
					updatePreview(ValueChange.SIGMA);
				}

			} else {
				// if no manual adjustment simply reset it
				sigmaScrollbar2.setValue(computeScrollbarPositionFromValue(sigma2, min, max, scrollbarSize));
			}
		}
	}

	protected class SigmaListener implements AdjustmentListener {
		final Label label;
		final float min, max;
		final int scrollbarSize;

		final Scrollbar sigmaScrollbar1;
		final Scrollbar sigmaScrollbar2;
		final Label sigmaText2;

		public SigmaListener(final Label label, final float min, final float max, final int scrollbarSize,
				final Scrollbar sigmaScrollbar1, final Scrollbar sigmaScrollbar2, final Label sigmaText2) {
			this.label = label;
			this.min = min;
			this.max = max;
			this.scrollbarSize = scrollbarSize;

			this.sigmaScrollbar1 = sigmaScrollbar1;
			this.sigmaScrollbar2 = sigmaScrollbar2;
			this.sigmaText2 = sigmaText2;
		}

		@Override
		public void adjustmentValueChanged(final AdjustmentEvent event) {
			sigma = computeValueFromScrollbarPosition(event.getValue(), min, max, scrollbarSize);

			if (!enableSigma2) {
				sigma2 = computeSigma2(sigma, sensitivity);
				sigmaText2.setText("Sigma 2 = " + sigma2);
				sigmaScrollbar2.setValue(computeScrollbarPositionFromValue(sigma2, min, max, scrollbarSize));
			} else if (sigma > sigma2) {
				sigma = sigma2 - 0.001f;
				sigmaScrollbar1.setValue(computeScrollbarPositionFromValue(sigma, min, max, scrollbarSize));
			}

			label.setText("Sigma 1 = " + sigma);

			// if ( !event.getValueIsAdjusting() )
			{
				while (isComputing) {
					SimpleMultiThreading.threadWait(10);
				}
				updatePreview(ValueChange.SIGMA);
			}
		}
	}

	protected static float computeValueFromScrollbarPosition(final int scrollbarPosition, final float min,
			final float max, final int scrollbarSize) {
		return min + (scrollbarPosition / (float) scrollbarSize) * (max - min);
	}

	protected static int computeScrollbarPositionFromValue(final float sigma, final float min, final float max,
			final int scrollbarSize) {
		return Util.round(((sigma - min) / (max - min)) * scrollbarSize);
	}

	protected class ThresholdListener implements AdjustmentListener {
		final Label label;
		final float min, max;

		public ThresholdListener(final Label label, final float min, final float max) {
			this.label = label;
			this.min = min;
			this.max = max;
		}

		@Override
		public void adjustmentValueChanged(final AdjustmentEvent event) {
			threshold = computeValueFromScrollbarPosition(event.getValue(), min, max, scrollbarSize);
			label.setText("Threshold = " + threshold);

			if (!isComputing) {
				updatePreview(ValueChange.THRESHOLD);
			} else if (!event.getValueIsAdjusting()) {
				while (isComputing) {
					SimpleMultiThreading.threadWait(10);
				}
				updatePreview(ValueChange.THRESHOLD);
			}
		}
	}

	protected class ImagePlusListener implements SliceListener {
		@Override
		public void sliceChanged(ImagePlus arg0) {
			if (isStarted) {
				while (isComputing) {
					SimpleMultiThreading.threadWait(10);
				}
				updatePreview(ValueChange.FRAME);

			}
		}
	}

	public static void main(String[] args) {
		new ImageJ();

		ImagePlus imp = new Opener().openImage("/Users/varunkapoor/res/mCherry-test.tif");
		ImagePlus Intensityimp = new Opener().openImage("/Users/varunkapoor/res/BioLum-test.tif");
		imp.show();
		// Convert the image to 8-bit or 16-bit, very crucial for snakes
		IJ.run("16-bit");
		final ImagePlus currentimp = IJ.getImage();
		Intensityimp.show();
		IJ.run("16-bit");
		final ImagePlus currentIntensityimp = IJ.getImage();

		new InteractiveActiveContour(currentimp, currentIntensityimp).run(null);

	}
}
