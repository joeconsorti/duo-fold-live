package org.duofold.live;

/** A logical display remap must not replace a matching physical-panel snapshot. */
final class B8SnapshotPolicy {
 static boolean matches(String panel,int width,int height,int rotation,
                        String imagePanel,int imageWidth,int imageHeight,int imageRotation) {
  return PanelIdentityPolicy.known(panel) && panel.equals(imagePanel)
    && width>0 && height>0 && width==imageWidth && height==imageHeight
    && rotation>=0 && rotation<=3 && rotation==imageRotation;
 }
}
