package mpicbg.spim.segmentation;

import ij.IJ;
import ij.Prefs;
import ij.gui.Line;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

public class ABSnake
{
  Point2d[] points;
  Point2d[] normale;
  Point2d[] deplace;
  double dataDistance;
  double[] lambda;
  int[] etat;
  int NPT;
  int NMAX = 50000;
  int block;
  int elimination;
  int ARRET;
  boolean closed;
  SnakeConfig config;
  ImageProcessor gradImage;
  ImageProcessor originalImage;

  public void kill()
  {
    this.points = null;
    this.normale = null;
    this.deplace = null;
    this.lambda = null;
    this.etat = null;
    System.gc();
  }

  public void setConfig(SnakeConfig sc)
  {
    this.config = sc;
  }

  public int getNbPoints()
  {
    return this.NPT;
  }

  public Point2d getPoint(int i)
  {
    return this.points[i];
  }

  public Point2d[] getPoints()
  {
    return this.points;
  }

  public SnakeConfig getConfig()
  {
    return this.config;
  }

  public double[] getLambda()
  {
    return this.lambda;
  }

  public Point2d[] getDisplacement()
  {
    return this.deplace;
  }

  public boolean closed()
  {
    return this.closed;
  }

  public void setOriginalImage(ImageProcessor originalImage) {
    this.originalImage = originalImage;
  }

  public void DrawSnake(ImageProcessor A, Color col, int linewidth)
  {
    A.setColor(col);
    A.setLineWidth(linewidth);
    for (int i = 0; i < this.NPT - 1; i++) {
      int x = (int)this.points[i].x;
      int y = (int)this.points[i].y;
      int xx = (int)this.points[(i + 1)].x;
      int yy = (int)this.points[(i + 1)].y;
      A.drawLine(x, y, xx, yy);
    }
    if (closed()) {
      int x = (int)this.points[(this.NPT - 1)].x;
      int y = (int)this.points[(this.NPT - 1)].y;
      int xx = (int)this.points[0].x;
      int yy = (int)this.points[0].y;
      A.drawLine(x, y, xx, yy);
    }
  }

  public void EcritFreeD(int nb)
  {
    try
    {
      File fichier = new File("freed" + (nb + 1) + ".txt");
      FileWriter fw = new FileWriter(fichier);
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write("MODELITEM cervo \"section " + (nb + 1) + "\";");
      bw.write("\nMODELITEMDATA  \n");
      for (int i = 0; i < this.NPT; i++) {
        bw.write("" + (int)this.points[i].x + "," + (int)this.points[i].y + "  \n");
      }
      bw.write(";\n");
      bw.close();
      fw.close();
    }
    catch (IOException e)
    {
    }
  }

  public void writeCoordinates(String nom, int nb, double resXY)
  {
    NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
    nf.setMaximumFractionDigits(3);
    try {
      File fichier = new File(nom + nb + ".txt");
      FileWriter fw = new FileWriter(fichier);
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write("nb\tX\tY\tZ\tXcal\tYcal\n");
      for (int i = 0; i < this.NPT; i++) {
        bw.write(i + "\t" + nf.format(this.points[i].x) + "\t" + nf.format(this.points[i].y) + "\t" + nb + "\t" + nf.format(this.points[i].x * resXY) + "\t" + nf.format(this.points[i].y) + "\n");
      }
      bw.close();
      fw.close();
    }
    catch (IOException e)
    {
    }
  }

  PolygonRoi createRoi()
  {
    int[] xx = new int[this.NPT];
    int[] yy = new int[this.NPT];
    for (int i = 0; i < this.NPT; i++) {
      xx[i] = ((int)this.points[i].x);
      yy[i] = ((int)this.points[i].y);
    }
    PolygonRoi rr = new PolygonRoi(xx, yy, this.NPT - 1, 3);
    return rr;
  }

