/*
 *
 *  Copyright 2019 Robert Winkler and Bohdan Storozhuk
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */
package io.github.resilience4j.core.metrics;


import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link Metrics} implementation is backed by a sliding window that aggregates only the last
 * {@code N} calls.
 * <p>
 * The sliding window is implemented with a circular array of {@code N} measurements. If the time
 * window size is 10, the circular array has always 10 measurements.
 * <p>
 * The sliding window incrementally updates a total aggregation. The total aggregation is updated
 * incrementally when a new call outcome is recorded. When the oldest measurement is evicted, the
 * measurement is subtracted from the total aggregation. (Subtract-on-Evict)
 * <p>
 * The time to retrieve a Snapshot is constant 0(1), since the Snapshot is pre-aggregated and is
 * independent of the window size. The space requirement (memory consumption) of this implementation
 * should be O(n).
 */
public class FixedSizeSlidingWindowMetrics implements Metrics {

    private final int windowSize; // 滑动窗口大小
    private final TotalAggregation totalAggregation; // 该窗口的总计数据
    private final Measurement[] measurements;//滑动窗口的桶
    int headIndex; // 滑动的指针，每次➕1

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates a new {@link FixedSizeSlidingWindowMetrics} with the given window size.
     *
     * @param windowSize the window size
     */
    public FixedSizeSlidingWindowMetrics(int windowSize) {
        this.windowSize = windowSize;
        this.measurements = new Measurement[this.windowSize];
        this.headIndex = 0;
        for (int i = 0; i < this.windowSize; i++) {
            measurements[i] = new Measurement();
        }
        this.totalAggregation = new TotalAggregation();
    }

    /**
     * 线程安全的
     * @param duration     the duration of the call
     * @param durationUnit the time unit of the duration
     * @param outcome      the outcome of the call
     * @return
     */
    @Override
    public Snapshot record(long duration, TimeUnit durationUnit, Outcome outcome) {
        lock.lock();

        try {
            totalAggregation.record(duration, durationUnit, outcome);//统计总数
            moveWindowByOne().record(duration, durationUnit, outcome);// 将数据记录到当前滑动得到的桶
            return new SnapshotImpl(totalAggregation); // 实时的快照
        } finally {
            lock.unlock();
        }
    }

    public Snapshot getSnapshot() {
        lock.lock();

        try {
            return new SnapshotImpl(totalAggregation);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 每次滚动一位
     * @return
     */
    private Measurement moveWindowByOne() {
        moveHeadIndexByOne();
        // 拿到当前headIndex对应的桶
        Measurement latestMeasurement = getLatestMeasurement();
        // 把这个桶对应的数据，从总计里面清空
        totalAggregation.removeBucket(latestMeasurement);
        // 清空该桶里面的数据
        latestMeasurement.reset();
        return latestMeasurement;
    }

    /**
     * Returns the head partial aggregation of the circular array.
     *
     * @return the head partial aggregation of the circular array
     */
    private Measurement getLatestMeasurement() {
        return measurements[headIndex];
    }

    /**
     * 这个算法就导致了，数组桶 是从 1到length-1 再到0 流转的
     *
     * Moves the headIndex to the next bucket.
     */
    void moveHeadIndexByOne() {
        this.headIndex = (headIndex + 1) % windowSize;
    }
}