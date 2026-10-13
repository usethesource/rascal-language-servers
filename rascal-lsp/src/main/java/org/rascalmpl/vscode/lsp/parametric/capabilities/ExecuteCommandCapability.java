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

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.DynamicRegistrationCapabilities;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;

public class ExecuteCommandCapability extends WorkspaceCapability<ExecuteCommandRegistrationOptions> {

    private final String metaCommandName;

    public ExecuteCommandCapability(String metaCommandName) {
        super("executeCommand");
        this.metaCommandName = metaCommandName;
    }

    @Override
    public CompletableFuture<Boolean> isProvidedBy(ICapabilityParams language) {
        return language.contributions().providesExecution();
    }

    @Override
    public @Nullable DynamicRegistrationCapabilities getCapabilities(WorkspaceClientCapabilities caps) {
        return caps.getExecuteCommand();
    }

    @Override
    public CompletableFuture<@Nullable ExecuteCommandRegistrationOptions> options(ICapabilityParams language) {
        var opts = new ExecuteCommandRegistrationOptions();
        opts.setCommands(Collections.singletonList(metaCommandName));
        return CompletableFuture.completedFuture(opts);
    }

    @Override
    public void registerStatically(ServerCapabilities result) {
        result.setExecuteCommandProvider(new ExecuteCommandOptions(Collections.singletonList(metaCommandName)));
    }

    @Override
    public ExecuteCommandRegistrationOptions mergeOptions(ExecuteCommandRegistrationOptions o1, ExecuteCommandRegistrationOptions o2) {
        var merged = new ExecuteCommandRegistrationOptions();
        merged.setCommands(Stream.concat(o1.getCommands().stream(), o2.getCommands().stream()).distinct().collect(Collectors.toList()));
        return merged;
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaCommandName, super.hashCode());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof ExecuteCommandCapability)) {
            return false;
        }
        var other = (ExecuteCommandCapability) obj;
        return Objects.equals(metaCommandName, other.metaCommandName) && super.equals(other);
    }

}