  public void Init(Roi R)
  {
    int i = 1;

    this.NPT = 0;

    this.points = new Point2d[this.NMAX];
    this.normale = new Point2d[this.NMAX];
    this.deplace = new Point2d[this.NMAX];
    this.dataDistance = 0.0D;
    this.etat = new int[this.NMAX];
    this.lambda = new double[this.NMAX];

    for (i = 0; i < this.NMAX; i++) {
      this.points[i] = new Point2d();
      this.normale[i] = new Point2d();
      this.deplace[i] = new Point2d();
    }

    if ((R.getType() == 1) || (R.getType() == 0)) {
      this.closed = true;
      Rectangle Rect = R.getBounds();
      int xc = Rect.x + Rect.width / 2;
      int yc = Rect.y + Rect.height / 2;
      double Rx = Rect.width / 2.0D;
      double Ry = Rect.height / 2.0D;
      double theta = 4.0D / (Rx + Ry);
      i = 0;
      for (double a = 6.283185307179586D; a > 0.0D; a -= theta) {
        this.points[i].x = ((int)(xc + Rx * Math.cos(a)));
        this.points[i].y = ((int)(yc + Ry * Math.sin(a)));
        this.etat[i] = 0;
        i++;
      }
      this.NPT = i;
    } else if (R.getType() == 5) {
      this.closed = false;
      Line l = (Line)R;
      double Rx = l.x2 - l.x1;
      double Ry = l.y2 - l.y1;
      double a = Math.sqrt(Rx * Rx + Ry * Ry);
      Rx /= a;
      Ry /= a;
      int ind = 0;
      for (i = 0; i <= l.getLength(); i++) {
        this.points[ind].x = (l.x1 + Rx * i);
        this.points[ind].y = (l.y1 + Ry * i);
        this.etat[ind] = 0;
        ind++;
      }
      this.NPT = ind;
    } else if ((R.getType() == 3) || (R.getType() == 2)) {
      this.closed = true;
      PolygonRoi p = (PolygonRoi)R;
      Rectangle rectBound = p.getBounds();
      int NBPT = p.getNCoordinates();
      int[] pointsX = p.getXCoordinates();
      int[] pointsY = p.getYCoordinates();
      for (i = 0; i < NBPT; i++) {
        this.points[i].x = (pointsX[i] + rectBound.x);
        this.points[i].y = (pointsY[i] + rectBound.y);
      }

      this.NPT = NBPT;
      if (R.getType() == 2)
        resample(true);
    }
    else {
      IJ.showStatus("Selection type not supported");
    }
    this.block = 0;
    this.elimination = 0;
    this.ARRET = 0;
  }

  void resample(boolean init)
  {
    Point2d[] temp = new Point2d[this.NMAX];
    Point2d Ta = new Point2d();

    double Dtot = 0.0D;
    double Dmin = 1000.0D;
    double Dmax = 0.0D;
    for (int i = 1; i < this.NPT; i++) {
      double Di = distance(i, i - 1);
      Dtot += Di;
      if (Di < Dmin) {
        Dmin = Di;
      }
      if (Di > Dmax) {
        Dmax = Di;
      }
    }
    if ((Dmax / Dmin > 3.0D) || (init)) {
      double Dmoyg = 1.0D;
      temp[0] = new Point2d();
      temp[0].x = this.points[0].x;
      temp[0].y = this.points[0].y;
      int i = 1;
      int ii = 1;
      temp[ii] = new Point2d();
      while (i < this.NPT) {
        double Dmoy = Dmoyg;
        double DD = distance(i, i - 1);
        if (DD > Dmoy) {
          int aj = (int)(DD / Dmoy);
          Ta.x = (this.points[i].x - this.points[(i - 1)].x);
          Ta.y = (this.points[i].y - this.points[(i - 1)].y);
          double normtan = Math.sqrt(Ta.x * Ta.x + Ta.y * Ta.y);
          Ta.x /= normtan;
          Ta.y /= normtan;
          for (int k = 1; k <= aj; k++) {
            temp[ii].x = (this.points[(i - 1)].x + k * Dmoy * Ta.x);
            temp[ii].y = (this.points[(i - 1)].y + k * Dmoy * Ta.y);
            ii++;
            temp[ii] = new Point2d();
          }
        }
        i++;
        if ((DD <= Dmoy) && (i < this.NPT - 1)) {
          int j = i - 1;
          double D = 0.0D;
          while ((D < Dmoy) && (j < this.NPT - 1)) {
            D += distance(j, j + 1);
            j++;
          }
          temp[ii].x = this.points[j].x;
          temp[ii].y = this.points[j].y;
          ii++;
          temp[ii] = new Point2d();
          i = j + 1;
        }
        if (i == this.NPT - 1) {
          i = this.NPT;
        }
      }
      temp[ii].x = this.points[(this.NPT - 1)].x;
      temp[ii].y = this.points[(this.NPT - 1)].y;
      this.NPT = (ii + 1);
      for (i = 0; i < this.NPT; i++) {
        this.points[i].x = temp[i].x;
        this.points[i].y = temp[i].y;
      }
    }
  }

