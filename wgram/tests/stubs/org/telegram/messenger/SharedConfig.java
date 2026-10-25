package org.telegram.messenger;
public class SharedConfig {
    public static final int PERFORMANCE_CLASS_LOW = 0;
    public static int performance = 2;
    public static int getDevicePerformanceClass() { return performance; }
}
