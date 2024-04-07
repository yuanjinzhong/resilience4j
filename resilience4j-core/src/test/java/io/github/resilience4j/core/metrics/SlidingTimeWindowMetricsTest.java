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

import com.statemachinesystems.mockclock.MockClock;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class SlidingTimeWindowMetricsTest {

    @Test
    public void checkInitialBucketCreation() {
        Clock clock1 = Clock.systemUTC();
        long epochSecond2 = clock1.instant().getEpochSecond(); // clock的值其实是Clock.systemUTC()

        MockClock clock = MockClock.at(2019, 8, 4, 12, 0, 0, ZoneId.of("UTC"));
        SlidingTimeWindowMetrics metrics = new SlidingTimeWindowMetrics(5, clock);

        PartialAggregation[] buckets = metrics.partialAggregations;

        long epochSecond = clock.instant().getEpochSecond();
        for (int i = 0; i < buckets.length; i++) {
            PartialAggregation bucket = buckets[i];
            assertThat(bucket.getEpochSecond()).isEqualTo(epochSecond + i);
        }

        Snapshot snapshot = metrics.getSnapshot();

        assertThat(snapshot.getTotalNumberOfCalls()).isZero();
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isZero();
        assertThat(snapshot.getNumberOfFailedCalls()).isZero();
        assertThat(snapshot.getTotalNumberOfSlowCalls()).isZero();
        assertThat(snapshot.getNumberOfSlowSuccessfulCalls()).isZero();
        assertThat(snapshot.getNumberOfSlowFailedCalls()).isZero();
        assertThat(snapshot.getTotalDuration().toMillis()).isZero();
        assertThat(snapshot.getAverageDuration().toMillis()).isZero();
        assertThat(snapshot.getFailureRate()).isZero();
    }

    @Test
    public void testRecordSuccess() {
        MockClock clock = MockClock.at(2019, 8, 4, 12, 0, 0, ZoneId.of("UTC"));
        Metrics metrics = new SlidingTimeWindowMetrics(5, clock);

        Snapshot snapshot = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(snapshot.getTotalNumberOfCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfFailedCalls()).isZero();
        assertThat(snapshot.getTotalNumberOfSlowCalls()).isZero();
        assertThat(snapshot.getNumberOfSlowSuccessfulCalls()).isZero();
        assertThat(snapshot.getNumberOfSlowFailedCalls()).isZero();
        assertThat(snapshot.getTotalDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getAverageDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getFailureRate()).isZero();
    }

    @Test
    public void testRecordError() {
        MockClock clock = MockClock.at(2019, 8, 4, 12, 0, 0, ZoneId.of("UTC"));
        Metrics metrics = new SlidingTimeWindowMetrics(5, clock);

        Snapshot snapshot = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.ERROR);
        assertThat(snapshot.getTotalNumberOfCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isZero();
        assertThat(snapshot.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(snapshot.getTotalNumberOfSlowCalls()).isZero();
        assertThat(snapshot.getNumberOfSlowSuccessfulCalls()).isZero();
        assertThat(snapshot.getNumberOfSlowFailedCalls()).isZero();
        assertThat(snapshot.getTotalDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getAverageDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getFailureRate()).isEqualTo(100);
    }

    @Test
    public void testRecordSlowSuccess() {
        MockClock clock = MockClock.at(2019, 8, 4, 12, 0, 0, ZoneId.of("UTC"));
        Metrics metrics = new SlidingTimeWindowMetrics(5, clock);

        Snapshot snapshot = metrics
            .record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SLOW_SUCCESS);
        assertThat(snapshot.getTotalNumberOfCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfFailedCalls()).isZero();
        assertThat(snapshot.getTotalNumberOfSlowCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfSlowSuccessfulCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfSlowFailedCalls()).isZero();
        assertThat(snapshot.getTotalDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getAverageDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getFailureRate()).isZero();
    }

    @Test
    public void testSlowCallsPercentage() {

        MockClock clock = MockClock.at(2019, 8, 4, 12, 0, 0, ZoneId.of("UTC"));
        Metrics metrics = new SlidingTimeWindowMetrics(5, clock);

        metrics.record(10000, TimeUnit.MILLISECONDS, Metrics.Outcome.SLOW_SUCCESS);
        metrics.record(10000, TimeUnit.MILLISECONDS, Metrics.Outcome.SLOW_ERROR);
        metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);

        Snapshot snapshot = metrics.getSnapshot();
        assertThat(snapshot.getTotalNumberOfCalls()).isEqualTo(5);
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isEqualTo(4);
        assertThat(snapshot.getTotalNumberOfSlowCalls()).isEqualTo(2);
        assertThat(snapshot.getNumberOfSlowSuccessfulCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfSlowFailedCalls()).isEqualTo(1);
        assertThat(snapshot.getTotalDuration().toMillis()).isEqualTo(20300);
        assertThat(snapshot.getAverageDuration().toMillis()).isEqualTo(4060);
        assertThat(snapshot.getSlowCallRate()).isEqualTo(40f);
    }

    @Test
    public void testMoveHeadIndexByOne() {
        MockClock clock = MockClock.at(2019, 8, 4, 12, 0, 0, ZoneId.of("UTC"));
        SlidingTimeWindowMetrics metrics = new SlidingTimeWindowMetrics(3, clock);

        assertThat(metrics.headIndex).isZero();

        metrics.moveHeadIndexByOne();

        assertThat(metrics.headIndex).isEqualTo(1);

        metrics.moveHeadIndexByOne();

        assertThat(metrics.headIndex).isEqualTo(2);

        metrics.moveHeadIndexByOne();

        assertThat(metrics.headIndex).isZero();

        metrics.moveHeadIndexByOne();

        assertThat(metrics.headIndex).isEqualTo(1);

    }

    @Test
    public void shouldClearSlidingTimeWindowMetrics() {
        MockClock clock = MockClock.at(2019, 8, 4, 12, 0, 0, ZoneId.of("UTC"));
        Metrics metrics = new SlidingTimeWindowMetrics(5, clock);

        Snapshot snapshot = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.ERROR);
        assertThat(snapshot.getTotalNumberOfCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isZero();
        assertThat(snapshot.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(snapshot.getTotalDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getAverageDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getFailureRate()).isEqualTo(100);

        clock.advanceByMillis(100);

        snapshot = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(snapshot.getTotalNumberOfCalls()).isEqualTo(2);
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(snapshot.getTotalDuration().toMillis()).isEqualTo(200);
        assertThat(snapshot.getAverageDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getFailureRate()).isEqualTo(50);

        clock.advanceByMillis(700);

        snapshot = metrics.record(1000, TimeUnit.MILLISECONDS, Metrics.Outcome.SLOW_ERROR);
        assertThat(snapshot.getTotalNumberOfCalls()).isEqualTo(3);
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(snapshot.getNumberOfSlowFailedCalls()).isEqualTo(1);
        assertThat(snapshot.getTotalDuration().toMillis()).isEqualTo(1200);
        assertThat(snapshot.getAverageDuration().toMillis()).isEqualTo(400);

        clock.advanceBySeconds(4);

        snapshot = metrics.getSnapshot();
        assertThat(snapshot.getTotalNumberOfCalls()).isEqualTo(3);
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(snapshot.getNumberOfSlowFailedCalls()).isEqualTo(1);
        assertThat(snapshot.getTotalDuration().toMillis()).isEqualTo(1200);
        assertThat(snapshot.getAverageDuration().toMillis()).isEqualTo(400);

        clock.advanceBySeconds(1);

        snapshot = metrics.getSnapshot();

        assertThat(snapshot.getTotalNumberOfCalls()).isZero();
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isZero();
        assertThat(snapshot.getNumberOfFailedCalls()).isZero();
        assertThat(snapshot.getNumberOfSlowFailedCalls()).isZero();
        assertThat(snapshot.getTotalDuration().toMillis()).isZero();
        assertThat(snapshot.getAverageDuration().toMillis()).isZero();
        assertThat(snapshot.getFailureRate()).isZero();

        clock.advanceByMillis(100);

        snapshot = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(snapshot.getTotalNumberOfCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfFailedCalls()).isZero();
        assertThat(snapshot.getNumberOfSlowFailedCalls()).isZero();
        assertThat(snapshot.getTotalDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getAverageDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getFailureRate()).isZero();

        clock.advanceBySeconds(5);

        snapshot = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(snapshot.getTotalNumberOfCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfFailedCalls()).isZero();
        assertThat(snapshot.getNumberOfSlowFailedCalls()).isZero();
        assertThat(snapshot.getTotalDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getAverageDuration().toMillis()).isEqualTo(100);
        assertThat(snapshot.getFailureRate()).isZero();
    }

    @Test
    public void testSlidingTimeWindowMetrics() {
        MockClock clock = MockClock.at(2019, 8, 4, 12, 0, 0, ZoneId.of("UTC"));
        Metrics metrics = new SlidingTimeWindowMetrics(5, clock);

        Snapshot result = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.ERROR);

        assertThat(result.getTotalNumberOfCalls()).isEqualTo(1);
        assertThat(result.getNumberOfSuccessfulCalls()).isZero();
        assertThat(result.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(result.getTotalDuration().toMillis()).isEqualTo(100);

        clock.advanceByMillis(100);

        result = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(result.getTotalNumberOfCalls()).isEqualTo(2);
        assertThat(result.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(result.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(result.getTotalDuration().toMillis()).isEqualTo(200);

        clock.advanceByMillis(100);

        result = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(result.getTotalNumberOfCalls()).isEqualTo(3);
        assertThat(result.getNumberOfSuccessfulCalls()).isEqualTo(2);
        assertThat(result.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(result.getTotalDuration().toMillis()).isEqualTo(300);

        clock.advanceBySeconds(1);

        result = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(result.getTotalNumberOfCalls()).isEqualTo(4);
        assertThat(result.getNumberOfSuccessfulCalls()).isEqualTo(3);
        assertThat(result.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(result.getTotalDuration().toMillis()).isEqualTo(400);

        clock.advanceBySeconds(1);

        result = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(result.getTotalNumberOfCalls()).isEqualTo(5);
        assertThat(result.getNumberOfSuccessfulCalls()).isEqualTo(4);
        assertThat(result.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(result.getTotalDuration().toMillis()).isEqualTo(500);

        clock.advanceBySeconds(1);

        result = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.ERROR);
        assertThat(result.getTotalNumberOfCalls()).isEqualTo(6);
        assertThat(result.getNumberOfSuccessfulCalls()).isEqualTo(4);
        assertThat(result.getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(result.getTotalDuration().toMillis()).isEqualTo(600);

        clock.advanceBySeconds(1);

        result = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(result.getTotalNumberOfCalls()).isEqualTo(7);
        assertThat(result.getNumberOfSuccessfulCalls()).isEqualTo(5);
        assertThat(result.getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(result.getTotalDuration().toMillis()).isEqualTo(700);

        clock.advanceBySeconds(1);

        result = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(result.getTotalNumberOfCalls()).isEqualTo(5);
        assertThat(result.getNumberOfSuccessfulCalls()).isEqualTo(4);
        assertThat(result.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(result.getTotalDuration().toMillis()).isEqualTo(500);

        clock.advanceBySeconds(1);

        result = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(result.getTotalNumberOfCalls()).isEqualTo(5);
        assertThat(result.getNumberOfSuccessfulCalls()).isEqualTo(4);
        assertThat(result.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(result.getTotalDuration().toMillis()).isEqualTo(500);

        clock.advanceBySeconds(5);

        result = metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
        assertThat(result.getTotalNumberOfCalls()).isEqualTo(1);
        assertThat(result.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(result.getNumberOfFailedCalls()).isZero();
        assertThat(result.getTotalDuration().toMillis()).isEqualTo(100);
    }

    @Test
    public  void  测试滑动窗口滑动的逻辑(){

        Clock clock = Mockito.mock(Clock.class);
        Instant instant = Mockito.mock(Instant.class);
        Mockito.when(clock.instant()).thenReturn(instant);

        // 等价于相对时间1秒的时候创建滑动窗口
        Mockito.when(clock.instant().getEpochSecond()).thenReturn(1L);

        SlidingTimeWindowMetrics metrics = new SlidingTimeWindowMetrics(5, clock);

        // 滑动窗口创建完，每个桶的时间是当前相对时间依次+1
        PartialAggregation[] buckets = metrics.partialAggregations;
        long epochSecond = clock.instant().getEpochSecond();
        for (int i = 0; i < buckets.length; i++) {
            PartialAggregation bucket = buckets[i];
            assertThat(bucket.getEpochSecond()).isEqualTo(epochSecond + i);
        }

        metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.ERROR);
        for (int i = 0; i < buckets.length; i++) {
            PartialAggregation bucket = buckets[i];
            System.out.println(String.format("第%s个窗口的时间戳：%s",i,bucket.getEpochSecond()));
        }

        //clock.advanceBySeconds(6);
        //等价于过了6秒又来了一个请求，整个滑动窗口会往后移动的
        Mockito.when(clock.instant().getEpochSecond()).thenReturn(7L);
        System.out.println("***********************************时间过了6秒，当前相对时间第7秒**********对比出窗口滑动的逻辑*************************");


        metrics.record(100, TimeUnit.MILLISECONDS, Metrics.Outcome.ERROR);
        // 滑动窗口创建完，每个桶的时间是当前相对时间依次+1
        PartialAggregation[] buckets2 = metrics.partialAggregations;
        for (int i = 0; i < buckets2.length; i++) {
            PartialAggregation bucket = buckets2[i];
            System.out.println(String.format("第%s个窗口的时间戳：%s",i,bucket.getEpochSecond()));
        }

    }

}
