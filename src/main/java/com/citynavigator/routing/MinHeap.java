package com.citynavigator.routing;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Binary min-heap of (priority, node) pairs in two primitive arrays. A node may be added more than once; the
 * router skips the stale copies ("lazy deletion"), which is simpler and in practice as fast as decrease-key.
 */
final class MinHeap {

    private double[] keys;
    private int[] values;
    private int size;

    MinHeap(int initialCapacity) {
        keys = new double[Math.max(1, initialCapacity)];
        values = new int[keys.length];
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    double peekKey() {
        if (size == 0) {
            throw new NoSuchElementException("the heap is empty");
        }
        return keys[0];
    }

    void add(double key, int value) {
        if (size == keys.length) {
            keys = Arrays.copyOf(keys, size * 2);
            values = Arrays.copyOf(values, size * 2);
        }
        int i = size++;
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (keys[parent] <= key) {
                break;
            }
            keys[i] = keys[parent];
            values[i] = values[parent];
            i = parent;
        }
        keys[i] = key;
        values[i] = value;
    }

    int poll() {
        if (size == 0) {
            throw new NoSuchElementException("the heap is empty");
        }
        int top = values[0];
        size--;
        double key = keys[size];
        int value = values[size];
        int i = 0;
        while (true) {
            int child = 2 * i + 1;
            if (child >= size) {
                break;
            }
            if (child + 1 < size && keys[child + 1] < keys[child]) {
                child++;
            }
            if (keys[child] >= key) {
                break;
            }
            keys[i] = keys[child];
            values[i] = values[child];
            i = child;
        }
        keys[i] = key;
        values[i] = value;
        return top;
    }
}
