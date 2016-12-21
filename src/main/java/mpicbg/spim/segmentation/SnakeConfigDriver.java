package mpicbg.spim.segmentation;

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
    this.maxDisplacement0 = 2.0D;
    this.maxDisplacement1 = 0.1D;
    this.inv_alphaD0 = 2.0D;
    this.inv_alphaD1 = 0.5D;
    this.reg0 = 2.0D;
    this.reg1 = 0.1D;
    this.step = 0.99D;
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
