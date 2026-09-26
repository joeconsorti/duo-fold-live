package org.duofold.live.wallpaperlayer;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.duofold.live.wallpaperlayer.AttachmentRequest.Result.*;
public class AttachmentRequestTest {
 @Test public void successSurvivesDuplicateReplyAndTimeout(){AttachmentRequest r=new AttachmentRequest(100);assertEquals(SUCCESS,r.reply(5099,0));assertEquals(IGNORED,r.reply(5100,1));assertEquals(IGNORED,r.timeout(5100));}
 @Test public void missingReplyTimesOutAndLateSuccessCannotActivate(){AttachmentRequest r=new AttachmentRequest(100);assertEquals(IGNORED,r.timeout(5099));assertEquals(TIMED_OUT,r.timeout(5100));assertEquals(IGNORED,r.reply(5101,0));}
 @Test public void delayedHandlerCannotAcceptExpiredSuccess(){AttachmentRequest r=new AttachmentRequest(100);assertEquals(TIMED_OUT,r.reply(5100,0));assertEquals(IGNORED,r.timeout(5200));}
 @Test public void cancellationPreventsActivationAndCleanupTwice(){AttachmentRequest r=new AttachmentRequest(100);r.cancel();assertEquals(IGNORED,r.reply(200,0));assertEquals(IGNORED,r.timeout(5100));}
 @Test public void rejectionCannotBeReplacedBySuccess(){AttachmentRequest r=new AttachmentRequest(100);assertEquals(REJECTED,r.reply(200,2));assertEquals(IGNORED,r.reply(201,0));}
}
