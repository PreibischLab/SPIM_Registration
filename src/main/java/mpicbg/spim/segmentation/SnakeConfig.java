package mpicbg.spim.segmentation;

public class SnakeConfig
{
  private double gradThreshold;
  private double maxDisplacement;
  private double maxSearch;
  private double regMin;
  private double regMax;
  private double alphaDeriche;

  public SnakeConfig(double gt, double md, double ms, double rmin, double rmax, double alpha)
  {
    this.gradThreshold = gt;
    this.maxDisplacement = md;
    this.maxSearch = ms;
    this.regMin = rmin;
    this.regMax = rmax;
    this.alphaDeriche = alpha;
  }

  public SnakeConfig(SnakeConfig conf)
  {
    this.gradThreshold = conf.getGradThreshold();
    this.maxDisplacement = conf.getMaxDisplacement();
    this.maxSearch = conf.getMaxSearch();
    this.regMin = conf.getRegMin();
    this.regMax = conf.getRegMax();
    this.alphaDeriche = conf.getAlpha();
  }

  public double getGradThreshold()
  {
    return this.gradThreshold;
  }

  public double getMaxDisplacement()
  {
    return this.maxDisplacement;
  }

  public double getMaxSearch()
  {
    return this.maxSearch;
  }

  public double getRegMin()
  {
    return this.regMin;
  }

  public double getRegMax()
  {
    return this.regMax;
  }

  public double getAlpha()
  {
    return this.alphaDeriche;
  }

  public void update(double mul)
  {
    this.alphaDeriche /= mul;

    this.regMax *= mul;
    this.regMin *= mul;
  }
}