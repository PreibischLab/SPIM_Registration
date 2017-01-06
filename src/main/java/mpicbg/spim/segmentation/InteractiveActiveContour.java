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
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
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
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import com.mxgraph.canvas.mxICanvas;
import com.mxgraph.canvas.mxSvgCanvas;
import com.mxgraph.io.mxCodec;
import com.mxgraph.io.mxGdCodec;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxCellRenderer.CanvasFactory;
import com.mxgraph.util.mxDomUtils;
import com.mxgraph.util.mxUtils;
import com.mxgraph.util.mxXmlUtils;
import com.mxgraph.util.png.mxPngEncodeParam;
import com.mxgraph.util.png.mxPngImageEncoder;
import com.mxgraph.view.mxGraph;
import blobObjects.FramedBlob;
import blobObjects.Subgraphs;
import costMatrix.CostFunction;
import costMatrix.IntensityDiffCostFunction;
import costMatrix.SquareDistCostFunction;
import mpicbg.imglib.algorithm.MultiThreaded;
import mpicbg.imglib.algorithm.MultiThreadedAlgorithm;
import mpicbg.imglib.algorithm.MultiThreadedBenchmarkAlgorithm;
import mpicbg.imglib.algorithm.gauss.GaussianConvolutionReal;
import mpicbg.imglib.algorithm.math.LocalizablePoint;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianReal1;
import mpicbg.imglib.algorithm.scalespace.SubpixelLocalization;
import mpicbg.imglib.container.array.ArrayContainerFactory;
import mpicbg.imglib.cursor.LocalizableByDimCursor;
import mpicbg.imglib.cursor.LocalizableCursor;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.image.display.imagej.ImageJFunctions;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyMirrorFactory;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyValueFactory;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.util.Util;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.registration.ViewStructure;
import mpicbg.spim.registration.detection.DetectionSegmentation;
import net.imglib2.RandomAccess;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.view.Views;
import overlaytrack.DisplayGraph;
import overlaytrack.DisplaysubGraph;
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

	final int extraSize = 40;
	final int scrollbarSize = 1000;

	float sigma = 0.5f;
	float sigma2 = 0.5f;
	float threshold = 0.0001f;

	// steps per octave
	public static int standardSensitivity = 4;
	int sensitivity = standardSensitivity;

	float imageSigma = 0.5f;
	float sigmaMin = 0.5f;
	float sigmaMax = 10f;
	int sigmaInit = 300;
	// ROI original
	int nbRois;
	Roi rorig = null;
	Roi processRoi = null;
	float thresholdMin = 0.0001f;
	float thresholdMax = 1f;
	int thresholdInit = 500;
	int initialSearchradius = 10;
	int maxSearchradius = 15;
	int missedframes = 20;
	double minIntensityImage = Double.NaN;
	double maxIntensityImage = Double.NaN;
	String usefolder = IJ.getDirectory("imagej");
	String addToName = "BlobinFramezStackwBio";
	String addTrackToName = "TrackedBlobsID";
	Color colorDraw = null;

	SliceObserver sliceObserver;
	RoiListener roiListener;
	ImagePlus imp;
	ImagePlus Intensityimp;
	int channel = 0;
	Rectangle rectangle;
	Image<FloatType> img;
	ImageStack pile = null;
	ImageStack pile_resultat = null;
	ImageStack pile_seg = null;
	int currentSlice = -1;
	// Dimensions of the stck :
	int stacksize = 0;
	int length = 0;
	int height = 0;
	FloatImagePlus<net.imglib2.type.numeric.real.FloatType> source;
	ArrayList<DifferenceOfGaussianPeak<FloatType>> peaks;
	// first and last slice to process
	int slice2, currentslice;
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
	BlobTracker blobtracker;
	CostFunction<SnakeObject, SnakeObject> UserchosenCostFunction;
	// = new SquareDistCostFunction();
	ArrayList<ArrayList<SnakeObject>> AllFrameSnakes;

	public static enum ValueChange {
		SIGMA, THRESHOLD, SLICE, ROI, MINMAX, ALL
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

	public void setThreshold(final float value) {
		threshold = value;
		final double log1001 = Math.log10(scrollbarSize + 1);
		thresholdInit = (int) Math.round(1001
				- Math.pow(10, -(((threshold - thresholdMin) / (thresholdMax - thresholdMin)) * log1001) + log1001));
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
	public FloatImagePlus<net.imglib2.type.numeric.real.FloatType> getConvertedImage() {
		return source;
	}

	public InteractiveActiveContour(final ImagePlus imp, final ImagePlus Intensityimp, final int channel) {
		this.imp = imp;
		this.Intensityimp = Intensityimp;
		this.channel = channel;
		standardRectangle = new Rectangle(0, 0, imp.getWidth() - 1, imp.getHeight() - 1);

	}

	public InteractiveActiveContour(final ImagePlus imp, final ImagePlus Intensityimp) {
		this.imp = imp;
		this.Intensityimp = Intensityimp;
		standardRectangle = new Rectangle(0, 0, imp.getWidth() - 1, imp.getHeight() - 1);

	}

	public void setMinIntensityImage(final double min) {
		this.minIntensityImage = min;
	}

	public void setMaxIntensityImage(final double max) {
		this.maxIntensityImage = max;
	}

	@Override
	public void run(String arg) {

		impcopy = imp.duplicate();
		// sizes of the stack
		stacksize = imp.getStack().getSize();
		AllFrameSnakes = new ArrayList<ArrayList<SnakeObject>>();
		slice2 = stacksize;

		if (imp == null)
			imp = WindowManager.getCurrentImage();

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

		currentslice = imp.getFrame();
		imp.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame());

		int z = currentslice;

		// copy the ImagePlus into an ArrayImage<FloatType> for faster access
		source = convertToFloat(imp, channel, z - 1, minIntensityImage, maxIntensityImage);

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

		if (stacksize > 1) {
			gd.addNumericField("Move to frame", currentslice, 0);

		}

		gd.showDialog();
		if (stacksize > 1) {
			currentslice = (int) gd.getNextNumber();

		}
		return !gd.wasCanceled();
	}

	private boolean Dialoguesec() {
		GenericDialog gd = new GenericDialog("Choose Final Frame");

		if (stacksize > 1) {
			gd.addNumericField("Do till frame", stacksize, 0);

			assert (int) gd.getNextNumber() > 1;
		}

		gd.showDialog();
		if (stacksize > 1) {
			stacksize = (int) gd.getNextNumber();

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
		switch (trackertype) {
		case 0:
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
					stacksize, missedframes);
			break;
		case 1:
			blobtracker = new NNsearch(AllFrameSnakes, maxSearchradius, stacksize);
			break;

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
		if (roiChanged || img == null || change == ValueChange.SLICE || rect.getMinX() != rectangle.getMinX()
				|| rect.getMaxX() != rectangle.getMaxX() || rect.getMinY() != rectangle.getMinY()
				|| rect.getMaxY() != rectangle.getMaxY()) {
			rectangle = rect;
			img = extractImage(source, rectangle, extraSize);
			roiChanged = true;
		}

		// if we got some mouse click but the ROI did not change we can return
		if (!roiChanged && change == ValueChange.ROI) {
			isComputing = false;
			return;
		}

		// compute the Difference Of Gaussian if necessary
		if (peaks == null || roiChanged || change == ValueChange.SIGMA || change == ValueChange.SLICE
				|| change == ValueChange.ALL) {
			//
			// Compute the Sigmas for the gaussian folding
			//

			final float k, K_MIN1_INV;
			final float[] sigma, sigmaDiff;

			if (enableSigma2) {
				sigma = new float[2];
				sigma[0] = this.sigma;
				sigma[1] = this.sigma2;
				k = sigma[1] / sigma[0];
				K_MIN1_INV = DetectionSegmentation.computeKWeight(k);
				sigmaDiff = DetectionSegmentation.computeSigmaDiff(sigma, imageSigma);
			} else {
				k = (float) DetectionSegmentation.computeK(sensitivity);
				K_MIN1_INV = DetectionSegmentation.computeKWeight(k);
				sigma = DetectionSegmentation.computeSigma(k, this.sigma);
				sigmaDiff = DetectionSegmentation.computeSigmaDiff(sigma, imageSigma);
			}

			// the upper boundary
			this.sigma2 = sigma[1];

			final DifferenceOfGaussianReal1<FloatType> dog = new DifferenceOfGaussianReal1<FloatType>(img,
					new OutOfBoundsStrategyValueFactory<FloatType>(), sigmaDiff[0], sigmaDiff[1], thresholdMin / 4,
					K_MIN1_INV);
			dog.setKeepDoGImage(true);
			dog.process();

			final SubpixelLocalization<FloatType> subpixel = new SubpixelLocalization<FloatType>(dog.getDoGImage(),
					dog.getPeaks());
			subpixel.process();

			peaks = dog.getPeaks();

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

		Roi[] RoisOrig = roimanager.getRoisAsArray();

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

		for (final DifferenceOfGaussianPeak<FloatType> peak : peaks) {
			if ((peak.isMax() && lookForMaxima) || (peak.isMin() && lookForMinima)) {
				final float x = peak.getPosition(0);
				final float y = peak.getPosition(1);
				if (Math.abs(peak.getValue().get()) > threshold && x >= extraSize / 2 && y >= extraSize / 2
						&& x < rect.width + extraSize / 2 && y < rect.height + extraSize / 2) {
					final OvalRoi or = new OvalRoi(Util.round(x - sigma) + rect.x - extraSize / 2,
							Util.round(y - sigma) + rect.y - extraSize / 2, Util.round(sigma + sigma2),
							Util.round(sigma + sigma2));

					if (peak.isMax())
						or.setStrokeColor(Color.red);
					else if (peak.isMin())
						or.setStrokeColor(Color.green);

					o.add(or);
					roimanager.addRoi(or);

				}

			}
		}

		imp.updateAndDraw();

		isComputing = false;
	}

	protected class moveNextListener implements ActionListener {
		@Override
		public void actionPerformed(final ActionEvent arg0) {

			// add listener to the imageplus slice slider
			sliceObserver = new SliceObserver(imp, new ImagePlusListener());

			imp.setSlice(imp.getFrame());
			if (imp.getFrame() + 1 <= stacksize) {
				imp.setSlice(imp.getFrame() + 1);
			} else {
				IJ.log("Max frame number exceeded, moving to last frame instead");
				imp.setSlice(stacksize);
				currentslice = stacksize;
			}
			currentslice = imp.getFrame();

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
			source = convertToFloat(imp, channel, currentslice - 1, minIntensityImage, maxIntensityImage);

			updatePreview(ValueChange.SLICE);
			isStarted = true;

			// check whenever roi is modified to update accordingly
			roiListener = new RoiListener();
			imp.getCanvas().addMouseListener(roiListener);
			ImagePlus newimp = new ImagePlus("Currentslice " + currentslice,
					imp.getImageStack().getProcessor(currentslice).duplicate());
			final Rectangle rect = roi.getBounds();

			for (final DifferenceOfGaussianPeak<FloatType> peak : peaks) {
				if ((peak.isMax() && lookForMaxima) || (peak.isMin() && lookForMinima)) {
					final float x = peak.getPosition(0);
					final float y = peak.getPosition(1);

					if (Math.abs(peak.getValue().get()) > threshold && x >= extraSize / 2 && y >= extraSize / 2
							&& x < rect.width + extraSize / 2 && y < rect.height + extraSize / 2) {
						final OvalRoi or = new OvalRoi(Util.round(x - sigma) + rect.x - extraSize / 2,
								Util.round(y - sigma) + rect.y - extraSize / 2, Util.round(sigma + sigma2),
								Util.round(sigma + sigma2));

						if (peak.isMax())
							or.setStrokeColor(Color.red);
						else if (peak.isMin())
							or.setStrokeColor(Color.green);

						newimp.setRoi(or);

					}

				}
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

				imp.setSlice(imp.getFrame());

				if (currentslice <= stacksize) {
					imp.setSlice(currentslice);
				} else {
					IJ.log("Max frame number exceeded, moving to last frame instead");
					imp.setSlice(stacksize);
					currentslice = stacksize;
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
				source = convertToFloat(imp, channel, currentslice - 1, minIntensityImage, maxIntensityImage);

				// compute first version
				updatePreview(ValueChange.SLICE);
				isStarted = true;

				// check whenever roi is modified to update accordingly
				roiListener = new RoiListener();
				imp.getCanvas().addMouseListener(roiListener);

				ImagePlus newimp = new ImagePlus("Currentslice " + currentslice,
						imp.getImageStack().getProcessor(currentslice).duplicate());

				final Rectangle rect = roi.getBounds();

				for (final DifferenceOfGaussianPeak<FloatType> peak : peaks) {
					if ((peak.isMax() && lookForMaxima) || (peak.isMin() && lookForMinima)) {
						final float x = peak.getPosition(0);
						final float y = peak.getPosition(1);

						if (Math.abs(peak.getValue().get()) > threshold && x >= extraSize / 2 && y >= extraSize / 2
								&& x < rect.width + extraSize / 2 && y < rect.height + extraSize / 2) {
							final OvalRoi or = new OvalRoi(Util.round(x - sigma) + rect.x - extraSize / 2,
									Util.round(y - sigma) + rect.y - extraSize / 2, Util.round(sigma + sigma2),
									Util.round(sigma + sigma2));

							if (peak.isMax())
								or.setStrokeColor(Color.red);
							else if (peak.isMin())
								or.setStrokeColor(Color.green);

							newimp.setRoi(or);

						}

					}
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

			imp.setSlice(imp.getFrame());
			Intensityimp.setSlice(imp.getSlice());
			int next = imp.getFrame();
			for (int index = next; index <= stacksize; ++index) {
				imp.setSlice(index);
				currentslice = imp.getFrame();
				Intensityimp.setSlice(imp.getSlice());
				ImagePlus newimp = new ImagePlus("Currentslice " + currentslice,
						imp.getImageStack().getProcessor(currentslice).duplicate());
				ImagePlus Intensitynewimp = new ImagePlus("Currentslice " + currentslice,
						Intensityimp.getImageStack().getProcessor(currentslice).duplicate());
				Roi roi = imp.getRoi();
				final Rectangle rect = roi.getBounds();
				InteractiveSnake snake = new InteractiveSnake(newimp, Intensitynewimp, currentslice);

				RoiManager manager = RoiManager.getInstance();
				if (manager != null) {
					manager.getRoisAsArray();
				}

				// copy the ImagePlus into an ArrayImage<FloatType> for faster
				// access
				source = convertToFloat(imp, channel, currentslice - 1, minIntensityImage, maxIntensityImage);

				updatePreview(ValueChange.SLICE);

				isStarted = true;

				// check whenever roi is modified to update accordingly
				roiListener = new RoiListener();
				imp.getCanvas().addMouseListener(roiListener);

				for (final DifferenceOfGaussianPeak<FloatType> peak : peaks) {
					if ((peak.isMax() && lookForMaxima) || (peak.isMin() && lookForMinima)) {
						final float x = peak.getPosition(0);
						final float y = peak.getPosition(1);

						if (Math.abs(peak.getValue().get()) > threshold && x >= extraSize / 2 && y >= extraSize / 2
								&& x < rect.width + extraSize / 2 && y < rect.height + extraSize / 2) {
							final OvalRoi or = new OvalRoi(Util.round(x - sigma) + rect.x - extraSize / 2,
									Util.round(y - sigma) + rect.y - extraSize / 2, Util.round(sigma + sigma2),
									Util.round(sigma + sigma2));

							if (peak.isMax())
								or.setStrokeColor(Color.red);
							else if (peak.isMin())
								or.setStrokeColor(Color.green);

							newimp.setRoi(or);

						}

					}
				}
				ImageProcessor ip = newimp.getProcessor();

				if (Auto) {
					if (index > next)
						snake.Auto = true;
				}
				snake.run(ip);

				ImageStack currentimg = snake.getResult();
				new ImagePlus("Snake Roi's for slice:" + currentslice, currentimg).show();
				ArrayList<SnakeObject> currentsnakes = snake.getRoiList();

				if (AllFrameSnakes != null) {

					for (int Listindex = 0; Listindex < AllFrameSnakes.size(); ++Listindex) {

						SnakeObject SnakeFrame = AllFrameSnakes.get(Listindex).get(0);
						int Frame = SnakeFrame.Framenumber;

						if (Frame == currentslice) {
							AllFrameSnakes.remove(Listindex);

						}
					}

				}

				AllFrameSnakes.add(currentsnakes);
				IJ.log(" Size of List for tracker: " + AllFrameSnakes.size());
				if (snake.saveIntensity) {

					snake.writeIntensities(usefolder + "//" + addToName + "-z", currentslice, currentsnakes);

				}
				RoiEncoder saveRoi;
				if (snake.saverois) {
					for (int indexs = 0; indexs < currentsnakes.size(); ++indexs) {
						Roi roiToSave = currentsnakes.get(indexs).roi;
						int roiindex = currentsnakes.get(indexs).Label;
						saveRoi = new RoiEncoder(
								usefolder + "//" + "Roi" + addToName + roiindex + "-z" + currentslice + ".roi");
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

	protected class snakeButtonListener implements ActionListener {
		@Override
		public void actionPerformed(final ActionEvent arg0) {

			ImagePlus newimp = new ImagePlus("Currentslice " + currentslice,
					imp.getImageStack().getProcessor(currentslice).duplicate());
			ImagePlus Intensitynewimp = new ImagePlus("Currentslice " + currentslice,
					Intensityimp.getImageStack().getProcessor(currentslice).duplicate());
			Roi roi = imp.getRoi();
			final Rectangle rect = roi.getBounds();
			InteractiveSnake snake = new InteractiveSnake(newimp, Intensitynewimp, currentslice);

			for (final DifferenceOfGaussianPeak<FloatType> peak : peaks) {
				if ((peak.isMax() && lookForMaxima) || (peak.isMin() && lookForMinima)) {
					final float x = peak.getPosition(0);
					final float y = peak.getPosition(1);

					if (Math.abs(peak.getValue().get()) > threshold && x >= extraSize / 2 && y >= extraSize / 2
							&& x < rect.width + extraSize / 2 && y < rect.height + extraSize / 2) {
						final OvalRoi or = new OvalRoi(Util.round(x - sigma) + rect.x - extraSize / 2,
								Util.round(y - sigma) + rect.y - extraSize / 2, Util.round(sigma + sigma2),
								Util.round(sigma + sigma2));

						if (peak.isMax())
							or.setStrokeColor(Color.red);
						else if (peak.isMin())
							or.setStrokeColor(Color.green);

						newimp.setRoi(or);

					}

				}
			}

			ImageProcessor ip = newimp.getProcessor();

			snake.run(ip);
			ImageStack currentimg = snake.getResult();
			new ImagePlus("Snake Roi's for slice:" + currentslice, currentimg).show();
			ArrayList<SnakeObject> currentsnakes = snake.getRoiList();
			if (AllFrameSnakes != null) {

				for (int Listindex = 0; Listindex < AllFrameSnakes.size(); ++Listindex) {

					SnakeObject SnakeFrame = AllFrameSnakes.get(Listindex).get(0);
					int Frame = SnakeFrame.Framenumber;

					if (Frame == currentslice) {
						AllFrameSnakes.remove(Listindex);

					}
				}

			}

			AllFrameSnakes.add(currentsnakes);
			IJ.log("Size of list for tracker " + AllFrameSnakes.size());
			if (snake.saveIntensity) {

				snake.writeIntensities(usefolder + "//" + addToName + "-z", currentslice, currentsnakes);

			}
			RoiEncoder saveRoi;
			if (snake.saverois) {
				for (int index = 0; index < currentsnakes.size(); ++index) {
					Roi roiToSave = currentsnakes.get(index).roi;
					int roiindex = currentsnakes.get(index).Label;
					saveRoi = new RoiEncoder(
							usefolder + "//" + "Roi" + addToName + roiindex + "-z" + currentslice + ".roi");
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

	/**
	 * Extract the current 2d region of interest from the souce image
	 * 
	 * @param source
	 *            - the source image, a {@link Image} which is a copy of the
	 *            {@link ImagePlus}
	 * @param rectangle
	 *            - the area of interest
	 * @param extraSize
	 *            - the extra size around so that detections at the border of
	 *            the roi are not messed up
	 * @return
	 */
	protected Image<FloatType> extractImage(final FloatImagePlus<net.imglib2.type.numeric.real.FloatType> source,
			final Rectangle rectangle, final int extraSize) {
		final Image<FloatType> img = new ImageFactory<FloatType>(new FloatType(), new ArrayContainerFactory())
				.createImage(new int[] { rectangle.width + extraSize, rectangle.height + extraSize });

		final int offsetX = rectangle.x - extraSize / 2;
		final int offsetY = rectangle.y - extraSize / 2;

		final int[] location = new int[source.numDimensions()];

		if (location.length > 2)
			location[2] = (imp.getCurrentSlice() - 1) / imp.getNChannels();

		final LocalizableCursor<FloatType> cursor = img.createLocalizableCursor();
		final RandomAccess<net.imglib2.type.numeric.real.FloatType> positionable;

		if (offsetX >= 0 && offsetY >= 0 && offsetX + img.getDimension(0) < source.dimension(0)
				&& offsetY + img.getDimension(1) < source.dimension(1)) {
			// it is completely inside so we need no outofbounds for copying
			positionable = source.randomAccess();
		} else {
			positionable = Views.extendMirrorSingle(source).randomAccess();
		}

		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.getPosition(location);

			location[0] += offsetX;
			location[1] += offsetY;

			positionable.setPosition(location);

			cursor.getType().set(positionable.get().get());
		}

		return img;
	}

	/**
	 * Normalize and make a copy of the {@link ImagePlus} into an {@link Image}
	 * &gt;FloatType&lt; for faster access when copying the slices
	 * 
	 * @param imp
	 *            - the {@link ImagePlus} input image
	 * @return - the normalized copy [0...1]
	 */
	public static FloatImagePlus<net.imglib2.type.numeric.real.FloatType> convertToFloat(final ImagePlus imp,
			int channel, int timepoint) {
		return convertToFloat(imp, channel, timepoint, Double.NaN, Double.NaN);
	}

	public static FloatImagePlus<net.imglib2.type.numeric.real.FloatType> convertToFloat(final ImagePlus imp,
			int channel, int timepoint, final double min, final double max) {
		// stupid 1-offset of imagej
		channel++;
		timepoint++;

		final int h = imp.getHeight();
		final int w = imp.getWidth();

		final ArrayList<float[]> img = new ArrayList<float[]>();

		if (imp.getProcessor() instanceof FloatProcessor) {
			for (int z = 0; z < imp.getNSlices(); ++z)
				img.add(((float[]) imp.getStack().getProcessor(imp.getStackIndex(channel, z + 1, timepoint))
						.getPixels()).clone());
		} else if (imp.getProcessor() instanceof ByteProcessor) {
			for (int z = 0; z < imp.getNSlices(); ++z) {
				final byte[] pixels = (byte[]) imp.getStack().getProcessor(imp.getStackIndex(channel, z + 1, timepoint))
						.getPixels();
				final float[] pixelsF = new float[pixels.length];

				for (int i = 0; i < pixels.length; ++i)
					pixelsF[i] = pixels[i] & 0xff;

				img.add(pixelsF);
			}
		} else if (imp.getProcessor() instanceof ShortProcessor) {
			for (int z = 0; z < imp.getNSlices(); ++z) {
				final short[] pixels = (short[]) imp.getStack()
						.getProcessor(imp.getStackIndex(channel, z + 1, timepoint)).getPixels();
				final float[] pixelsF = new float[pixels.length];

				for (int i = 0; i < pixels.length; ++i)
					pixelsF[i] = pixels[i] & 0xffff;

				img.add(pixelsF);
			}
		} else // some color stuff or so
		{
			for (int z = 0; z < imp.getNSlices(); ++z) {
				final ImageProcessor ip = imp.getStack().getProcessor(imp.getStackIndex(channel, z + 1, timepoint));
				final float[] pixelsF = new float[w * h];

				int i = 0;

				for (int y = 0; y < h; ++y)
					for (int x = 0; x < w; ++x)
						pixelsF[i++] = ip.getPixelValue(x, y);

				img.add(pixelsF);
			}
		}

		final FloatImagePlus<net.imglib2.type.numeric.real.FloatType> i = createImgLib2(img, w, h);

		if (Double.isNaN(min) || Double.isNaN(max) || Double.isInfinite(min) || Double.isInfinite(max) || min == max)
			FusionHelper.normalizeImage(i);
		else
			FusionHelper.normalizeImage(i, (float) min, (float) max);

		return i;
	}

	public static FloatImagePlus<net.imglib2.type.numeric.real.FloatType> createImgLib2(final List<float[]> img,
			final int w, final int h) {
		final ImagePlus imp;

		if (img.size() > 1) {
			final ImageStack stack = new ImageStack(w, h);
			for (int z = 0; z < img.size(); ++z)
				stack.addSlice(new FloatProcessor(w, h, img.get(z)));
			imp = new ImagePlus("ImgLib2 FloatImagePlus (3d)", stack);
		} else {
			imp = new ImagePlus("ImgLib2 FloatImagePlus (2d)", new FloatProcessor(w, h, img.get(0)));
		}

		return ImagePlusAdapter.wrapFloat(imp);
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
		final float log1001 = (float) Math.log10(scrollbarSize + 1);

		this.threshold = thresholdMin
				+ ((log1001 - (float) Math.log10(1001 - thresholdInit)) / log1001) * (thresholdMax - thresholdMin);

		this.sigma2 = computeSigma2(this.sigma, this.sensitivity);
		final int sigma2init = computeScrollbarPositionFromValue(this.sigma2, sigmaMin, sigmaMax, scrollbarSize);
		final Scrollbar sigma2 = new Scrollbar(Scrollbar.HORIZONTAL, sigma2init, 10, 0, 10 + scrollbarSize);

		final Label sigmaText1 = new Label("Sigma 1 = " + this.sigma, Label.CENTER);
		final Label sigmaText2 = new Label("Sigma 2 = " + this.sigma2, Label.CENTER);

		final Label thresholdText = new Label("Threshold = " + this.threshold, Label.CENTER);

		final Button button = new Button("Start Tracking");
		final Button cancel = new Button("Cancel");
		final Button snakes = new Button("Apply snakes to current Frame selection");
		final Button moveNextListener = new Button("Move to next frame");
		final Button JumpFrame = new Button("Jump to frame number:");
		final Button ApplytoStack = new Button("Automated Snake run for all frames");
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
		frame.add(ApplytoStack, c);

		++c.gridy;
		c.insets = new Insets(10, 175, 0, 175);
		frame.add(button, c);

		++c.gridy;
		c.insets = new Insets(10, 175, 0, 175);
		frame.add(cancel, c);

		/* Configuration */
		sigma1.addAdjustmentListener(
				new SigmaListener(sigmaText1, sigmaMin, sigmaMax, scrollbarSize, sigma1, sigma2, sigmaText2));
		sigma2.addAdjustmentListener(new Sigma2Listener(sigmaMin, sigmaMax, scrollbarSize, sigma2, sigmaText2));
		threshold.addAdjustmentListener(new ThresholdListener(thresholdText, thresholdMin, thresholdMax));
		button.addActionListener(new TrackerButtonListener(frame));
		cancel.addActionListener(new CancelButtonListener(frame, true));
		snakes.addActionListener(new snakeButtonListener());
		moveNextListener.addActionListener(new moveNextListener());
		JumpFrame.addActionListener(new moveToFrameListener());
		ApplytoStack.addActionListener(new moveAllListener());
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

			File fichier = new File(addToName + "All" + ".txt");
			try {
				FileWriter fw = new FileWriter(fichier);
				BufferedWriter bw = new BufferedWriter(fw);

				File folder = new File(usefolder);
				File[] files = folder.listFiles();

				HashMap<Integer, File> filesmap = new HashMap<Integer, File>();

				for (int i = 0; i < files.length; ++i) {
					File file = files[i];
					if (file.isFile() && file.getName().contains(addToName)) {

						filesmap.put(i, file);
					}

				}
				IJ.log("Total files to be combined:" + filesmap.size());
				// create your iterator for your map
				Iterator<Entry<Integer, File>> it = filesmap.entrySet().iterator();

				while (it.hasNext()) {

					// the key/value pair is stored here in pairs
					Map.Entry<Integer, File> pairs = it.next();
					int c;
					FileInputStream in = new FileInputStream(pairs.getValue());
					while ((c = in.read()) != -1) {

						bw.write(c);
					}
				}

				bw.close();
				fw.close();
				IJ.log("Compiled the Object properties for all frames in the folder:" + usefolder);
			}

			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			IJ.log("Start Tracking");

			boolean dialog = DialogueTracker();
			if (dialog) {

				blobtracker.process();

				SimpleWeightedGraph<SnakeObject, DefaultWeightedEdge> graph = blobtracker.getResult();

				IJ.log("Tracking Complete " + " " + "Displaying results");

				DisplayGraph totaldisplaytracks = new DisplayGraph(impcopy, graph, colorDraw);
				totaldisplaytracks.getImp();
				
				TrackModel model = new TrackModel(graph) ;
				
				
				
				// Get all the track id's
				for ( final Integer id : model.trackIDs( true ) )
				{
				   
					// Get the corresponding set for each id
					
					final HashSet<SnakeObject> Snakeset = model.trackSnakeObjects(id);
					ArrayList<SnakeObject> list = new ArrayList<SnakeObject>(Snakeset);
					Comparator<SnakeObject> Framecomparison = new Comparator<SnakeObject>(){
							
							@Override
					       public int compare(final SnakeObject A, final SnakeObject B){
							
								int FramenumberA = A.Framenumber;
								int FramenumberB = B.Framenumber;
								
								if (FramenumberA > FramenumberB)
								
								return A.compareTo(B);
								
								else
								return B.compareTo(A);
							
						}

						
							
							
						};
	
				Collections.sort(list, Framecomparison);
						
					Iterator<SnakeObject> Snakeiter = Snakeset.iterator();
					
					// Write tracks with same track id to file
					try {
				        File fichiertrack = new File(usefolder + "//" + addTrackToName   + id + ".txt");
				        FileWriter fwtr = new FileWriter(fichiertrack);
				        BufferedWriter bwtr = new BufferedWriter(fwtr);
				        NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
					    nf.setMaximumFractionDigits(3);
			bwtr.write("\tFramenumber\tTrackID\tCenterofMassX\tCenterofMassY\tIntensityCherry\tIntensityBio\n");

					while(Snakeiter.hasNext()){
						
						
						SnakeObject currentsnake = Snakeiter.next();
       
				       		
				            bwtr.write("\t" + currentsnake.Framenumber + "\t" + "\t" + id  
				           		 + "\t" +"\t" + nf.format(currentsnake.centreofMass[0]) + "\t" +"\t" 
				        + nf.format(currentsnake.centreofMass[1]) + "\t" +"\t" 
				           		 + nf.format(currentsnake.IntensityCherry) +"\t" +"\t"
				                   		 + nf.format(currentsnake.IntensityBio) + "\n");
				        
				        
				    					
				}
				      
								
							    bwtr.close();
						        fwtr.close();
						    } catch (IOException e) {
						    }	
				
				}
			}

		}

	}
	 
	 public static void writeTracks(String nom, int Framenumber, int Trackid,SnakeObject currentsnake) {
	     NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
	     nf.setMaximumFractionDigits(3);
	     try {
	         File fichier = new File(nom + Trackid + ".txt");
	         FileWriter fw = new FileWriter(fichier);
	         BufferedWriter bw = new BufferedWriter(fw);
	         bw.write("\tFramenumber\tTrackID\tCenterofMassX\tCenterofMassY\tIntensityCherry\tIntensityBio\n");
	       
	        	
	        		
	             bw.write("\t" + Framenumber + "\t" + "\t" + Trackid  
	            		 + "\t" +"\t" + nf.format(currentsnake.centreofMass[0]) + "\t" +"\t" 
	         + nf.format(currentsnake.centreofMass[1]) + "\t" +"\t" 
	            		 + nf.format(currentsnake.IntensityCherry) +"\t" +"\t"
	                    		 + nf.format(currentsnake.IntensityBio) + "\n");
	         
	         
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
		final float log1001 = (float) Math.log10(1001);

		public ThresholdListener(final Label label, final float min, final float max) {
			this.label = label;
			this.min = min;
			this.max = max;
		}

		@Override
		public void adjustmentValueChanged(final AdjustmentEvent event) {
			threshold = min + ((log1001 - (float) Math.log10(1001 - event.getValue())) / log1001) * (max - min);
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
				updatePreview(ValueChange.SLICE);

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
