package org.duofold.live;
import android.view.Display;
/** Identity is already validated by its separate policy; hold it constant here. */
public class PanelIdentity {public static String identity(Display display){return display.physical;}}