  public void calculus(int deb, int fin)
  {
    double omega = 1.8D;
    Point2d bi = new Point2d();
    Point2d temp = new Point2d();
    Point2d debtemp = new Point2d();

    debtemp.x = this.points[deb].x;
    debtemp.y = this.points[deb].y;

    for (int i = deb; i < fin; i++) {
      bi.x = (this.points[i].x + this.deplace[i].x);
      bi.y = (this.points[i].y + this.deplace[i].y);

      double gi = -this.lambda[i];
      double di = -this.lambda[(i + 1)];
      double mi = this.lambda[i] + this.lambda[(i + 1)] + 1.0D;
      if (i > deb) {
        temp.x = (mi * this.points[i].x + omega * (-gi * this.points[(i - 1)].x - mi * this.points[i].x - di * this.points[(i + 1)].x + bi.x));
        temp.y = (mi * this.points[i].y + omega * (-gi * this.points[(i - 1)].y - mi * this.points[i].y - di * this.points[(i + 1)].y + bi.y));
      }
      if ((i == deb) && (this.closed)) {
        temp.x = (mi * this.points[i].x + omega * (-gi * this.points[fin].x - mi * this.points[i].x - di * this.points[(i + 1)].x + bi.x));
        temp.y = (mi * this.points[i].y + omega * (-gi * this.points[fin].y - mi * this.points[i].y - di * this.points[(i + 1)].y + bi.y));
      }
      if ((i == deb) && (!this.closed)) {
        temp.x = (this.points[deb].x * mi);
        temp.y = (this.points[deb].y * mi);
      }
      this.points[i].x = (temp.x / mi);
      this.points[i].y = (temp.y / mi);
    }

    if (this.closed) {
      int i = fin;
      bi.x = (this.points[i].x + this.deplace[i].x);
      bi.y = (this.points[i].y + this.deplace[i].y);

      double gi = -this.lambda[i];
      double di = -this.lambda[deb];
      double mi = this.lambda[i] + this.lambda[deb] + 1.0D;
      temp.x = (mi * this.points[i].x + omega * (-gi * this.points[(i - 1)].x - mi * this.points[i].x - di * debtemp.x + bi.x));
      temp.y = (mi * this.points[i].y + omega * (-gi * this.points[(i - 1)].y - mi * this.points[i].y - di * debtemp.y + bi.y));
      this.points[i].x = (temp.x / mi);
      this.points[i].y = (temp.y / mi);
    }
  }

  public double compute_displacements()
  {
    double som = 0.0D;
    double seuil = this.config.getGradThreshold();
    double DivForce = this.config.getMaxDisplacement();
    Point2d displ = new Point2d();

    som = 0.0D;
    for (int i = 0; i < this.NPT; i++) {
      displ.x = 0.0D;
      displ.y = 0.0D;
      displ = compute_displ(i, seuil, 1000.0D, 1000.0D, 0);

      double force = Math.sqrt(displ.x * displ.x + displ.y * displ.y);
      if (force > DivForce) {
        this.deplace[i].x = (DivForce * (displ.x / force));
        this.deplace[i].y = (DivForce * (displ.y / force));
      } else {
        this.deplace[i].x = displ.x;
        this.deplace[i].y = displ.y;
      }
      force = Math.sqrt(this.deplace[i].x * this.deplace[i].x + this.deplace[i].y * this.deplace[i].y);

      som += force;
    }
    return som;
  }

  public void computeGrad(ImageProcessor image)
  {
    this.gradImage = grad2d_deriche(image, this.config.getAlpha());
  }

