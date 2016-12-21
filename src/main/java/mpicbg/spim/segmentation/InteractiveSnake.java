package mpicbg.spim.segmentation;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.RoiEncoder;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InteractiveSnake
  implements PlugInFilter
{
  ImagePlus imp;
  ImageStack pile = null;
  ImageStack pile_resultat = null;
  ImageStack pile_seg = null;
  int channel;
  public InteractiveSnake (ImagePlus imp){
	  
	  this.imp = imp;
  }
  
  public InteractiveSnake (ImagePlus imp, final int channel){
	  
	  this.imp = imp;
	  this.channel = channel;
  }
  
  
  int currentSlice = -1;

  int profondeur = 0;
  int largeur = 0;
  int hauteur = 0;
  int nbRois;
  Roi rorig = null;
  Roi processRoi = null;
  Color colorDraw = null;
  SnakeConfigDriver configDriver;
  int ite = 50;

  int step = 1;

  int seuil = 5;

  int DistMAX = Prefs.getInt("ABSnake_DistSearch.int", 100);

  double force = 5.0D;

  double reg = 5.0D;
  double regmin;
  double regmax;
  int slice1;
  int slice2;
  boolean showgrad = false;
  boolean savecoords = false;
  boolean createsegimage = false;
  boolean advanced = false;
  boolean propagate = true;
  boolean movie = false;

  public void run(ImageProcessor ip)
  {
	  
    this.pile = this.imp.getImageStack();
    
    
    this.profondeur = this.pile.getSize();
    this.largeur = this.pile.getWidth();
    this.hauteur = this.pile.getHeight();
    this.slice1 = 1;
    this.slice2 = this.profondeur;
    Calibration cal = this.imp.getCalibration();
    double resXY = cal.pixelWidth;

    boolean dialog = Dialogue();

    RoiManager roimanager = RoiManager.getInstance();
    if (roimanager == null) {
      roimanager = new RoiManager();
      roimanager.setVisible(true);
      this.rorig = this.imp.getRoi();
      if (this.rorig == null)
        IJ.showMessage("Roi required");
      else {
        roimanager.add(this.imp, this.rorig, 0);
      }
    }

    this.nbRois = roimanager.getCount();
    final Roi[] RoisOrig = roimanager.getRoisAsArray();
    final Roi[] RoisCurrent = new Roi[this.nbRois];
    Roi[] RoisResult = new Roi[this.nbRois];
    for (int i = 0; i < this.nbRois; i++) {
      RoisCurrent[i] = RoisOrig[i];
    }

    if (dialog) {
      this.configDriver = new SnakeConfigDriver();
      AdvancedParameters();

      this.regmin = (this.reg / 2.0D);
      this.regmax = this.reg;

      this.pile_resultat = new ImageStack(this.largeur, this.hauteur, ColorModel.getRGBdefault());
      if (this.createsegimage) {
        this.pile_seg = new ImageStack(this.largeur, this.hauteur);
      }

      String label = "" + this.imp.getTitle();
      for (int z = 0; z < this.profondeur; z++) {
        this.pile_resultat.addSlice(label, this.pile.getProcessor(z + 1).duplicate().convertToRGB());
      }

      int nbcpu = 1;
      Thread[] threads = new Thread[nbcpu];
      final AtomicInteger k = new AtomicInteger(0);
      final ABSnake[] snakes = new ABSnake[RoisOrig.length];

      ColorProcessor[] image = new ColorProcessor[RoisOrig.length];
      final ImagePlus[] pluses = new ImagePlus[RoisOrig.length];

      int sens = this.slice1 < this.slice2 ? 1 : -1;
      for (int z = this.slice1; z != this.slice2 + sens; z += sens) {
        final int zz = z;
        k.set(0);

        for (int i = 0; i < RoisOrig.length; i++) {
          image[i] = ((ColorProcessor)(ColorProcessor)this.pile_resultat.getProcessor(zz).duplicate());
          pluses[i] = new ImagePlus("Roi " + i, image[i]);
        }

        for (int t = 0; t < threads.length; t++) {
          threads[t] = new Thread()
          {
            public void run()
            {
              IJ.wait(1000);
              Roi roi = null;

              for (int i = k.getAndIncrement(); i < RoisOrig.length; i = k.getAndIncrement())
              {
                if (InteractiveSnake.this.propagate)
                {
                  roi = RoisCurrent[i];
                }
                else {
                  roi = RoisOrig[i];
                }
                IJ.log("processing slice " + zz + " with roi " + i);

                snakes[i] = InteractiveSnake.this.processSnake(pluses[i], roi, zz, i + 1);
              }

            }

          };
        }

        for (int ithread = 0; ithread < threads.length; ithread++) {
          threads[ithread].setPriority(5);
          threads[ithread].start();
        }
        try
        {
          for (int ithread = 0; ithread < threads.length; ithread++)
            threads[ithread].join();
        }
        catch (InterruptedException ie) {
          throw new RuntimeException(ie);
        }

        ColorProcessor imageDraw = (ColorProcessor)this.pile_resultat.getProcessor(zz).duplicate();
        for (int i = 0; i < RoisOrig.length; i++) {
          snakes[i].DrawSnake(imageDraw, this.colorDraw, 1);
          pluses[i].hide();
          RoisResult[i] = snakes[i].createRoi();
          RoisResult[i].setName("res-" + i);
          RoisCurrent[i] = snakes[i].createRoi();
        }

        this.pile_resultat.setPixels(imageDraw.getPixels(), z);

        if (this.createsegimage) {
          ByteProcessor seg = new ByteProcessor(this.pile_seg.getWidth(), this.pile_seg.getHeight());

          for (int i = 0; i < RoisOrig.length; i++) {
            ByteProcessor tmp = snakes[i].segmentation(seg.getWidth(), seg.getHeight(), i + 1);
            seg.copyBits(tmp, 0, 0, 3);
          }
          seg.resetMinAndMax();
          this.pile_seg.addSlice("Seg " + z, seg);
        }

        if (this.savecoords) {
          for (int i = 0; i < RoisOrig.length; i++) {
            try {
              snakes[i].writeCoordinates("ABSnake-r" + (i + 1) + "-z", zz, resXY);
              RoiEncoder saveRoi = new RoiEncoder("ABSnake-r" + (i + 1) + "-z" + zz + ".roi");
              saveRoi.write(RoisResult[i]);
            } catch (IOException ex) {
              Logger.getLogger(InteractiveSnake.class.getName()).log(Level.SEVERE, null, ex);
            }
          }
        }
      }

      new ImagePlus("Draw", this.pile_resultat).show();
      if (this.createsegimage)
        new ImagePlus("Seg", this.pile_seg).show();
    }
	
  }

  private boolean Dialogue()
  {
    String[] colors = { "Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "Black", "White" };
    int indexcol = 0;

    GenericDialog gd = new GenericDialog("Snake");
    gd.addNumericField("Gradient_threshold:", this.seuil, 0);
    gd.addNumericField("Number_of_iterations:", this.ite, 0);
    gd.addNumericField("Step_result_show:", this.step, 0);

    gd.addCheckbox("Save intermediate images", this.movie);

    if (this.profondeur > 1) {
      gd.addNumericField("First_slice:", this.slice1, 0);
      gd.addNumericField("Last_slice:", this.slice2, 0);
      gd.addCheckbox("Propagate roi", this.propagate);
    }
    gd.addChoice("Draw_color:", colors, colors[indexcol]);
    gd.addCheckbox("Save_coords", this.savecoords);
    gd.addCheckbox("Create_seg_image", this.createsegimage);

    gd.showDialog();

    this.seuil = ((int)gd.getNextNumber());

    this.ite = ((int)gd.getNextNumber());

    this.step = ((int)gd.getNextNumber());

    this.movie = gd.getNextBoolean();

    if (this.step > this.ite - 1) {
      IJ.showStatus("Warning : show step too big\n\t step assignation 1");
      this.step = 1;
    }
    if (this.profondeur > 1) {
      this.slice1 = ((int)gd.getNextNumber());
      this.slice2 = ((int)gd.getNextNumber());
      this.propagate = gd.getNextBoolean();
    }

    indexcol = gd.getNextChoiceIndex();
    switch (indexcol) {
    case 0:
      this.colorDraw = Color.red;
      break;
    case 1:
      this.colorDraw = Color.green;
      break;
    case 2:
      this.colorDraw = Color.blue;
      break;
    case 3:
      this.colorDraw = Color.cyan;
      break;
    case 4:
      this.colorDraw = Color.magenta;
      break;
    case 5:
      this.colorDraw = Color.yellow;
      break;
    case 6:
      this.colorDraw = Color.black;
      break;
    case 7:
      this.colorDraw = Color.white;
      break;
    default:
      this.colorDraw = Color.yellow;
    }
    this.savecoords = gd.getNextBoolean();
    this.createsegimage = gd.getNextBoolean();

    return !gd.wasCanceled();
  }

  private void AdvancedParameters()
  {
    this.configDriver.setMaxDisplacement(Prefs.get("ABSnake_DisplMin.double", 0.1D), Prefs.get("ABSnake_DisplMax.double", 2.0D));
    this.configDriver.setInvAlphaD(Prefs.get("ABSnake_InvAlphaMin.double", 0.5D), Prefs.get("ABSnake_InvAlphaMax.double", 2.0D));
    this.configDriver.setReg(Prefs.get("ABSnake_RegMin.double", 0.1D), Prefs.get("ABSnake_RegMax.double", 2.0D));
    this.configDriver.setStep(Prefs.get("ABSnake_MulFactor.double", 0.99D));
  }

  public ABSnake processSnake(ImagePlus plus, Roi roi, int numSlice, int numRoi)
  {
    this.processRoi = roi;

    ABSnake snake = new ABSnake();
    snake.Init(this.processRoi);
    snake.setOriginalImage(this.pile.getProcessor(numSlice));

    IJ.showStatus("Calculating snake...");

    if (this.step > 0) {
      plus.show();
    }

    double InvAlphaD = this.configDriver.getInvAlphaD(false);

    double regMax = this.configDriver.getReg(false);
    double regMin = this.configDriver.getReg(true);
    double DisplMax = this.configDriver.getMaxDisplacement(false);

    double mul = this.configDriver.getStep();

    SnakeConfig config = new SnakeConfig(this.seuil, DisplMax, this.DistMAX, regMin, regMax, 1.0D / InvAlphaD);
    snake.setConfig(config);

    snake.computeGrad(this.pile.getProcessor(numSlice));

    IJ.resetEscape();
    FileSaver fs = new FileSaver(plus);

    double dist0 = 0.0D;

    for (int i = 0; (i < this.ite) && 
      (!IJ.escapePressed()); i++)
    {
      double dist = snake.process();
      if ((dist >= dist0) && (dist < this.force))
      {
        snake.computeGrad(this.pile.getProcessor(numSlice));
        config.update(mul);
      }
      dist0 = dist;

      if ((this.step > 0) && (i % this.step == 0)) {
        IJ.showStatus("Show intermediate result (iteration n" + (i + 1) + ")");
        ColorProcessor image2 = (ColorProcessor)this.pile_resultat.getProcessor(numSlice).duplicate();

        snake.DrawSnake(image2, this.colorDraw, 1);
        plus.setProcessor("", image2);
        plus.setTitle(this.imp.getTitle() + " roi " + numRoi + " (iteration n" + (i + 1) + ")");
        plus.updateAndRepaintWindow();
        if (this.movie) {
          fs = new FileSaver(plus);
          fs.saveAsTiff("ABsnake-t" + i + "-r" + numRoi + "-z" + numSlice + ".tif");
        }

      }

    }

    return snake;
  }

  
  
  
  public int setup(String arg, ImagePlus imp)
  {
    this.imp = imp;
    return 141;
  }
}