package com.citynavigator.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MinHeapTest {

    @Test
    void returnsValuesInOrderOfTheirKeysAndGrowsAsNeeded() {
        MinHeap heap = new MinHeap(1);
        Random random = new Random(7);
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            int key = random.nextInt(10_000);
            heap.add(key, key);
            expected.add(key);
        }
        expected.sort(null);
        List<Integer> polled = new ArrayList<>();
        while (!heap.isEmpty()) {
            double key = heap.peekKey();
            int value = heap.poll();
            assertThat((double) value).isEqualTo(key);
            polled.add(value);
        }
        assertThat(polled).isEqualTo(expected);
        assertThat(heap.size()).isZero();
    }

    @Test
    void anEmptyHeapHasNothingToGive() {
        MinHeap heap = new MinHeap(4);
        assertThatThrownBy(heap::poll).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(heap::peekKey).isInstanceOf(NoSuchElementException.class);
    }
}
