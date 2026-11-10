package android.graphics;
public class Point {
 public int x,y;
 public Point(){}
 public Point(int x,int y){this.x=x;this.y=y;}
 public Point(Point other){this(other.x,other.y);}
}
