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
package org.rascalmpl.vscode.lsp.parametric;

import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.vscode.lsp.parametric.LanguageRegistry.LanguageParameter;
import org.rascalmpl.vscode.lsp.util.NamedThreadPool;
import org.rascalmpl.vscode.lsp.util.locations.impl.TreeSearch;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IList;

/**
 * This is to call the language registry from Rascal (for example in REPL code)
 * @param services
 */
public class RascalInterface {
    private final @Nullable LanguageRegistry languageRegistry;
    private final IRascalMonitor monitor;
    private static final Logger logger = LogManager.getLogger(RascalInterface.class);

    @SuppressWarnings("resource")
    public RascalInterface(IRascalMonitor monitor) {
        this.monitor = monitor;
        LanguageRegistry registry = null;
        try {
            var property = System.getProperty("rascal.languageRegistryPort");
            if (property != null) {
                var port = Integer.parseInt(property);
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                Launcher<LanguageRegistry> clientLauncher = new Launcher.Builder<LanguageRegistry>()
                    .setLocalService(new Object())
                    .setRemoteInterface(LanguageRegistry.class)
                    .setInput(socket.getInputStream())
                    .setOutput(socket.getOutputStream())
                    .setExecutorService(NamedThreadPool.single("rascal-interface"))
                    .create();

                clientLauncher.startListening();
                registry = clientLauncher.getRemoteProxy();
            }
        } catch (Throwable e) {
            monitor.warning("Could not establish connection with Rascal language registry: " + e.getMessage(), URIUtil.unknownLocation());
        }
        languageRegistry = registry;
    }

    public void registerLanguage(IConstructor lang) {
        if (languageRegistry == null) {
            monitor.warning("Could not register language: no connection", URIUtil.unknownLocation());
        } else {
            try {
                logger.info("registerLanguage({})/before", lang);
                languageRegistry.registerLanguage(LanguageParameter.fromRascalValue(lang)).get(1, TimeUnit.MINUTES);
                logger.info("registerLanguage({})/after", lang);
            } catch (InterruptedException e) {
                monitor.warning("registerLanuage was interrupted: " + e.getMessage(), URIUtil.unknownLocation());
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                throw RuntimeExceptionFactory.io("Could not register language " + lang + "\n" + e);
            }
        }
    }

    public void unregisterLanguage(IConstructor lang) {
        if (languageRegistry == null) {
            monitor.warning("Could not unregister language: no connection", URIUtil.unknownLocation());
        } else {
            try {
                logger.info("unregisterLanguage({})/before", lang);
                languageRegistry.unregisterLanguage(LanguageParameter.fromRascalValue(lang)).get(1, TimeUnit.MINUTES);
                logger.info("unregisterLanguage({})/after", lang);
            } catch (InterruptedException e) {
                monitor.warning("unregisterLanuage was interrupted: " + e.getMessage(), URIUtil.unknownLocation());
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                throw RuntimeExceptionFactory.io("Could not unregister language " + lang + "\n" + e);
            }
        }
    }

    public IList computeFocusList(IConstructor input, IInteger line, IInteger column) {
        return TreeSearch.computeFocusList((ITree) input, line.intValue(), column.intValue());
    }
}
