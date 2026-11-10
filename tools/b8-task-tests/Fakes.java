package org.duofold.live.experimental.b8;
final class PowerExperiment {static Object service(String n,String c){if(n.equals("display"))return new android.hardware.display.IDisplayManager();if(n.equals("window"))return new android.view.IWindowManager();return new android.os.IPowerManager();}}
final class ExperimentConfig {int duration,initial,target,backend,renewal,cancellation;boolean alternate;}
