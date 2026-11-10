package org.duofold.live.experimental.b8;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecentCrashEvidenceTest {
 @Test public void keepsSystemCauseAndCascadingDeathsButOmitsOldAppCrashes(){
  String raw="--------- beginning of crash\n1793000000.000 1 1 E AndroidRuntime: old OOM\n1793978142.690 32243 32302 E AndroidRuntime: FATAL EXCEPTION IN SYSTEM PROCESS: android.display\n1793978142.690 32243 32302 E AndroidRuntime: at BLASTSyncEngine.setSyncMethod\n1793978142.872 3351 3351 E AndroidRuntime: DeadSystemException\n";
  String r=RecentCrashEvidence.select(raw,1793978154303L,300000);
  assertTrue(r.contains("SYSTEM PROCESS"));assertTrue(r.contains("BLASTSyncEngine"));assertTrue(r.contains("DeadSystemException"));assertFalse(r.contains("old OOM"));assertTrue(r.contains("retainedLines=3"));assertTrue(r.contains("omittedOutsideWindow=1"));
 }
 @Test public void missingOrUnsupportedEvidenceDoesNotBecomeACleanPass(){String r=RecentCrashEvidence.select("logcat: permission denied\nNaN bad\n",1793978154303L,300000);assertTrue(r.contains("unparsedLines=2"));assertTrue(r.contains("Absence is not proof"));}
 @Test public void futureAndBoundaryTimestamps(){String r=RecentCrashEvidence.select("700.000 begin\n699.999 old\n1000.000 now\n2000.000 future\n",1000000,300000);assertTrue(r.contains(" begin"));assertTrue(r.contains(" now"));assertFalse(r.contains(" old"));assertFalse(r.contains(" future"));}
}