  Point2d compute_displ(int num, double Edge_Threshold, double dist_plus, double dist_minus, int dir)
  {
    double scaleint = this.config.getMaxSearch();

    double crp = (0.0D / 0.0D);
    double crm = (0.0D / 0.0D);

    int scale = 10;
    double[] image_line = new double[(int)(2 * scale * scaleint + 1.0D)];

    Point2d pos = this.points[num];
    Point2d norm = this.normale[num];

    Point2d displacement = new Point2d();

    int index = 0;
    double step = 1.0D / scale;
    double deb = -scaleint;
    for (double ii = deb; ii < scaleint; ii += step) {
      double iy = pos.y + norm.y * ii;
      double ix = pos.x + norm.x * ii;
      if (ix < 0.0D) {
        ix = 0.0D;
      }
      if (iy < 0.0D) {
        iy = 0.0D;
      }
      if (ix >= this.gradImage.getWidth()) {
        ix = this.gradImage.getWidth() - 1;
      }
      if (iy >= this.gradImage.getHeight()) {
        iy = this.gradImage.getHeight() - 1;
      }
      image_line[index] = this.gradImage.getInterpolatedPixel(ix, iy);
      index++;
    }

    for (int i = 0; i < this.NPT - 1; i++) {
      if ((i != num) && (i != num - 1)) {
        double bden = -norm.x * this.points[(i + 1)].y + norm.x * this.points[i].y + norm.y * this.points[(i + 1)].x - norm.y * this.points[i].x;
        double bnum = -norm.x * pos.y + norm.x * this.points[i].y + norm.y * pos.x - norm.y * this.points[i].x;
        double bres;
        if (bden != 0.0D)
          bres = bnum / bden;
        else {
          bres = 5.0D;
        }
        if ((bres >= 0.0D) && (bres <= 1.0D)) {
          double ares = (float)(-(-this.points[(i + 1)].y * pos.x + this.points[(i + 1)].y * this.points[i].x + this.points[i].y * pos.x + pos.y * this.points[(i + 1)].x - pos.y * this.points[i].x - this.points[i].y * this.points[(i + 1)].x) / (-norm.x * this.points[(i + 1)].y + norm.x * this.points[i].y + norm.y * this.points[(i + 1)].x - norm.y * this.points[i].x));
          if ((ares > 0.0D) && (ares < crp)) {
            crp = ares;
          }
          if ((ares < 0.0D) && (ares > crm)) {
            crm = ares;
          }
        }
      }
    }
    double coeff_crossing = 0.9D;
    crp *= coeff_crossing;
    crm *= coeff_crossing;

    double deplus = (1.0D / 0.0D);
    double demoins = (-1.0D / 0.0D);

    boolean edge_found = false;
    for (index = 1; index < 2 * scale * scaleint - 1.0D; index++)
    {
      if ((image_line[index] >= Edge_Threshold) && (image_line[index] >= image_line[(index - 1)]) && (image_line[index] >= image_line[(index + 1)])) {
        double Dist = index * step + deb;
        if ((Dist < 0.0D) && (Dist > demoins)) {
          demoins = Dist;
          edge_found = true;
        }
        if ((Dist >= 0.0D) && (Dist < deplus)) {
          deplus = Dist;
          edge_found = true;
        }
      }
    }
    this.etat[num] = 0;

    if (!edge_found) {
      displacement.x = 0.0D;
      displacement.y = 0.0D;

      return displacement;
    }

    if (deplus > dist_plus) {
      deplus = 2.0D * scaleint;
    }
    if (demoins < -dist_minus) {
      demoins = -2.0D * scaleint;
    }
    if ((Double.isInfinite(deplus)) && (Double.isInfinite(demoins))) {
      displacement.x = 0.0D;
      displacement.y = 0.0D;

      return displacement;
    }
    int direction;
    if (-demoins < deplus) {
      norm.x *= demoins;
      norm.y *= demoins;
      direction = -1;
    } else {
      norm.x *= deplus;
      norm.y *= deplus;
      direction = 1;
    }

    return displacement;
  }

  public void compute_normales()
  {
    for (int i = 0; i < this.NPT; i++)
      this.normale[i] = compute_normale(i);
  }

  public void compute_lambdas()
  {
    double maxforce = 0.0D;
    double minr = this.config.getRegMin();
    double maxr = this.config.getRegMax();

    for (int i = 0; i < this.NPT; i++) {
      double force = Math.sqrt(this.deplace[i].x * this.deplace[i].x + this.deplace[i].y * this.deplace[i].y);
      if (force > maxforce) {
        maxforce = force;
      }
    }

    for (int i = 0; i < this.NPT; i++) {
      double force = Math.sqrt(this.deplace[i].x * this.deplace[i].x + this.deplace[i].y * this.deplace[i].y);
      this.lambda[i] = (maxr / (1.0D + (maxr - minr) / minr * (force / maxforce)));
    }
  }

