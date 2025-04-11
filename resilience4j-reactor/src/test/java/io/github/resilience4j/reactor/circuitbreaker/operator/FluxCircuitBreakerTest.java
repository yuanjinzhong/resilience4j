/*
 * Copyright 2018 Julien Hoarau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.resilience4j.reactor.circuitbreaker.operator;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class FluxCircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @Before
    public void setUp() {
        circuitBreaker = mock(CircuitBreaker.class, RETURNS_DEEP_STUBS);
    }

    @Test
    public void shouldSubscribeToFluxJust() {
        given(circuitBreaker.tryAcquirePermission()).willReturn(true);
        given(circuitBreaker.getCurrentTimestamp()).willReturn(System.nanoTime());
        given(circuitBreaker.getTimestampUnit()).willReturn(TimeUnit.NANOSECONDS);

        StepVerifier.create(
            Flux.just("Event 1", "Event 2")
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker)))//在流被订阅时，应用断路器
            .expectNext("Event 1")
            .expectNext("Event 2")
            .verifyComplete();// 内部会订阅流（subcribe方法）

        // 由于这个流的订阅并没有发生异常，所以这里在flux里面元素被订阅完毕，才会执行断路器的onsuccess方法
        verify(circuitBreaker, times(1)).onSuccess(anyLong(), any(TimeUnit.class));
        // 由于这个流的订阅没有发生异常，所以onError方法不会被调用
        verify(circuitBreaker, never()).onError(anyLong(), any(TimeUnit.class), any(Throwable.class));
    }

    @Test
    public void shouldPropagateError() {
        given(circuitBreaker.tryAcquirePermission()).willReturn(true);
        given(circuitBreaker.getCurrentTimestamp()).willReturn(System.nanoTime());
        given(circuitBreaker.getTimestampUnit()).willReturn(TimeUnit.NANOSECONDS);

        StepVerifier.create(
            Flux.error(new IOException("BAM!"))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker)))
            .expectError(IOException.class)
            .verify(Duration.ofSeconds(1));

        // 由于这个流的订阅发生了异常，所以会执行断路器的onError方法，执行一次
        verify(circuitBreaker, times(1))
            .onError(anyLong(), any(TimeUnit.class), any(IOException.class));
        //由于这个流的订阅发生了异常，所以onResult方法不会被调用
        verify(circuitBreaker, never()).onResult(anyLong(), any(TimeUnit.class), any());
    }

    @Test
    public void shouldPropagateErrorWhenErrorNotOnSubscribe() {
        given(circuitBreaker.tryAcquirePermission()).willReturn(true);
        given(circuitBreaker.getCurrentTimestamp()).willReturn(System.nanoTime());
        given(circuitBreaker.getTimestampUnit()).willReturn(TimeUnit.NANOSECONDS);

        StepVerifier.create(
            Flux.error(new IOException("BAM!"), true)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker)))
            .expectError(IOException.class)
            .verify(Duration.ofSeconds(1));

        verify(circuitBreaker, times(1))
            .onError(anyLong(), any(TimeUnit.class), any(IOException.class));
        verify(circuitBreaker, never()).onSuccess(anyLong(), any(TimeUnit.class));
    }

    @Test
    public void shouldSubscribeToMonoJustTwice() {
        given(circuitBreaker.tryAcquirePermission()).willReturn(true);
        given(circuitBreaker.getCurrentTimestamp()).willReturn(System.nanoTime());
        given(circuitBreaker.getTimestampUnit()).willReturn(TimeUnit.NANOSECONDS);

        StepVerifier.create(Flux.just("Event 1", "Event 2")
            .flatMap(value -> Mono.just("Bla " + value)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))))
            .expectNext("Bla Event 1")
            .expectNext("Bla Event 2")
            .verifyComplete();

        verify(circuitBreaker, times(2)).onResult(anyLong(), any(TimeUnit.class), any(String.class));
        verify(circuitBreaker, never())
            .onError(anyLong(), any(TimeUnit.class), any(Throwable.class));
    }

    @Test
    public void shouldEmitErrorWithCircuitBreakerOpenException() {
        given(circuitBreaker.tryAcquirePermission()).willReturn(false);

        StepVerifier.create(
            Flux.just("Event 1", "Event 2")
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker)))
            .expectError(CallNotPermittedException.class)
            .verify(Duration.ofSeconds(1));

        verify(circuitBreaker, never())
            .onError(anyLong(), any(TimeUnit.class), any(Throwable.class));
        verify(circuitBreaker, never()).onSuccess(anyLong(), any(TimeUnit.class));
    }

    @Test
    public void shouldEmitCircuitBreakerOpenExceptionEvenWhenErrorNotOnSubscribe() {
        given(circuitBreaker.tryAcquirePermission()).willReturn(false);

        StepVerifier.create(
            Flux.error(new IOException("BAM!"), true)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker)))
            .expectError(CallNotPermittedException.class)
            .verify(Duration.ofSeconds(1));

        verify(circuitBreaker, never())
            .onError(anyLong(), any(TimeUnit.class), any(Throwable.class));
        verify(circuitBreaker, never()).onSuccess(anyLong(), any(TimeUnit.class));
    }

    @Test
    public void shouldEmitCircuitBreakerOpenExceptionEvenWhenErrorDuringSubscribe() {
        given(circuitBreaker.tryAcquirePermission()).willReturn(false);

        StepVerifier.create(
            Flux.error(new IOException("BAM!"))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker)))
            .expectError(CallNotPermittedException.class)
            .verify(Duration.ofSeconds(1));

        verify(circuitBreaker, never())
            .onError(anyLong(), any(TimeUnit.class), any(Throwable.class));
        verify(circuitBreaker, never()).onSuccess(anyLong(), any(TimeUnit.class));
    }

    @Test
    public void shouldReleasePermissionOnCancel() {
        given(circuitBreaker.tryAcquirePermission()).willReturn(true);

        StepVerifier.create(
            Flux.just("Event")
                .delayElements(Duration.ofDays(1))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker)))
            .expectSubscription()
            .thenCancel()
            .verify();

        verify(circuitBreaker, times(1)).releasePermission();
        verify(circuitBreaker, never())
            .onError(anyLong(), any(TimeUnit.class), any(Throwable.class));
        verify(circuitBreaker, never()).onSuccess(anyLong(), any(TimeUnit.class));
    }

    @Test
    public void shouldInvokeOnSuccessOnCancelWhenEventWasEmitted() {
        given(circuitBreaker.tryAcquirePermission()).willReturn(true);
        given(circuitBreaker.getCurrentTimestamp()).willReturn(System.nanoTime());
        given(circuitBreaker.getTimestampUnit()).willReturn(TimeUnit.NANOSECONDS);

        StepVerifier.create(
            Flux.just("Event1", "Event2", "Event3")
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker)))
            .expectSubscription()
            .thenRequest(1)
            .thenCancel()
            .verify();

        verify(circuitBreaker, never()).releasePermission();
        verify(circuitBreaker, never())
            .onError(anyLong(), any(TimeUnit.class), any(Throwable.class));
        verify(circuitBreaker, times(1)).onSuccess(anyLong(), any(TimeUnit.class));
    }
}
