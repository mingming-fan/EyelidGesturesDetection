package eyeinteraction.utils;

public class SensorData {
    public float Left;
    public float Right;
    public long Time;

    public SensorData() {}

    public SensorData(float ll, float rr, long tt) {
        Left = ll;
        Right = rr;
        Time = tt;
    }

    public SensorData(SensorData sd) {
        Left = sd.Left;
        Right = sd.Right;
        Time = sd.Time;
    }
}
