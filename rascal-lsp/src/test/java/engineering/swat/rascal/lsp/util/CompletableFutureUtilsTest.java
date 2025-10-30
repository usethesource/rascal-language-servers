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
package engineering.swat.rascal.lsp.util;

import static org.junit.Assert.assertEquals;
import static org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils.flatten;
import static org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils.reduce;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.apache.commons.compress.utils.Sets;
import org.junit.Before;
import org.junit.Test;
import org.rascalmpl.values.IRascalValueFactory;

import io.usethesource.vallang.IList;

public class CompletableFutureUtilsTest {
    private List<CompletableFuture<Integer>> futList;

    private static final IRascalValueFactory VF = IRascalValueFactory.getInstance();

    @Before
    public void setUp() {
        futList = new LinkedList<>();
        futList.add(CompletableFuture.completedFuture(1));
        futList.add(CompletableFuture.completedFuture(2));
        futList.add(CompletableFuture.completedFuture(3));
    }

    @Test
    public void reduceList() throws InterruptedException, ExecutionException {
        CompletableFuture<List<Integer>> reduced = reduce(futList);
        assertEquals(Arrays.asList(1, 2, 3), reduced.get());
    }

    @Test
    public void reduceStream() throws InterruptedException, ExecutionException {
        CompletableFuture<List<Integer>> reduced = reduce(futList.stream());
        assertEquals(Arrays.asList(1, 2, 3), reduced.get());
    }

    @Test
    public void reduceToSet() throws InterruptedException, ExecutionException {
        futList.add(CompletableFuture.completedFuture(1));
        CompletableFuture<Set<Integer>> reduced = reduce(futList, Sets::newHashSet, Sets::newHashSet, this::setUnion);
        assertEquals(Sets.newHashSet(1, 2, 3), reduced.get());
    }

    @Test
    public void reduceAndSum() throws InterruptedException, ExecutionException {
        var listFutList = List.of(
            CompletableFuture.completedFuture(List.of(1, 2)),
            CompletableFuture.completedFuture(List.of(3, 4))
        );

        CompletableFuture<Integer> reduced = reduce(listFutList,
            () -> 0,
            l -> l.stream().reduce(Integer::sum).orElse(0),
            Integer::sum
        );
        assertEquals(10, reduced.get().intValue());
    }

    @Test
    public void reduceAndAddList() throws InterruptedException, ExecutionException {
        CompletableFuture<Integer> reduced = reduce(futList, () -> 0, Function.identity(), Integer::sum);
        assertEquals(6, reduced.get().intValue());
    }

    @Test
    public void reduceAndAddStream() throws InterruptedException, ExecutionException {
        CompletableFuture<Integer> reduced = reduce(futList.stream(), () -> 0, Function.identity(), Integer::sum);
        assertEquals(6, reduced.get().intValue());
    }

    @Test
    public void flattenRascalList() throws InterruptedException, ExecutionException {
        var inner = VF.list(VF.integer(1), VF.integer(2), VF.integer(3));
        var outer = List.of(CompletableFuture.completedFuture(inner), CompletableFuture.completedFuture(inner));

        CompletableFuture<IList> reduced = flatten(outer.stream(), VF::list, IList::concat);
        assertEquals(VF.list(VF.integer(1), VF.integer(2), VF.integer(3), VF.integer(1), VF.integer(2), VF.integer(3)), reduced.get());
    }

    private <T> Set<T> setUnion(Set<T> l, Set<T> r) {
        var s = new HashSet<>(l);
        s.addAll(r);
        return s;
    }
}
