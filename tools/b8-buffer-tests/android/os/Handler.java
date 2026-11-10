package android.os;public class Handler{public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}}
