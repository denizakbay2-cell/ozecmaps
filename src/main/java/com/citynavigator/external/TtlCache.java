package com.citynavigator.external;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A small least-recently-used cache whose entries also expire, so repeated searches and routes do not hit the
 * public services again. Thread-safe; a value is computed outside the lock.
 */
public final class TtlCache<K, V> {

    private final int maxEntries;
    private final Duration timeToLive;
    private final Clock clock;
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);

    public TtlCache(int maxEntries, Duration timeToLive, Clock clock) {
        this.maxEntries = maxEntries;
        this.timeToLive = timeToLive;
        this.clock = clock;
    }

    public synchronized Optional<V> get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expires())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public synchronized void put(K key, V value) {
        entries.put(key, new Entry<>(value, clock.instant().plus(timeToLive)));
        Iterator<K> oldestFirst = entries.keySet().iterator();
        while (entries.size() > maxEntries) {
            oldestFirst.next();
            oldestFirst.remove();
        }
    }

    public V computeIfAbsent(K key, Supplier<V> loader) {
        Optional<V> cached = get(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        V value = loader.get();
        put(key, value);
        return value;
    }

    public synchronized int size() {
        return entries.size();
    }

    private record Entry<V>(V value, Instant expires) {
    }
}
