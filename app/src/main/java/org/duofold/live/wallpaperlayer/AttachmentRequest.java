package org.duofold.live.wallpaperlayer;
/** Main-thread attachment lifecycle: a late or duplicate reply cannot activate/release a surface. */
final class AttachmentRequest {
 enum Result { SUCCESS, REJECTED, TIMED_OUT, IGNORED }
 final long deadline;private boolean finished;
 AttachmentRequest(long start){deadline=start+5000;}
 Result reply(long now,int code){if(finished)return Result.IGNORED;finished=true;return now>=deadline?Result.TIMED_OUT:code==0?Result.SUCCESS:Result.REJECTED;}
 Result timeout(long now){if(finished||now<deadline)return Result.IGNORED;finished=true;return Result.TIMED_OUT;}
 void cancel(){finished=true;}
}
