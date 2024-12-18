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


import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link Metrics} implementation is backed by a sliding time window that aggregates only the
 * calls made in the last {@code N} seconds.
 * <p>
 * The sliding time window is implemented with a circular array of {@code N} partial aggregations
 * (buckets). If the time window size is 10 seconds, the circular array has always 10 partial
 * aggregations (buckets). Every bucket aggregates the outcome of all calls which happen in a
 * certain epoch second. (Partial aggregation) The head bucket of the circular array stores the call
 * outcomes of the current epoch second. The other partial aggregations store the call outcomes of
 * the previous {@code N-1} epoch seconds.
 * <p>
 * The sliding window does not store call outcomes (tuples) individually, but incrementally updates
 * partial aggregations (bucket) and a total aggregation. The total aggregation is updated
 * incrementally when a new call outcome is recorded. When the oldest bucket is evicted, the partial
 * total aggregation of that bucket is subtracted from the total aggregation. (Subtract-on-Evict)
 * <p>
 * The time to retrieve a Snapshot is constant 0(1), since the Snapshot is pre-aggregated and is
 * independent of the time window size. The space requirement (memory consumption) of this
 * implementation should be nearly constant O(n), since the call outcomes (tuples) are not stored
 * individually. Only {@code N} partial aggregations and 1 total aggregation are created.
 */
public class SlidingTimeWindowMetrics implements Metrics {

    final PartialAggregation[] partialAggregations; // 滑动窗口的桶
    private final int timeWindowSizeInSeconds;//滑动窗口大小
    private final TotalAggregation totalAggregation;// 该窗口内的总计数据
    private final Clock clock; // 时间
    int headIndex; // 滑动的指针

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates a new {@link SlidingTimeWindowMetrics} with the given clock and window of time.
     *
     * @param timeWindowSizeInSeconds the window time size in seconds
     * @param clock                   the {@link Clock} to use
     */
    public SlidingTimeWindowMetrics(int timeWindowSizeInSeconds, Clock clock) {
        this.clock = clock;
        this.timeWindowSizeInSeconds = timeWindowSizeInSeconds;
        this.partialAggregations = new PartialAggregation[timeWindowSizeInSeconds];
        this.headIndex = 0;
        // epochSecond 含义：时间戳，秒
        long epochSecond = clock.instant().getEpochSecond(); // clock的值其实是Clock.systemUTC()
        for (int i = 0; i < timeWindowSizeInSeconds; i++) {
            partialAggregations[i] = new PartialAggregation(epochSecond);// 把创建桶的时候的时间戳记录下来， 一开始就把时间记录下来了
            epochSecond++;// 每个桶的间隔是1秒， 妙啊！！！
        }
        this.totalAggregation = new TotalAggregation();
    }

    @Override
    public Snapshot record(long duration, TimeUnit durationUnit, Outcome outcome) {
        lock.lock();

        try {
            totalAggregation.record(duration, durationUnit, outcome);
            moveWindowToCurrentEpochSecond(getLatestPartialAggregation())
                    .record(duration, durationUnit, outcome);
            return new SnapshotImpl(totalAggregation);
        } finally {
            lock.unlock();
        }
    }

    public Snapshot getSnapshot() {
        lock.lock();

        try {
            // 之所以需要滑动窗口的原因是，取快照需要取最新的时间窗口里面的数据，则过期的时间的数据需要剔除，所以需要滑动（动词）窗口
            moveWindowToCurrentEpochSecond(getLatestPartialAggregation());
            return new SnapshotImpl(totalAggregation);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Moves the end of the time window to the current epoch second. The latest bucket of the
     * circular array is used to calculate how many seconds the window must be moved. The difference
     * is calculated by subtracting the epoch second from the latest bucket from the current epoch
     * second. If the difference is greater than the time window size, the time window size is
     * used.
     *
     * @param latestPartialAggregation the latest partial aggregation of the circular array
     */
    private PartialAggregation moveWindowToCurrentEpochSecond(
        PartialAggregation latestPartialAggregation) {
        long currentEpochSecond = clock.instant().getEpochSecond();
        long differenceInSeconds = currentEpochSecond - latestPartialAggregation.getEpochSecond();// latestPartialAggregation.getEpochSecond()：根据headIndex（旧的）找出来的哪个桶的时间戳
        // 比如currentEpochSecond 为1秒99 毫秒， latestPartialAggregation.getEpochSecond() 为1秒01毫秒
        // 他们之间的差值在以秒为单位的时候，是0；也就是这个时间差在一秒之内，那么一秒之内，还是使用当前指针所指向的桶（注意headIndex是确定需要滑动的时候再递增的）
        // 也就是在单位秒的时间内，所有的请求都是用的同一个桶记录
        if (differenceInSeconds == 0) {
            return latestPartialAggregation;
        }
        // 时间大于一秒了，需要滑动了
        long secondsToMoveTheWindow = Math.min(differenceInSeconds, timeWindowSizeInSeconds);
        PartialAggregation currentPartialAggregation;

        do {
            secondsToMoveTheWindow--;
            //移动得到下一个桶的下标
            moveHeadIndexByOne();
            //根据下标得到的下一个桶
            currentPartialAggregation = getLatestPartialAggregation();
            // 总计数据减去当前寻址到的桶上面的数据
            totalAggregation.removeBucket(currentPartialAggregation);
            // secondsToMoveTheWindow：当前相对时间与headIndex（没有移动之前的下标）桶的时间间隔
            // 当前相对时间-headIndex（没有移动之前的下标）桶的时间=secondsToMoveTheWindow
            // 即： 当前相对时间-secondsToMoveTheWindow=headIndex下标桶的时间
            // 在循环中secondsToMoveTheWindow在减减，则headIndex下标桶的时间在加加
            currentPartialAggregation.reset(currentEpochSecond - secondsToMoveTheWindow);
        } while (secondsToMoveTheWindow > 0);
        return currentPartialAggregation;
    }

    /**
     * Returns the head partial aggregation of the circular array.
     *
     * @return the head partial aggregation of the circular array
     *
     * 根据headIndex取桶
     */
    private PartialAggregation getLatestPartialAggregation() {
        return partialAggregations[headIndex];
    }

    /**
     * Moves the headIndex to the next bucket.
     *
     * 指针滑动，从1开始往后滑动，循环
     */
    void moveHeadIndexByOne() {
        this.headIndex = (headIndex + 1) % timeWindowSizeInSeconds;
    }
}