
package mpicbg.spim.segmentation;
public class Point2d
{
  public double x;
  public double y;

  public Point2d()
  {
    this.x = 0.0D;
    this.y = 0.0D;
  }

  public double dist(Point2d p2)
  {
    return Math.sqrt((this.x - p2.x) * (this.x - p2.x) + (this.y - p2.y) * (this.y - p2.y));
  }

  public String toString()
  {
    String data = new String("x=" + this.x + " y=" + this.y);
    return data;
  }
}