  Point2d compute_normale(int np)
  {
    Point2d tan = new Point2d();
    Point2d norma = new Point2d();

    if (np == 0) {
      if (this.closed) {
        tan.x = (this.points[1].x - this.points[(this.NPT - 1)].x);
        tan.y = (this.points[1].y - this.points[(this.NPT - 1)].y);
      } else {
        tan.x = (this.points[1].x - this.points[0].x);
        tan.y = (this.points[1].y - this.points[0].y);
      }
    }
    if (np == this.NPT - 1) {
      if (this.closed) {
        tan.x = (this.points[0].x - this.points[(this.NPT - 2)].x);
        tan.y = (this.points[0].y - this.points[(this.NPT - 2)].y);
      } else {
        tan.x = (this.points[(this.NPT - 1)].x - this.points[(this.NPT - 2)].x);
        tan.y = (this.points[(this.NPT - 1)].y - this.points[(this.NPT - 2)].y);
      }
    }
    if ((np > 0) && (np < this.NPT - 1)) {
      tan.x = (this.points[(np + 1)].x - this.points[(np - 1)].x);
      tan.y = (this.points[(np + 1)].y - this.points[(np - 1)].y);
    }
    double normtan = Math.sqrt(tan.x * tan.x + tan.y * tan.y);
    if (normtan > 0.0D) {
      tan.x /= normtan;
      tan.y /= normtan;
      norma.x = (-tan.y);
      norma.y = tan.x;
    }
    return norma;
  }

  void destroysnake()
  {
    Point2d[] temp = new Point2d[this.NPT];
    Point2d[] fo = new Point2d[this.NPT];
    double[] lan = new double[this.NPT];
    int[] state = new int[this.NPT];

    int j = 0;
    for (int i = 0; i < this.NPT; i++) {
      if (this.etat[i] != 1) {
        temp[j] = new Point2d();
        temp[j].x = this.points[i].x;
        temp[j].y = this.points[i].y;
        state[j] = this.etat[i];
        fo[j] = new Point2d();
        fo[j].x = this.deplace[i].x;
        fo[j].y = this.deplace[i].y;
        lan[j] = this.lambda[i];
        j++;
      }
    }
    this.NPT = j;

    for (int i = 0; i < this.NPT; i++) {
      this.points[i].x = temp[i].x;
      this.points[i].y = temp[i].y;
      this.etat[i] = state[i];
      this.deplace[i].x = fo[i].x;
      this.deplace[i].y = fo[i].y;
      this.lambda[i] = lan[i];
    }
  }

  double distance(int a, int b)
  {
    return Math.sqrt(Math.pow(this.points[a].x - this.points[b].x, 2.0D) + Math.pow(this.points[a].y - this.points[b].y, 2.0D));
  }

  void new_positions()
  {
    calculus(0, this.NPT - 1);
  }

