package eyeinteraction.utils;

public class RollingMovingAverage extends NumberFixedLengthFifoQueue {

    private float maNumerator;
    private float maValue;
    private float stdValue;

    public RollingMovingAverage(Number[] initialValues) {
        super(initialValues);
        maNumerator = 0.0f;
        maValue = 0.0f;
        initialize();
    }

    public float getValue() {
        return maValue;
    }

    @Override
    public boolean add(Number newest) {
        //remove those negative values, which indicate the eyes are lost tracking
        if(newest.floatValue() > -0.1f)
            this.offer(newest);
        return true;
    }

    @Override
    public boolean offer(Number newest) {
        maNumerator -= ring[index].floatValue();

        boolean res = super.offer(newest);

        maNumerator += ring[getHeadIndex()].floatValue();
        maValue = maNumerator / (float) ring.length;

        float tempStd = 0;

        for(int i = 1; i < ring.length; i++){
            tempStd += (ring[i].floatValue() - maValue) * (ring[i].floatValue() - maValue);
        }

        stdValue = (float) Math.sqrt(tempStd / (float)ring.length);

        return res;
    }

    public float getStd() {
        return stdValue;
    }

    private void initialize() {
        for (int i = previousIndex(index), n = 0; n < ring.length; i = previousIndex(i), n++) {
            maNumerator += ring[i].floatValue();
        }
        maValue = maNumerator / (float) ring.length;
    }
}