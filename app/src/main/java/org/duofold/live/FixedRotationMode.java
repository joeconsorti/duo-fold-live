package org.duofold.live;
final class FixedRotationMode {
 static int parse(String mode){switch(mode){case "default":return 0;case "disabled":return 1;case "enabled":return 2;case "enabled_if_no_auto_rotation":return 3;default:throw new IllegalArgumentException("Unknown fixed rotation policy: "+mode);}}
}
