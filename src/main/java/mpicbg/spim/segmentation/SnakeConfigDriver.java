package mpicbg.spim.segmentation;

/**
 *  Configuration parameters for the snake plug-in
 *
 *@author     thomas
 *@created    11 mai 2004
 */
public class SnakeConfigDriver
{
  private double maxDisplacement0;
  private double maxDisplacement1;
  private double inv_alphaD0;
  private double inv_alphaD1;
  private double reg0;
  private double reg1;
  private double step;

  public SnakeConfigDriver()
  {
	  maxDisplacement0 = 2.0;
		maxDisplacement1 = 0.1;
		inv_alphaD0 = 1.0 / 0.5;
		inv_alphaD1 = 1.0 / 2.0;
		reg0 = 2.0;
		reg1 = 0.1;
		step = 0.99;
  }

  public void setMaxDisplacement(double min, double max)
  {
    this.maxDisplacement1 = min;
    this.maxDisplacement0 = max;
  }

  public void setInvAlphaD(double min, double max)
  {
    this.inv_alphaD1 = min;
    this.inv_alphaD0 = max;
  }

  public void setReg(double min, double max)
  {
    this.reg1 = min;
    this.reg0 = max;
  }

  public void setStep(double s)
  {
    this.step = s;
  }

  public double getStep()
  {
    return this.step;
  }

  public double getInvAlphaD(boolean min)
  {
    if (min) {
      return this.inv_alphaD1;
    }
    return this.inv_alphaD0;
  }

  public double getMaxDisplacement(boolean min)
  {
    if (min) {
      return this.maxDisplacement1;
    }
    return this.maxDisplacement0;
  }

  public double getReg(boolean min)
  {
    if (min) {
      return this.reg1;
    }
    return this.reg0;
  }
}