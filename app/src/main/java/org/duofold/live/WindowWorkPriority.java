package org.duofold.live;

/** Short bookkeeping only; no monitor is held across an IPC or a pixel copy.
 * Critical work never waits for, or acquires the busy flag of, optional work. */
final class WindowWorkPriority {
 static final WindowWorkPriority INSTANCE=new WindowWorkPriority();
 private int critical;
 private long revision;
 synchronized void enterCritical(){critical++;revision++;}
 synchronized void leaveCritical(){if(critical<=0)throw new IllegalStateException("Unbalanced window work");critical--;}
 synchronized long optionalToken(){return critical==0?revision:-1;}
 synchronized boolean accepts(long token){return token>=0&&critical==0&&token==revision;}
}
