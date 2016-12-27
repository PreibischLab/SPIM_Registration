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
import spim.fiji.plugin.Max_Project;

import java.awt.*;
  import java.io.IOException;
  import java.util.concurrent.atomic.AtomicInteger;
  import java.util.logging.Level;
  import java.util.logging.Logger;

  /**
   * ABSnake_ plugin interface
   *
   * @author thomas.boudier@snv.jussieu.fr and Philippe Andrey
   * @created 26 aout 2003
   */
  public class InteractiveSnake implements PlugInFilter {
      // Sauvegarde de la fenetre d'image :

      ImagePlus imp;
      // Sauvegarde de la pile de debut et de resultat :
      ImageStack pile = null;
      ImageStack pile_resultat = null;
      ImageStack pile_seg = null;
      int currentSlice = -1;
      // Dimensions of the stck :
      int stacksize = 0;
      int length = 0;
      int height= 0;
      // ROI original
      int nbRois;
      Roi rorig = null;
      Roi processRoi = null;
      Color colorDraw = null;
     
      int channel;
      
 public InteractiveSnake (){
    	  
    	  
      }
      
      public InteractiveSnake (ImagePlus imp){
    	  
    	  this.imp = imp;
      }
      
      public InteractiveSnake (ImagePlus imp, final int channel){
    	  
    	  this.imp = imp;
    	  this.channel = channel;
      }
      
      
      /**
       * Parametres of Snake :
       */
      SnakeConfigDriver configDriver;
      // number of iterations
      int ite = 200;
      // step to display snake
      int step = 100;
      // threshold of edges
      int seuil = 10;
      // how far to look for edges
      int DistMAX = Prefs.getInt("ABSnake_DistSearch.int", 20);
      // maximum displacement
      double force = 5.0;
      // regularization factors, min and max
      double reg = 5.0;
      double regmin, regmax;
      // first and last slice to process
      int slice1, slice2;
      // misc options
      boolean showgrad = false;
      boolean savecoords = false;
      boolean createsegimage = false;
      boolean advanced = false;
      boolean propagate = true;
      boolean movie = false;
      boolean saveiterrois = false;
      boolean saveIntensity = true;
      boolean useroinames = false;
      boolean nosizelessrois = false;
      //boolean differentfolder=false;
      String usefolder = IJ.getDirectory("imagej");
      String addToName = "";
      // String[] RoisNames;

      /**
       * Main processing method for the Snake_deriche_ object
       *
       * @param ip image
       */
      public void run(ImageProcessor ip) {
          // original stack
    	  
    	  
          pile = imp.getStack();
          // sizes of the stack
          stacksize = pile.getSize();
          length = pile.getWidth();
          height= pile.getHeight();
          slice1 = 1;
          slice2 = stacksize;
          Calibration cal = imp.getCalibration();
          double resXY = cal.pixelWidth;
            double Inten =   cal.pixelDepth;
          boolean dialog = Dialogue();

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

          //RoisNames=new String[nbRois];
          //for (int i = 0; i < nbRois; i++) {
          //RoisNames[i]=roimanager.getName(i);
          //}
          if (dialog) {
              configDriver = new SnakeConfigDriver();
              AdvancedParameters();
              // ?
              regmin = reg / 2.0;
              regmax = reg;
              // ?
              // init result
              pile_resultat = new ImageStack(length, height, java.awt.image.ColorModel.getRGBdefault());
              if (createsegimage) {
                  pile_seg = new ImageStack(length, height);
              }
             
              // update of the display
              String label = "" + imp.getTitle();
              for (int z = 0; z < stacksize; z++) {
                  pile_resultat.addSlice(label, pile.getProcessor(z + 1).duplicate().convertToRGB());
              }
            
              int nbcpu = 1;
              Thread[] threads = new Thread[nbcpu];
              AtomicInteger k = new AtomicInteger(0);
              ABSnake[] snakes = new ABSnake[RoisOrig.length];

              //display sices in RGB color
              ColorProcessor image;
              ImagePlus plus;

              // NEW LOOP 15/12/2015
              Roi roi;
              ABSnake snake;
              RoiEncoder saveRoi;
              ByteProcessor seg = null;
              int sens = slice1 < slice2 ? 1 : -1;
              for (int z = slice1; z != (slice2 + sens); z += sens) {
                  ColorProcessor imageDraw = (ColorProcessor) (pile_resultat.getProcessor(z).duplicate());
                  image = (ColorProcessor) (pile_resultat.getProcessor(z).duplicate());
                  plus = new ImagePlus("Roi " + z, image);
                  for (int i = 0; i < RoisOrig.length; i++) {
                      if (createsegimage) {
                          seg = new ByteProcessor(pile_seg.getWidth(), pile_seg.getHeight());
                      }
                      if (propagate) {
                          // imp.setRoi(RoisCurrent[i]);
                          roi = RoisCurrent[i];
                      } else {
                          // imp.setRoi(RoisOrig[i]);
                          roi = RoisOrig[i];
                      }
                      IJ.log("processing slice " + z + " with roi " + i);
                      snake = processSnake(plus, roi, z, i + 1);
                      snake.killImages();

                      snake.DrawSnake(imageDraw, colorDraw, 1);
                      //pluses[i].hide();
                      RoisResult[i] = snake.createRoi();
                      RoisResult[i].setName("res-" + i);
                      RoisCurrent[i] = snake.createRoi();

                      pile_resultat.setPixels(imageDraw.getPixels(), z);

                      if (createsegimage) {
                          seg.copyBits(snake.segmentation(seg.getWidth(), seg.getHeight(), i + 1), 0, 0, Blitter.ADD);
                          seg.resetMinAndMax();
                          pile_seg.addSlice("Seg " + z, seg);
                      } // segmentation

                      if (savecoords) {
                          snake.writeCoordinates(usefolder + "//" + "ABSnake-r" + (i + 1) + "-z", z, resXY);
                          if (nosizelessrois == false || (nosizelessrois == true && RoisResult[i].getFloatWidth() > 2 && RoisResult[i].getFloatHeight() > 2)) {
                              try {
                                  saveRoi = new RoiEncoder(usefolder + "//" + "ABSnake-r" + (i + 1) + "-z" + z + ".roi");
                                  saveRoi.write(RoisResult[i]);
                              } catch (IOException ex) {
                                  Logger.getLogger(InteractiveSnake.class.getName()).log(Level.SEVERE, null, ex);
                              }
                          }
                      } // save coord
                      
                      
                      ColorProcessor imagep = (ColorProcessor) (pile_resultat.getProcessor(z).duplicate());
                      double IntensityRoi = getIntensity(imagep, RoisResult[i]);
                      
                      if (saveIntensity){
                      	snake.writeIntensities(usefolder + "//" + "RoiIntensitySecframe" + (i + 1) + "-z", z, resXY, Inten,  IntensityRoi);
                      	
                      	
                      }
                      
                      
                  }
              }


            //  new ImagePlus("Snake Result" , pile_resultat).show();
              if (createsegimage) {
                  new ImagePlus("Seg", pile_seg).show();
              }
          }// dialog
          System.gc();
      }
      
      public ImageStack getResult(){
    	  
    	  return pile_resultat;
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
          gd.addNumericField("Gradient_threshold:", seuil, 0);
          gd.addNumericField("Number_of_iterations:", ite, 0);
          gd.addNumericField("Step_result_show:", step, 0);
          //if (stacksize == 1) {
          gd.addCheckbox("Save intermediate images", movie);
          //}
          if (stacksize > 1) {
              gd.addNumericField("First_slice:", slice1, 0);
              gd.addNumericField("Last_slice:", slice2, 0);
              gd.addCheckbox("Propagate roi", propagate);
          }
          gd.addChoice("Draw_color:", colors, colors[indexcol]);
          gd.addCheckbox("Save_coords:", savecoords);
          gd.addCheckbox("Create_seg_image:", createsegimage);
          gd.addCheckbox("Save_iteration_rois:", saveiterrois);
          gd.addCheckbox("Save_RoiIntensities:", saveIntensity);
          //gd.addCheckbox("Use_roi_names:", useroinames);
          gd.addCheckbox("No_sizeless_rois:", nosizelessrois);
          //gd.addCheckbox("Use_different_folder", differentfolder);
          gd.addStringField("Use_folder:", usefolder);
          //gd.addCheckbox("Advanced_options", advanced);
          // show dialog
          gd.showDialog();

          // threshold of edge
          seuil = (int) gd.getNextNumber();

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
          if (stacksize > 1) {
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
          savecoords = gd.getNextBoolean();
          createsegimage = gd.getNextBoolean();
          saveiterrois = gd.getNextBoolean();
          saveIntensity = gd.getNextBoolean();
          //useroinames=gd.getNextBoolean();
          nosizelessrois = gd.getNextBoolean();
          //differentfolder=gd.getNextBoolean();
          //Vector<?> stringFields=gd.getStringFields();
          //usefolder=((TextField) stringFields.get(0)).getText();
          usefolder = gd.getNextString();
          //advanced = gd.getNextBoolean();

          return !gd.wasCanceled();
      }

      /**
       * Dialog advanced
       *
       * @return dialog ok ?
       */
      private void AdvancedParameters() {
          // see advanced dialog class
          configDriver.setMaxDisplacement(Prefs.get("ABSnake_DisplMin.double", 0.1), Prefs.get("ABSnake_DisplMax.double", 2.0));
          configDriver.setInvAlphaD(Prefs.get("ABSnake_InvAlphaMin.double", 0.5), Prefs.get("ABSnake_InvAlphaMax.double", 2.0));
          configDriver.setReg(Prefs.get("ABSnake_RegMin.double", 0.1), Prefs.get("ABSnake_RegMax.double", 2.0));
          configDriver.setStep(Prefs.get("ABSnake_MulFactor.double", 0.99));
      }

      /**
       * do the snake algorithm on all images
       *
       * @param image RGB image to display the snake
       * @param numSlice which image of the stack
       */
      public ABSnake processSnake(ImagePlus plus, Roi roi, int numSlice, int numRoi) {
          //int x;
          //int y;
          //int max;
          //int min;
          //int p;
          int i;
          //int NbPoints;
          //double cou;
          //double cour;
          //double maxC;
          //double scale;
          SnakeConfig config;

          //IJ.log("process " + numRoi + " " + numSlice + " " + plus + " " + roi);
          // processRoi = imp.getRoi();
          processRoi = roi;

          // initialisation of the snake
          ABSnake snake = new ABSnake();
          snake.Init(processRoi);
          snake.setOriginalImage(pile.getProcessor(numSlice));

          // start of computation
          IJ.showStatus("Calculating snake...");
          //ColorProcessor image2;
          //image2 = (ColorProcessor) image.duplicate();
          // image2 = (ColorProcessor) (pile_resultat.getProcessor(numSlice).duplicate());

          //ImagePlus windowsTemp = new ImagePlus("Iteration", image2);
          if (step > 0) {
              plus.show();
          }

          double InvAlphaD = configDriver.getInvAlphaD(false);
          //double InvAlphaDMin = configDriver.getInvAlphaD(true);
          double regMax = configDriver.getReg(false);
          double regMin = configDriver.getReg(true);
          double DisplMax = configDriver.getMaxDisplacement(false);
          //double DisplMin = configDriver.getMaxDisplacement(true);
          double mul = configDriver.getStep();

          config = new SnakeConfig(seuil, DisplMax, DistMAX, regMin, regMax, 1.0 / InvAlphaD);
          snake.setConfig(config);
          // compute image gradient
          snake.computeGrad(pile.getProcessor(numSlice));

          IJ.resetEscape();
          FileSaver fs = new FileSaver(plus);

          double dist0 = 0.0;
          double dist;
          //double InvAlphaD0 = InvAlphaD;

          //if(useroinames==true){
          //addToName=RoisNames[numRoi];    
          //IJ.log("Selected to use names, has name: "+addToName);
          //}else{
          //  IJ.log("Not selected to use names");
          //}
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
                  ColorProcessor image2 = (ColorProcessor) (pile_resultat.getProcessor(numSlice).duplicate());

                  snake.DrawSnake(image2, colorDraw, 1);
                  plus.setProcessor("", image2);
                  plus.setTitle(imp.getTitle() + " roi " + numRoi + " (iteration n" + (i + 1) + ")");
                  plus.updateAndRepaintWindow();
                  if (movie) {
                      fs = new FileSaver(plus);
                      fs.saveAsTiff(usefolder + "//" + addToName + "ABsnake-r" + numRoi + "-t" + i + "-z" + numSlice + ".tif");
                  }
                  RoiEncoder saveRoi;
                  if (saveiterrois) {
                      try {
                          Roi roiToSave = snake.createRoi();
                          if (nosizelessrois == false || (nosizelessrois == true && roiToSave.getFloatWidth() > 2 && roiToSave.getFloatHeight() > 2)) {
                              saveRoi = new RoiEncoder(usefolder + "//" + addToName + "ABsnake-r" + numRoi + "-t" + i + "-z" + numSlice + ".roi");
                              saveRoi.write(roiToSave);
                          }
                      } catch (IOException ex) {
                          Logger.getLogger(InteractiveSnake.class.getName()).log(Level.SEVERE, null, ex);
                      }

                  }
              }
          }
          
          
       
        
        
          // end iteration

          // close temp window    
          //plus.hide();

          /*
           // draw
           snake.DrawSnake(image, colorDraw, 1);
           ByteProcessor segImage;
           if (createsegimage) {
           segImage = snake.segmentation(image.getWidth(), image.getHeight(), numRoi);
           segImage.setMinAndMax(0, nbRois);
           if (currentSlice != numSlice) {
           pile_seg.addSlice("" + numSlice, segImage);
           currentSlice = numSlice;
           } else {
           (pile_seg.getProcessor(currentSlice)).copyBits(segImage, 0, 0, Blitter.ADD);
           }
           }
           if (savecoords) {
           snake.writeCoordinates("ABsnake-" + numRoi + "-", numSlice);
           }
           * 
           */
          //processRoi = new PolygonRoi(0, 0, imp);
          //processRoi = snake.createRoi(imp);
          // snake.kill();
          //imp.killRoi();
          //imp.setRoi(processRoi);
          snake.setOriginalImage(null);

          return snake;
      }

      
      public double getIntensity(ImageProcessor ip, Roi roi){
    	  
    	  double Intensity = 0;
    	  
    	 
			ImageProcessor mask = roi.getMask();
			Rectangle r = roi.getBounds();
			
			
			
			for (int y=0; y<r.height; y++) {
				for (int x=0; x<r.width; x++) {
					if (mask==null||mask.getPixel(x,y)!=0) {

						Intensity += ip.getPixelValue(x+r.x, y+r.y);
					
					}
				}
			}
    		
    	  return Intensity;
    	  
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