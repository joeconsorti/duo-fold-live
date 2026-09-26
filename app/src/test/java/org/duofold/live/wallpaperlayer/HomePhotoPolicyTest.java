package org.duofold.live.wallpaperlayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomePhotoPolicyTest {
 @Test public void acceptsHomeWithSystemBarInsetsOnBothPanels(){assertTrue(HomePhotoPolicy.accepts(1248,1780,1248,1972));assertTrue(HomePhotoPolicy.accepts(2448,1710,2448,1848));}
 @Test public void rejectsPopupsAndPartialWindows(){assertFalse(HomePhotoPolicy.accepts(800,700,1248,1972));assertFalse(HomePhotoPolicy.accepts(1248,900,1248,1972));}
 @Test public void rejectsStaleGeometryAfterPanelRemap(){assertFalse(HomePhotoPolicy.accepts(2448,1848,1248,1972));assertFalse(HomePhotoPolicy.accepts(1248,1972,2448,1848));}
 @Test public void rejectsMissingOrInvalidDimensions(){assertFalse(HomePhotoPolicy.accepts(0,0,1248,1972));assertFalse(HomePhotoPolicy.accepts(1248,1972,0,0));}
}
