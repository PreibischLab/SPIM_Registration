
package mpicbg.spim.segmentation;




 
  /*
   *  Active Contours ( "Snakes")
   *  Initialization by a rectangle, oval or closed area
   *  Deformation of the snake towards the nearest edges along the
   *  normals, edges computed by a deriche filter and above the threshold
   *  value.
   *  Regularization of the shape by constraing the points to form a
   *  smooth curve (depending on the value of regularization)
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
  public class InteractiveSnake implements PlugInFilter {
      // Sauvegarde de la fenetre d'image :

      ImagePlus imp;
      ImagePlus Intensityimp;
      
      ImageStack pile = null;
      ImageStack Intensitypile = null;
      ImageStack pile_resultat = null;
      ImageStack Intensitypile_resultat = null;
      ImageStack pile_seg = null;
      int currentSlice = -1;
      // Dimensions of the stck :
      int slicesize = 0;
      int length = 0;
      int height= 0;
      // ROI original
      int nbRois;
      Roi rorig = null;
      Roi processRoi = null;
      Color colorDraw = Color.RED;
     ArrayList<SnakeObject> snakeList;
     
      int channel;
      int Frame;
      

   
      public InteractiveSnake (ImagePlus imp, ImagePlus Intensityimp, final int Frame){
    	  
    	  this.imp = imp;
    	  this.Intensityimp = Intensityimp;
    	  this.Frame = Frame;
      }
      
      public InteractiveSnake (ImagePlus imp, ImagePlus Intensityimp, final int channel, final int Frame){
    	  
    	  this.imp = imp;
    	  this.Intensityimp = Intensityimp;
    	  this.channel = channel;
    	  this.Frame = Frame;
      }
      
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
      
      double Displacement_min =  0.1;
      double Displacement_max = 2.0;
      double Threshold_dist_positive =  100;
     double Threshold_dist_negative =  100;
     double Inv_alpha_min = 0.2;
      double Inv_alpha_max =  10.0;
      double Reg_min = 1;
      double Reg_max =  5;
      double Mul_factor = 0.99;
      
      
      
      // maximum displacement
      double force = 10;
      // regularization factors, min and max
      double reg = 5;
      double regmin, regmax;
      // first and last slice to process
      int slice1, slice2;
      // misc options
      boolean showgrad = false;
      boolean createsegimage = false;
      boolean advanced = false;
      boolean propagate = true;
      boolean Auto = false;
      boolean movie = false;
      boolean saverois = false;
      boolean saveIntensity = true;
      boolean useroinames = false;
      boolean nosizelessrois = true;
      boolean differentfolder=false;
      String usefolder = IJ.getDirectory("imagej");
      String addToName = "";
      // String[] RoisNames;

      /**
       * Main processing method for the Snake_deriche_ object
       *
       * @param ip image
       */
      public void run(ImageProcessor ip) {
    	  
    	  
    	  configDriver = new SnakeConfigDriver();
          AdvancedParameters();
    	
          // original stack
    	  
    	  snakeList = new ArrayList<SnakeObject>();
    	 
          pile = imp.getStack();
          Intensitypile = Intensityimp.getStack();
          // sizes of the stack
          slicesize = imp.getNSlices();
          height= imp.getHeight();
      	length = imp.getWidth();
          slice1 = 1;
          slice2 = slicesize;
          Calibration cal = imp.getCalibration();
          double resXY = cal.pixelWidth;
            double Inten =   cal.pixelDepth;
          
            
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
             
              pile_resultat = new ImageStack(length, height, java.awt.image.ColorModel.getRGBdefault());
              Intensitypile_resultat = new ImageStack(length, height, java.awt.image.ColorModel.getRGBdefault());
              if (createsegimage) {
                  pile_seg = new ImageStack(length, height);
              }
             
              // update of the display
              String label = "" + imp.getTitle();
              for (int z = 0; z < slicesize; z++) {
                  pile_resultat.addSlice(label, pile.getProcessor(z + 1).duplicate().convertToRGB());
            	  Intensitypile_resultat.addSlice(label, Intensitypile.getProcessor(z + 1).duplicate().convertToRGB());

              }
             

              //display sices in RGB color
              ColorProcessor image;
              ImagePlus plus;

              Roi roi;
              ABSnake snake;
              ByteProcessor seg = null;
              int sens = slice1 < slice2 ? 1 : -1;
              for (int z = slice1; z != (slice2 + sens); z += sens) {
                  ColorProcessor imageDraw = (ColorProcessor) (pile_resultat.getProcessor(z).duplicate());
                  ColorProcessor IntensityimageDraw = (ColorProcessor) (Intensitypile_resultat.getProcessor(z).duplicate());
                  image = (ColorProcessor) (pile_resultat.getProcessor(z).duplicate());
                  plus = new ImagePlus("Running snakes in current Slice " , image);
                 
                  for (int i = 0; i < RoisOrig.length; i++) {
                      if (createsegimage) {
                          seg = new ByteProcessor(pile_seg.getWidth(), pile_seg.getHeight());
                      }
                      if (propagate) {
                          roi = RoisCurrent[i];
                      } else {
                          roi = RoisOrig[i];
                      }
                      IJ.log("processing Frame no. " + Frame + " with roi " + i);
                      IJ.selectWindow("Log");
                     
                	  IJ.saveAs("Text", usefolder + "//" + "Logsnakerun.txt");
                      snake = processSnake(plus, roi, z, i + 1);
                      snake.killImages();

                      snake.DrawSnake(imageDraw, colorDraw, 1);
                      snake.DrawSnake(IntensityimageDraw, colorDraw, 1);
                      RoisResult[i] = snake.createRoi();
                      RoisResult[i].setName("res-" + i);
                      RoisCurrent[i] = snake.createRoi();

                      pile_resultat.setPixels(imageDraw.getPixels(), z);
                      Intensitypile_resultat.setPixels(IntensityimageDraw.getPixels(), z);
                      if (createsegimage) {
                          seg.copyBits(snake.segmentation(seg.getWidth(), seg.getHeight(), i + 1), 0, 0, Blitter.ADD);
                          seg.resetMinAndMax();
                          pile_seg.addSlice("Seg " + z, seg);
                      } // segmentation

                     
                      
                      ColorProcessor imagep = (ColorProcessor) (pile_resultat.getProcessor(z).duplicate());
                      ColorProcessor Intensityimagep = (ColorProcessor) (Intensitypile_resultat.getProcessor(z).duplicate());
                      if (RoisResult[i]!=null){
                      double IntensityBio = getIntensity(Intensityimagep, RoisResult[i]);
                      double IntensityCherry = getIntensity(imagep, RoisResult[i]);
                      double[] center = getCentreofMass(imagep, RoisResult[i]);
                      SnakeObject currentsnake = new SnakeObject(Frame, i, RoisResult[i], center, 
                    		  IntensityCherry, IntensityBio);
                      snakeList.add(currentsnake);
                      }
                  }
                  plus.hide();
              }

              
              if (createsegimage) {
                  new ImagePlus("Seg", pile_seg).show();
              }
       
          System.gc();
      }
      
     

	public ImageStack getResult(){
    	  
    	  return pile_resultat;
      }
      
      public ArrayList<SnakeObject> getRoiList(){
    	  
    	  return snakeList;
      }
      
    
      

      /**
       * Dialog
       *
       * @return dialog ok ?
       */
      private boolean Dialogue() {
          // array of colors
          String[] colors = {"Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "Black", "White"};
          int indexcol = 0;
          // create dialog
          GenericDialog gd = new GenericDialog("Snake");
          gd.addNumericField("Gradient_threshold:", Gradthresh, 0);
          gd.addNumericField("Number_of_iterations:", ite, 0);
          gd.addNumericField("Step_result_show:", step, 0);
          //if (stacksize == 1) {
          gd.addCheckbox("Save intermediate images", movie);
          //}
          if (slicesize > 1) {
              gd.addNumericField("First_slice:", slice1, 0);
              gd.addNumericField("Last_slice:", slice2, 0);
              gd.addCheckbox("Propagate roi", propagate);
          }
          gd.addChoice("Draw_color:", colors, colors[indexcol]);
          gd.addCheckbox("Create_seg_image:", createsegimage);
          gd.addCheckbox("Save_rois:", saverois);
          gd.addCheckbox("Save_RoiIntensities:", saveIntensity);
          //gd.addCheckbox("Use_roi_names:", useroinames);
          gd.addCheckbox("No_sizeless_rois:", nosizelessrois);
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
          //if (stacksize == 1) {
          movie = gd.getNextBoolean();
          //}
          if (step > ite - 1) {
              IJ.showStatus("Warning : show step too big\n\t step assignation 1");
              step = 1;
          }
          if (slicesize > 1) {
              slice1 = (int) gd.getNextNumber();
              slice2 = (int) gd.getNextNumber();
              propagate = gd.getNextBoolean();
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
          //useroinames=gd.getNextBoolean();
          nosizelessrois = gd.getNextBoolean();
          differentfolder=gd.getNextBoolean();
          //Vector<?> stringFields=gd.getStringFields();
          //usefolder=((TextField) stringFields.get(0)).getText();
          usefolder = gd.getNextString();
          advanced = gd.getNextBoolean();

          return !gd.wasCanceled();
      }

      
      
      
      /**
       * Dialog advanced
       *
       * @return dialog ok ?
       */
      
      private boolean AdvancedDialog(){
    	  
    		 // dialog
	        GenericDialog gd = new GenericDialog("Snake Advanced");
	        gd.addNumericField("Distance_Search", DistMAX, 0);
	        gd.addNumericField("Displacement_min",   Displacement_min, 2);
	        gd.addNumericField("Displacement_max", Displacement_max, 2);
	        gd.addNumericField("Threshold_dist_positive",Threshold_dist_positive, 0);
	        gd.addNumericField("Threshold_dist_negative", Threshold_dist_negative, 0);
	        gd.addNumericField("Inv_alpha_min", Inv_alpha_min, 2);
	        gd.addNumericField("Inv_alpha_max", Inv_alpha_max, 2);
	        gd.addNumericField("Reg_min", Reg_min, 2);
	        gd.addNumericField("Reg_max", Reg_max, 2);
	        gd.addNumericField("Mul_factor", Mul_factor, 4);
	        // show dialog
	        gd.showDialog();

	      
	        DistMAX =  (int) gd.getNextNumber();
	        Displacement_min =  gd.getNextNumber();
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
          configDriver.setMaxDisplacement(Displacement_min,Displacement_max);
          configDriver.setInvAlphaD(Inv_alpha_min, Inv_alpha_max);
          configDriver.setReg(Reg_min, Reg_max);
          configDriver.setStep(Mul_factor);
      }

      /**
       * do the snake algorithm on all images
       *
       * @param image RGB image to display the snake
       * @param numSlice which image of the stack
       */
      public ABSnake processSnake(ImagePlus plus, Roi roi, int numSlice, int numRoi) {
         
          int i;
          
          SnakeConfig config;

         
          processRoi = roi;

          // initialisation of the snake
          ABSnake snake = new ABSnake();
          snake.Init(processRoi);
          snake.setOriginalImage(pile.getProcessor(numSlice));

          // start of computation
          IJ.showStatus("Calculating snake...");
          

          if (step > 0) {
              plus.show();
          }

          double InvAlphaD = configDriver.getInvAlphaD(false);
          double regMax = configDriver.getReg(false);
          double regMin = configDriver.getReg(true);
          double DisplMax = configDriver.getMaxDisplacement(false);
          double mul = configDriver.getStep();

          config = new SnakeConfig(Gradthresh, DisplMax, DistMAX, regMin, regMax, 1.0 / InvAlphaD);
          snake.setConfig(config);
          // compute image gradient
          snake.computeGrad(pile.getProcessor(numSlice));

          IJ.resetEscape();
          FileSaver fs = new FileSaver(plus);

          double dist0 = 0.0;
          double dist;
       
         
          for (i = 0; i < ite; i++) {
              if (IJ.escapePressed()) {
                  break;
              }
              // each iteration
              dist = snake.process();
              
              if ((dist >= dist0) && (dist < force)) {
                  //System.out.println("update " + config.getAlpha());
                  snake.computeGrad(pile.getProcessor(numSlice));
                  config.update(mul);
              }
              dist0 = dist;

              
              // display of the snake
              if ((step > 0) && ((i % step) == 0)) {
                  IJ.showStatus("Show intermediate result (iteration n" + (i + 1) + ")");
           
                  if (movie) {
                      fs = new FileSaver(plus);
                      fs.saveAsTiff(usefolder + "//" + addToName + "ABsnake-r" + numRoi + "-t" + i + "-z" + numSlice + ".tif");
                  }
                
              }
          }
          
          
       
        
          snake.setOriginalImage(null);

          return snake;
      }

      
      public double getIntensity(ImageProcessor ip, Roi roi){
    	  
    	  double Intensity = 0;
    	  
    	 
			//ImageProcessor mask = roi.getMask();
			Rectangle r = roi.getBounds();
			
			
			
			for (int y=0; y<r.height; y++) {
				for (int x=0; x<r.width; x++) {
					//if (mask.getPixel(x,y)!=0) {
                if (roi.contains(x + r.x, y + r.y)){
						Intensity += ip.getPixelValue(x+r.x, y+r.y);
					
					}
				}
			}
    		
    	  return Intensity;
    	  
      }
      
 public double[] getCentreofMass(ImageProcessor ip, Roi roi){
    	  
    	  double Intensity = 0;
    	  double SumX = 0;
    	  double SumY = 0;
    	 double[] center = new double[ 2 ];
		//	ImageProcessor mask = roi.getMask();
			Rectangle r = roi.getBounds();
			
			
			for (int y=0; y<r.height; y++) {
				for (int x=0; x<r.width; x++) {
					//if (mask.getPixel(x,y)!=0) {
						if (roi.contains(x + r.x, y + r.y)){

						Intensity += ip.getPixelValue(x+r.x, y+r.y);
						SumX += (x + r.x) * ip.getPixelValue(x+r.x, y+r.y);
						SumY += (y + r.y) * ip.getPixelValue(x+r.x, y+r.y);
						
					}
				}
			}
			center[ 0 ] = SumX / Intensity;
			center[ 1 ] = SumY / Intensity;
			
    	  return center;
    	  
      }

      /**
       * setup
       *
       * @param arg arguments
       * @param imp image plus
       * @return setup
       */
      public int setup(String arg, ImagePlus imp) {
          this.imp = imp;
          return DOES_8G + DOES_16 + DOES_32 + NO_CHANGES;
      }

	
	
  
  }
