package me.xiaozhangup.octopus.util;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.AbstractList;

public class FixedSizeList<E> extends AbstractList<E> {
    private final int capacity;
    private final ObjectArrayList<E> data;
    private int head = 0;
    private int size = 0;

    public FixedSizeList(int capacity) {
        this.capacity = capacity;
        this.data = new ObjectArrayList<>(capacity);
    }

    @Override
    public boolean add(E e) {
        if (size < capacity) {
            data.add(e);
            size++;
        } else {
            data.set(head, e);
            head = (head + 1) % capacity;
        }
        return true;
    }

    @Override
    public E get(int index) {
        if (index >= size) throw new IndexOutOfBoundsException();
        int actualIndex = (head - size + index + capacity) % capacity;
        return data.get(actualIndex);
    }

    @Override
    public int size() {
        return size;
    }
}

