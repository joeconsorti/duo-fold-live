package org.duofold.live;

/** A logical display remap must not replace a matching physical-panel snapshot. */
final class B8SnapshotPolicy {
 // Same observation-age and query-duration limits as LiveB8Controller.nativeAppReady.
 static boolean currentObservation(long issuedCheck,long activeCheck,long startedAt,long observedAt,long now) {
  return issuedCheck>0 && issuedCheck==activeCheck && startedAt>0 && observedAt>=startedAt
    && observedAt<=now && observedAt-startedAt<=750 && now-observedAt<=250;
 }
 static boolean matches(String panel,int width,int height,int rotation,
                        String imagePanel,int imageWidth,int imageHeight,int imageRotation) {
  return PanelIdentityPolicy.known(panel) && panel.equals(imagePanel)
    && width>0 && height>0 && width==imageWidth && height==imageHeight
    && rotation>=0 && rotation<=3 && rotation==imageRotation;
 }
}
