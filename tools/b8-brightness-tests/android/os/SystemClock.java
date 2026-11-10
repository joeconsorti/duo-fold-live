package android.os;public class SystemClock {public static volatile long now=10000;public static long elapsedRealtime(){return now;}public static long uptimeMillis(){return now;}}