  private ImageProcessor grad2d_deriche(ImageProcessor iDep, double alphaD)
  {
    ImageProcessor iGrad = new ByteProcessor(iDep.getWidth(), iDep.getHeight());

    float[] nf_grx = null;
    float[] nf_gry = null;
    float[] a1 = null;
    float[] a2 = null;
    float[] a3 = null;
    float[] a4 = null;
    byte[] result_array = null;

    int icolonnes = 0;
    int icoll = 0;

    int lignes = iDep.getHeight();
    int colonnes = iDep.getWidth();
    int nmem = lignes * colonnes;

    int lig_1 = lignes - 1;
    int lig_2 = lignes - 2;
    int lig_3 = lignes - 3;
    int col_1 = colonnes - 1;
    int col_2 = colonnes - 2;
    int col_3 = colonnes - 3;

    nf_grx = new float[nmem];
    nf_gry = new float[nmem];

    a1 = new float[nmem];
    a2 = new float[nmem];
    a3 = new float[nmem];
    a4 = new float[nmem];

    float ad1 = (float)-Math.exp(-alphaD);
    float ad2 = 0.0F;
    float an1 = 1.0F;
    float an2 = 0.0F;
    float an3 = (float)Math.exp(-alphaD);
    float an4 = 0.0F;
    float an11 = 1.0F;

    for (int i = 0; i < lignes; i++) {
      for (int j = 0; j < colonnes; j++) {
        a1[(i * colonnes + j)] = iDep.getPixelValue(j, i);
      }
    }

    for (int i = 0; i < lignes; i++) {
      icolonnes = i * colonnes;
      int icol_1 = icolonnes - 1;
      int icol_2 = icolonnes - 2;
      a2[icolonnes] = (an1 * a1[icolonnes]);
      a2[(icolonnes + 1)] = (an1 * a1[(icolonnes + 1)] + an2 * a1[icolonnes] - ad1 * a2[icolonnes]);

      for (int j = 2; j < colonnes; j++) {
        a2[(icolonnes + j)] = (an1 * a1[(icolonnes + j)] + an2 * a1[(icol_1 + j)] - ad1 * a2[(icol_1 + j)] - ad2 * a2[(icol_2 + j)]);
      }

    }

    for (int i = 0; i < lignes; i++) {
      icolonnes = i * colonnes;
      int icol_1 = icolonnes + 1;
      int icol_2 = icolonnes + 2;
      a3[(icolonnes + col_1)] = 0.0F;
      a3[(icolonnes + col_2)] = (an3 * a1[(icolonnes + col_1)]);
      for (int j = col_3; j >= 0; j--) {
        a3[(icolonnes + j)] = (an3 * a1[(icol_1 + j)] + an4 * a1[(icol_2 + j)] - ad1 * a3[(icol_1 + j)] - ad2 * a3[(icol_2 + j)]);
      }

    }

    int icol_1 = lignes * colonnes;

    for (int i = 0; i < icol_1; i++) {
      a2[i] += a3[i];
    }

    for (int j = 0; j < colonnes; j++) {
      a3[j] = 0.0F;
      a3[(colonnes + j)] = (an11 * a2[j] - ad1 * a3[j]);
      for (int i = 2; i < lignes; i++) {
        a3[(i * colonnes + j)] = (an11 * a2[((i - 1) * colonnes + j)] - ad1 * a3[((i - 1) * colonnes + j)] - ad2 * a3[((i - 2) * colonnes + j)]);
      }

    }

    for (int j = 0; j < colonnes; j++) {
      a4[(lig_1 * colonnes + j)] = 0.0F;
      a4[(lig_2 * colonnes + j)] = (-an11 * a2[(lig_1 * colonnes + j)] - ad1 * a4[(lig_1 * colonnes + j)]);

      for (int i = lig_3; i >= 0; i--) {
        a4[(i * colonnes + j)] = (-an11 * a2[((i + 1) * colonnes + j)] - ad1 * a4[((i + 1) * colonnes + j)] - ad2 * a4[((i + 2) * colonnes + j)]);
      }

    }

    icol_1 = colonnes * lignes;
    for (int i = 0; i < icol_1; i++) {
      a3[i] += a4[i];
    }

    for (int i = 0; i < lignes; i++) {
      for (int j = 0; j < colonnes; j++) {
        nf_gry[(i * colonnes + j)] = a3[(i * colonnes + j)];
      }

    }

    for (int i = 0; i < lignes; i++) {
      for (int j = 0; j < colonnes; j++) {
        a1[(i * colonnes + j)] = iDep.getPixel(j, i);
      }
    }

    for (int i = 0; i < lignes; i++) {
      icolonnes = i * colonnes;
      icol_1 = icolonnes - 1;
      int icol_2 = icolonnes - 2;
      a2[icolonnes] = 0.0F;
      a2[(icolonnes + 1)] = (an11 * a1[icolonnes]);
      for (int j = 2; j < colonnes; j++) {
        a2[(icolonnes + j)] = (an11 * a1[(icol_1 + j)] - ad1 * a2[(icol_1 + j)] - ad2 * a2[(icol_2 + j)]);
      }

    }

    for (int i = 0; i < lignes; i++) {
      icolonnes = i * colonnes;
      icol_1 = icolonnes + 1;
      int icol_2 = icolonnes + 2;
      a3[(icolonnes + col_1)] = 0.0F;
      a3[(icolonnes + col_2)] = (-an11 * a1[(icolonnes + col_1)]);
      for (int j = col_3; j >= 0; j--) {
        a3[(icolonnes + j)] = (-an11 * a1[(icol_1 + j)] - ad1 * a3[(icol_1 + j)] - ad2 * a3[(icol_2 + j)]);
      }
    }

    icol_1 = lignes * colonnes;
    for (int i = 0; i < icol_1; i++) {
      a2[i] += a3[i];
    }

    for (int j = 0; j < colonnes; j++) {
      a3[j] = (an1 * a2[j]);
      a3[(colonnes + j)] = (an1 * a2[(colonnes + j)] + an2 * a2[j] - ad1 * a3[j]);

      for (int i = 2; i < lignes; i++) {
        a3[(i * colonnes + j)] = (an1 * a2[(i * colonnes + j)] + an2 * a2[((i - 1) * colonnes + j)] - ad1 * a3[((i - 1) * colonnes + j)] - ad2 * a3[((i - 2) * colonnes + j)]);
      }

    }

    for (int j = 0; j < colonnes; j++) {
      a4[(lig_1 * colonnes + j)] = 0.0F;
      a4[(lig_2 * colonnes + j)] = (an3 * a2[(lig_1 * colonnes + j)] - ad1 * a4[(lig_1 * colonnes + j)]);
      for (int i = lig_3; i >= 0; i--) {
        a4[(i * colonnes + j)] = (an3 * a2[((i + 1) * colonnes + j)] + an4 * a2[((i + 2) * colonnes + j)] - ad1 * a4[((i + 1) * colonnes + j)] - ad2 * a4[((i + 2) * colonnes + j)]);
      }

    }

    icol_1 = colonnes * lignes;
    for (int i = 0; i < icol_1; i++) {
      a3[i] += a4[i];
    }

    for (int i = 0; i < lignes; i++) {
      for (int j = 0; j < colonnes; j++) {
        nf_grx[(i * colonnes + j)] = a3[(i * colonnes + j)];
      }

    }

    for (int i = 0; i < lignes; i++) {
      for (int j = 0; j < colonnes; j++) {
        a2[(i * colonnes + j)] = nf_gry[(i * colonnes + j)];
      }
    }
    icol_1 = colonnes * lignes;
    for (int i = 0; i < icol_1; i++) {
      a2[i] = ((float)Math.sqrt(a2[i] * a2[i] + a3[i] * a3[i]));
    }

    result_array = new byte[nmem];

    double min = a2[0];
    double max = a2[0];
    for (int i = 1; i < nmem; i++) {
      if (min > a2[i]) {
        min = a2[i];
      }
      if (max < a2[i]) {
        max = a2[i];
      }

    }

    for (int i = 0; i < nmem; i++) {
      result_array[i] = ((byte)(int)(255.0D * (a2[i] / (max - min))));
    }

    iGrad.setPixels(result_array);

    return iGrad;
  }

