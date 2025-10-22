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
package org.rascalmpl.vscode.lsp.parametric.capabilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

public class DynamicCapabilities {

    private static final Logger logger = LogManager.getLogger(DynamicCapabilities.class);

    private final LanguageClient client;
    private final List<AbstractDynamicCapability<?>> supportedCapabilities;
    private final Map<String, Registration> currentRegistrations = new ConcurrentHashMap<>();

    // private @MonotonicNonNull ClientCapabilities clientCapabilities;
    // private @MonotonicNonNull ServerCapabilities serverCapabilities;

    public DynamicCapabilities(LanguageClient client, List<AbstractDynamicCapability<? extends Object>> supportedCapabilities) {
        this.client = client;
        this.supportedCapabilities = supportedCapabilities;
    }

    // public void setStaticCapabilities(ClientCapabilities clientCapabilities, ServerCapabilities serverCapabilities) {
    //     // TODO Use these to determine whether we actually need/are allowed to register certain capabilities
    //     // If the client does not support dynamic regitration for a certain capability, do not register it. Instead, register it statically.
    //     this.clientCapabilities = clientCapabilities;
    //     this.serverCapabilities = serverCapabilities;
    // }

    // private CompletableFuture<List<Either<Registration, Unregistration>>> registration(AbstractDynamicCapability<?> cap, ILanguageContributions contribs) {
    //     return cap.options(contribs).thenApply(opts -> {
    //         var existing = currentRegistrations.get(cap.methodName());
    //         if (existing != null) {
    //             return List.of(unregistration(cap));
    //             actualOpts = cap.mergeOptions(existing.getRegisterOptions(), opts);

    //         }
    //         var mergedOpts = existing == null
    //             ? opts
    //             : cap.mergeOptions(existing.getRegisterOptions(), opts);
    //         return new Registration(cap.id(), cap.methodName(), mergedOpts);
    //     });
    // }

    public CompletableFuture<Void> registerCapabilities(ILanguageContributions contribs) {
        return CompletableFutureUtils.flatten(supportedCapabilities.stream().map(c -> updateRegistration(c, contribs)), LinkedList::new)
            .thenCompose(this::doRegistrationRequests);
    }

    public CompletableFuture<Void> unregisterCapabilties(ILanguageContributions contribs) {
        return CompletableFutureUtils.reduce(supportedCapabilities.stream().map(c -> removeRegistration(c, contribs)))
            .thenApply(us -> us.stream()
                .filter(Objects::nonNull)
                .map(u -> Either.<Registration, Unregistration>forRight(u))
                .collect(Collectors.toList()))
            .thenCompose(this::doRegistrationRequests);
    }

    private CompletableFuture<List<Either<Registration, Unregistration>>> updateRegistration(AbstractDynamicCapability<?> cap, ILanguageContributions contribs) {
        return cap.hasContribution(contribs).thenCompose(contributes -> {
            if (!contributes) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            return cap.options(contribs).thenApply(opts -> {
                var existing = currentRegistrations.get(cap.methodName());
                if (existing != null) {
                    return List.of(Either.forRight(unregistration(cap)), Either.forLeft(registration(cap, cap.mergeOptions(existing.getRegisterOptions(), opts))));
                }
                return List.of(Either.forLeft(registration(cap, opts)));
            });
        });
    }

    private CompletableFuture<@Nullable Unregistration> removeRegistration(AbstractDynamicCapability<?> cap, ILanguageContributions contribs) {
        return cap.hasContribution(contribs).thenApply(contributes -> contributes
            ? unregistration(cap)
            : null);
    }

    private Registration registration(AbstractDynamicCapability<?> cap, Object opts) {
        return new Registration(cap.id(), cap.methodName(), opts);
    }

    private Unregistration unregistration(AbstractDynamicCapability<?> cap) {
        return new Unregistration(cap.id(), cap.methodName());
    }

    private CompletableFuture<Void> doRegistrationRequests(List<Either<Registration, Unregistration>> tasks) {
        var pair = unzip(tasks);
        return unregister(pair.getRight()).thenCompose((_v) -> register(pair.getLeft()));
    }

    private CompletableFuture<Void> register(List<Registration> rs) {
        return client.registerCapability(new RegistrationParams(rs))
            .thenAccept((v) -> {
                rs.forEach(r -> currentRegistrations.put(r.getMethod(), r));
            });
    }

    private CompletableFuture<Void> unregister(List<Unregistration> us) {
        return client.unregisterCapability(new UnregistrationParams(us))
            .thenAccept((v) -> {
                us.forEach(u -> currentRegistrations.remove(u.getMethod()));
            });
    }

    private <L, R> Pair<List<L>, List<R>> unzip(List<Either<L, R>> eithers) {
        List<L> ls = new ArrayList<>();
        List<R> rs = new ArrayList<>();
        eithers.forEach(e -> e.map(ls::add, rs::add));
        return Pair.of(ls, rs);
    }

}
