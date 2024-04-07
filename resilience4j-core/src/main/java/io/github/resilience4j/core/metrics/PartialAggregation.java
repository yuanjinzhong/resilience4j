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

public class PartialAggregation extends AbstractAggregation {

    private long epochSecond;// 时间戳， 单位秒

    PartialAggregation(long epochSecond) {
        this.epochSecond = epochSecond;
    }

    void reset(long epochSecond) {
        this.epochSecond = epochSecond;
        this.totalDurationInMillis = 0;
        this.numberOfSlowCalls = 0;
        this.numberOfFailedCalls = 0;
        this.numberOfSlowFailedCalls = 0;
        this.numberOfCalls = 0;
    }

    public long getEpochSecond() {
        return epochSecond;
    }
}