  public double process()
  {
    Point2d displ = new Point2d();
    double maxforce = 0.0D;
    double som = 0.0D;
    double seuil = this.config.getGradThreshold();
    double DivForce = this.config.getMaxDisplacement();
    double minr = this.config.getRegMin();
    double maxr = this.config.getRegMax();
    double alpha = this.config.getAlpha();

    double dist_plus = Prefs.get("ABSnake_ThreshDistPos.double", 100.0D);
    double dist_minus = Prefs.get("ABSnake_ThreshDistNeg.double", 100.0D);

    for (int i = 0; i < this.NPT; i++) {
      this.normale[i] = compute_normale(i);
    }
    this.block = 0;
    this.elimination = 0;
    for (int i = 0; i < this.NPT; i++) {
      displ.x = 0.0D;
      displ.y = 0.0D;
      displ = compute_displ(i, seuil, dist_plus, dist_minus, -1);

      double force = Math.sqrt(Math.pow(displ.x, 2.0D) + Math.pow(displ.y, 2.0D));
      if (force > DivForce) {
        this.deplace[i].x = (DivForce * (displ.x / force));
        this.deplace[i].y = (DivForce * (displ.y / force));
      } else {
        this.deplace[i].x = displ.x;
        this.deplace[i].y = displ.y;
      }
      force = Math.sqrt(this.deplace[i].x * this.deplace[i].x + this.deplace[i].y * this.deplace[i].y);
      if (force > maxforce) {
        maxforce = force;
      }
      som += force;
    }
    this.dataDistance = (som / this.NPT);

    for (int i = 0; i < this.NPT; i++) {
      double force = Math.sqrt(Math.pow(this.deplace[i].x, 2.0D) + Math.pow(this.deplace[i].y, 2.0D));
      this.lambda[i] = (maxr / (1.0D + (maxr - minr) / minr * (force / maxforce)));
    }
    if (this.elimination == 1) {
      destroysnake();
    }

    new_positions();
    resample(false);

    return this.dataDistance;
  }

