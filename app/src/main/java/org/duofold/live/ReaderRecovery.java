package org.duofold.live;
public final class ReaderRecovery {
 public static boolean needsRestart(String state){return "Stopped".equals(state)||"Log reader ended".equals(state)||(state!=null&&state.startsWith("Reader error:"));}
}
