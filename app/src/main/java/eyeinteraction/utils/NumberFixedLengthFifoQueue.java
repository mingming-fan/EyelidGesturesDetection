package eyeinteraction.utils;

public class NumberFixedLengthFifoQueue implements Queue<Number> {

    protected Number[] ring;
    protected int index;

    /**
     * @param initialValues contains the ring's initial values.
     * The "oldest" value in the queue is expected to reside in
     * position 0, the newest one in position length-1.
     */
    public NumberFixedLengthFifoQueue(Number[] initialValues) {
        // This is a little ugly, but there are no
        // generic arrays in Java
        ring = new Number[initialValues.length];

        // We don't want to work on the original data
        System.arraycopy(initialValues, 0, ring, 0, initialValues.length);

        // The next time we add something to the queue,
        // the oldest element should be replaced
        index = 0;
    }

    @Override
    public boolean add(Number newest) {
        return offer(newest);
    }

    @Override
    public Number element() {
        return ring[getHeadIndex()];
    }

    @Override
    public boolean offer(Number newest) {
        Number oldest = ring[index];
        ring[index] = newest;
        incrIndex();
        return true;
    }

    @Override
    public Number peek() {
        return ring[getHeadIndex()];
    }

    @Override
    public Number poll() {
        throw new IllegalStateException("The poll method is not available for NumberFixedLengthFifoQueue.");
    }

    @Override
    public Number remove() {
        throw new IllegalStateException("The remove method is not available for NumberFixedLengthFifoQueue.");
    }

    public Number get(int absIndex) throws IndexOutOfBoundsException {
        if (absIndex >= ring.length) {
            throw new IndexOutOfBoundsException("Invalid index " + absIndex);
        }
        int i = index + absIndex;
        if (i >= ring.length) {
            i -= ring.length;
        }
        return ring[i];
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer("[");
        for (int i = index, n = 0; n < ring.length; i = nextIndex(i), n++) {
            sb.append(ring[i]);
            if (n+1 < ring.length) { sb.append(", "); }
        }
        return sb.append("]").toString();
    }

    protected void incrIndex() {
        index = nextIndex(index);
    }

    protected int nextIndex(int current) {
        if (current + 1 >= ring.length) { return 0; }
        else return current + 1;
    }

    protected int previousIndex(int current) {
        if (current - 1 < 0) { return ring.length - 1; }
        else return current - 1;
    }

    protected int getHeadIndex() {
        if (index == 0) { return ring.length-1; }
        else return index-1;
    }
}