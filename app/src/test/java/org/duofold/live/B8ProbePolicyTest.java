package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class B8ProbePolicyTest {
 @Test public void revealAndCleanupDoNotRevokeEpisode(){
  long latestSession=101;
  assertTrue(B8ProbePolicy.accepts(101,latestSession));
  // Neither clearing primaryCheck nor ending rendering changes latestSession.
  assertTrue(B8ProbePolicy.accepts(101,latestSession));
 }
 @Test public void subsequentEpisodePermanentlyInvalidatesOldProbe(){
  assertFalse(B8ProbePolicy.accepts(101,202));
  assertTrue(B8ProbePolicy.accepts(202,202));
 }
 @Test public void uninitializedEpisodeCannotCapture(){assertFalse(B8ProbePolicy.accepts(0,0));}
}
