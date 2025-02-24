/*
 * Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp.util.concurrent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

public class DebouncedSupplierTests {
    private static final Duration EXTRA_SMALL_DELAY = Duration.ofMillis(250);
    private static final Duration SMALL_DELAY = Duration.ofMillis(500);
    private static final Duration MEDIUM_DELAY = Duration.ofMillis(1000);
    private static final Duration LARGE_DELAY = Duration.ofMillis(2000);

    private static final Duration DEFAULT_SUPPLY_TIME = LARGE_DELAY;

    // Default tolerance when comparing expected/actual execution times
    private static final Duration DELTA = EXTRA_SMALL_DELAY;

    private static DebouncedSupplier<String> newDebouncedSupplier(
            AtomicReference<String> input) {
        return newDebouncedSupplier(input, DEFAULT_SUPPLY_TIME);
    }

    private static DebouncedSupplier<String> newDebouncedSupplier(
            AtomicReference<String> input, Duration supplyTime) {

        return new DebouncedSupplier<>(() -> {
            return CompletableFuture.supplyAsync(() -> {
                var output = input.get();
                sleep(supplyTime); // Simulate supplier
                return output;
            });
        });
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static long timeMillis(Runnable r) {
        var begin = System.currentTimeMillis();
        r.run();
        var end = System.currentTimeMillis();
        return end - begin;
    }

    @Test
    public void testNoDelay() {
        var input = new AtomicReference<>("");
        var supplier = newDebouncedSupplier(input);

        var expectedTimeMillis = DEFAULT_SUPPLY_TIME.toMillis();
        var actualTimeMillis = timeMillis(() -> {
            input.set("foo");
            supplier
                .get()
                .thenAccept(output -> Assert.assertEquals("foo", output))
                .join();
        });

        Assert.assertEquals(expectedTimeMillis, actualTimeMillis, DELTA.toMillis());
    }

    @Test
    public void testNoDelayAfterDelay() {
        var input = new AtomicReference<>("");
        var supplier = newDebouncedSupplier(input);

        var expectedTimeMillis = SMALL_DELAY.plus(DEFAULT_SUPPLY_TIME).toMillis();

        var actualTimeMillis = timeMillis(() -> {
            input.set("foo1");
            var future1 = supplier
                .get(MEDIUM_DELAY)
                .thenAccept(output -> Assert.assertEquals("bar", output));

            sleep(SMALL_DELAY);

            input.set("foo2");
            var future2 = supplier
                .get()
                .thenAccept(output -> Assert.assertEquals("bar", output));

            input.set("bar");
            future1.join();
            future2.join();
        });

        Assert.assertEquals(expectedTimeMillis, actualTimeMillis, DELTA.toMillis());

    }

    @Test
    public void testFewSequential() {
        var input = new AtomicReference<>("");
        var supplier = newDebouncedSupplier(input);

        var expectedTimeMillis = Duration.ZERO
            .plus(MEDIUM_DELAY).plus(DEFAULT_SUPPLY_TIME) // First get
            .plus(MEDIUM_DELAY).plus(DEFAULT_SUPPLY_TIME) // Second get
            .toMillis();

        var actualTimeMillis = timeMillis(() -> {
            input.set("foo1");
            supplier
                .get(MEDIUM_DELAY)
                .thenAccept(output -> Assert.assertEquals("foo1", output))
                .join();

            input.set("foo2");
            supplier
                .get(MEDIUM_DELAY)
                .thenAccept(output -> Assert.assertEquals("foo2", output))
                .join();
        });

        Assert.assertEquals(expectedTimeMillis, actualTimeMillis, DELTA.toMillis());
    }

    @Test
    public void testManySequential() {
        var input = new AtomicReference<>("");
        var supplier = newDebouncedSupplier(input);
        var n = 5;

        var expectedTimeMillis = Duration.ZERO
            .plus(MEDIUM_DELAY).plus(DEFAULT_SUPPLY_TIME).multipliedBy(n) // First get, ..., last get
            .toMillis();

        var actualTimeMillis = timeMillis(() -> {
            for (var i = 0; i < n; i++) {
                var fooi = "foo" + i;
                input.set(fooi);
                supplier
                    .get(MEDIUM_DELAY)
                    .thenAccept(output -> Assert.assertEquals(fooi, output))
                    .join();
            }
        });

        Assert.assertEquals(expectedTimeMillis, actualTimeMillis, DELTA.toMillis());
    }

    @Test
    public void testFewConcurrent() {
        var input = new AtomicReference<>("");
        var supplier = newDebouncedSupplier(input);

        var expectedTimeMillis = Duration.ZERO
            .plus(SMALL_DELAY) // First get until second get
            .plus(MEDIUM_DELAY).plus(DEFAULT_SUPPLY_TIME) // Second get
            .toMillis();

        var actualTimeMillis = timeMillis(() -> {
            input.set("foo1");
            var future1 = supplier
                .get(MEDIUM_DELAY)
                .thenAccept(output -> Assert.assertEquals("bar", output));

            sleep(SMALL_DELAY);

            input.set("foo2");
            var future2 = supplier
                .get(MEDIUM_DELAY)
                .thenAccept(output -> Assert.assertEquals("bar", output));

            input.set("bar");
            future1.join();
            future2.join();
        });

        Assert.assertEquals(expectedTimeMillis, actualTimeMillis, DELTA.toMillis());
    }

    @Test
    public void testManyConcurrent() {
        var input = new AtomicReference<>("");
        var supplier = newDebouncedSupplier(input);
        var n = 10;

        var expectedTimeMillis = Duration.ZERO
            .plus(SMALL_DELAY).multipliedBy(n - 1) // First get until second get, ..., (n-1)th get until last get
            .plus(MEDIUM_DELAY).plus(DEFAULT_SUPPLY_TIME) // Last get
            .toMillis();

        var actualTimeMillis = timeMillis(() -> {
            var futures = new ArrayList<CompletableFuture<Void>>(n);

            for (var i = 0; i < n; i++) {
                if (i > 0) {
                    sleep(SMALL_DELAY);
                }

                input.set("foo" + i);
                var future = supplier
                    .get(MEDIUM_DELAY)
                    .thenAccept(output -> Assert.assertEquals("bar", output));

                futures.add(future);
            }

            input.set("bar");
            for (var f : futures) {
                f.join();
            }
        });

        Assert.assertEquals(expectedTimeMillis, actualTimeMillis, DELTA.toMillis());
    }
}
