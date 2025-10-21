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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.rascalmpl.vscode.lsp.parametric.ILanguageContributions;
import org.rascalmpl.vscode.lsp.util.concurrent.CompletableFutureUtils;

public abstract class AbstractDynamicCapability<OptionsType> {

    public abstract String id();

    public abstract String methodName();

    public abstract CompletableFuture<OptionsType> options(ILanguageContributions contribs);

    public abstract CompletableFuture<Boolean> hasContribution(ILanguageContributions contribs);

    public final CompletableFuture<Registration> registration(ILanguageContributions contribs) {
        return options(contribs).thenApply(opts -> new Registration(id(), methodName(), opts));
    }

    public final Unregistration unregistration() {
        return new Unregistration(id(), methodName());
    }

    public final CompletableFuture<Either<Registration, Unregistration>> updateRegistration(ILanguageContributions contribs) {
        return hasContribution(contribs).thenCompose(contributes -> contributes
            ? registration(contribs).thenApply(Either::forLeft)
            : CompletableFuture.completedFuture(Either.forRight(unregistration()))
        );
    }

    public static CompletableFuture<Void> updateRegistrations(LanguageClient client, ILanguageContributions contribs, List<AbstractDynamicCapability<?>> capabilities) {
        return CompletableFutureUtils.reduce(capabilities.stream().map(c -> c.updateRegistration(contribs)))
            .thenApply(ts -> {
                List<Registration> regs = new LinkedList<>();
                List<Unregistration> unregs = new LinkedList<>();

                for (var t : ts) {
                    t.map(regs::add, unregs::add);
                }

                return Pair.of(regs, unregs);
            })
            .thenCompose(ts -> client.unregisterCapability(new UnregistrationParams(ts.getRight())).thenApply(v -> ts))
            .thenCompose(ts -> client.registerCapability(new RegistrationParams(ts.getLeft())));
    }
}
