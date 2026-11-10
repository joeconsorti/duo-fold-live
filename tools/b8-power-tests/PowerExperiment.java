package org.duofold.live.experimental.b8;
final class PowerExperiment {static Object service(String name,String type){return name.equals("display")?new android.hardware.display.IDisplayManager():new android.hardware.devicestate.IDeviceStateManager();}}