  public ByteProcessor segmentation(int wi, int he, int col)
  {
    Point2d pos = new Point2d();
    Point2d norm = new Point2d();
    Point2d ref = new Point2d();

    ByteProcessor res = new ByteProcessor(wi, he);

    int top = 0;
    int bottom = 100000;
    int left = 100000;
    int right = 0;
    for (int i = 0; i < this.NPT; i++) {
      if (this.points[i].y > top) {
        top = (int)this.points[i].y;
      }
      if (this.points[i].y < bottom) {
        bottom = (int)this.points[i].y;
      }
      if (this.points[i].x > right) {
        right = (int)this.points[i].x;
      }
      if (this.points[i].x < left) {
        left = (int)this.points[i].x;
      }

    }

    ref.x = 0.0D;
    ref.y = 0.0D;
    for (int x = left; x < right; x++) {
      for (int y = bottom; y < top; y++) {
        pos.x = x;
        pos.y = y;

        if (inside(pos))
          res.putPixel(x, y, col);
        else {
          res.putPixel(x, y, 0);
        }
      }
    }
    return res;
  }

  boolean inside(Point2d pos)
  {
    Point2d norm = new Point2d();
    Point2d ref = new Point2d();

    ref.x = 0.0D;
    ref.y = 0.0D;
    ref.x -= pos.x;
    ref.y -= pos.y;
    double lnorm = Math.sqrt(norm.x * norm.x + norm.y * norm.y);
    norm.x /= lnorm;
    norm.y /= lnorm;

    int count = 0;
    for (int i = 0; i < this.NPT - 1; i++) {
      double bden = -norm.x * this.points[(i + 1)].y + norm.x * this.points[i].y + norm.y * this.points[(i + 1)].x - norm.y * this.points[i].x;
      double bnum = -norm.x * pos.y + norm.x * this.points[i].y + norm.y * pos.x - norm.y * this.points[i].x;
      double bres;
      if (bden != 0.0D)
        bres = bnum / bden;
      else {
        bres = 5.0D;
      }
      if ((bres >= 0.0D) && (bres <= 1.0D)) {
        double ares = -(-this.points[(i + 1)].y * pos.x + this.points[(i + 1)].y * this.points[i].x + this.points[i].y * pos.x + pos.y * this.points[(i + 1)].x - pos.y * this.points[i].x - this.points[i].y * this.points[(i + 1)].x) / (-norm.x * this.points[(i + 1)].y + norm.x * this.points[i].y + norm.y * this.points[(i + 1)].x - norm.y * this.points[i].x);

        if ((ares >= 0.0D) && (ares <= lnorm)) {
          count++;
        }
      }
    }

    int i = this.NPT - 1;
    double bden = -norm.x * this.points[0].y + norm.x * this.points[i].y + norm.y * this.points[0].x - norm.y * this.points[i].x;
    double bnum = -norm.x * pos.y + norm.x * this.points[i].y + norm.y * pos.x - norm.y * this.points[i].x;
    double bres;
    if (bden != 0.0D)
      bres = bnum / bden;
    else {
      bres = 5.0D;
    }
    if ((bres >= 0.0D) && (bres <= 1.0D)) {
      double ares = -(-this.points[0].y * pos.x + this.points[0].y * this.points[i].x + this.points[i].y * pos.x + pos.y * this.points[0].x - pos.y * this.points[i].x - this.points[i].y * this.points[0].x) / (-norm.x * this.points[0].y + norm.x * this.points[i].y + norm.y * this.points[0].x - norm.y * this.points[i].x);

      if ((ares >= 0.0D) && (ares <= lnorm)) {
        count++;
      }
    }
    return count % 2 == 1;
  }
}