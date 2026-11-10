package org.duofold.live;
import java.util.regex.*;
/** Retain only numeric/enum rotation policy fields; never window titles or app contents. */
final class RotationDumpEvidence {
 private static final Pattern FIELD=Pattern.compile("(?<![A-Za-z0-9_])(mDisplayId|displayId|mRotation|mUserRotation|mUserRotationMode|mFixedToUserRotation|mCurrentAppOrientation|mLastOrientation|mSensorRotation|mProposedRotation|mLastSensorRotation|mDeferredRotationPauseCount|mSeamlessRotationCount|mDisplayFrozen|mWindowsFreezingScreen|mOrientationListenerEnabled|mEnabled|mCurrentRotation|mPredictedRotation|mDesiredRotation|mTouching|mSwinging|mOverhead|mFlat)\\s*=\\s*(-?[0-9]+|true|false|[A-Z][A-Z0-9_]*)(?=\\s|[,;}]|$)");
 static String filter(String dump){StringBuilder out=new StringBuilder();int count=0;for(String line:dump.split("\\n")){Matcher m=FIELD.matcher(line);StringBuilder row=new StringBuilder();while(m.find()){if(row.length()>0)row.append(' ');row.append(m.group(1)).append('=').append(m.group(2));}if(row.length()>0){if(out.length()>0)out.append(" | ");out.append(row);if(++count>=32||out.length()>=2200)break;}}return out.length()==0?"UNAVAILABLE: rotation fields not exposed by firmware":out.toString();}
}
