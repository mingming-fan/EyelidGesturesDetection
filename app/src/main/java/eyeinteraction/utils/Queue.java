package eyeinteraction.utils;

public interface Queue<E> {

    public boolean add(E e);
    public E element();
    public boolean offer(E e);
    public E peek();
    public E poll();
    public E remove();

}