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
package org.rascalmpl.vscode.lsp.log;

import java.net.URI;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

public class LogRedirectConfiguration extends ConfigurationFactory {

    @Override
    public String[] getSupportedTypes() {
        return new String[] {"*"};
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, String name, URI configLocation) {
        return buildRedirectConfig();
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, String name, URI configLocation,
        ClassLoader loader) {
        return buildRedirectConfig();
    }


    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
        return buildRedirectConfig();
    }

    private static Configuration buildRedirectConfig() {
        Level targetLevel = Level.getLevel(System.getProperty("log4j2.level", "INFO"));
        if (targetLevel == null) {
            targetLevel = Level.INFO;
        }

        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setConfigurationName("DefaultLogger");
        builder.setStatusLevel(targetLevel);

        // Root logger
        var rootAppenderName = "RootConsole";

        builder.add(builder
            .newAppender(rootAppenderName, "CONSOLE")
            .addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR)
            .add(builder.newLayout("PatternLayout").addAttribute("pattern", "%d [%t] %p - %c %m%n")));

        builder.add(builder
            .newRootLogger(targetLevel)
            .add(builder.newAppenderRef(rootAppenderName)));

        // LSP4J logger
        var lsp4jAppenderName = "Lsp4jConsole";

        builder.add(builder
            .newAppender(lsp4jAppenderName, "CONSOLE")
            .addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR)
            .add(builder.newLayout("PatternLayout")
                .addAttribute("pattern", "%d [%t] %p - %c %m (change `LogRedirectConfiguration.java` to write exceptions)%n")

                // Suppress printing of strack traces by lsp4j to avoid choking the server. This would happen, for
                // instance, when the connection with the client is lost, but the server keeps trying to report
                // progress. Related issue: https://github.com/eclipse-lsp4j/lsp4j/issues/849.
                .addAttribute("alwaysWriteExceptions", false)));

        builder.add(builder
            .newLogger("org.eclipse.lsp4j.jsonrpc", Level.INFO)
            .add(builder.newAppenderRef(lsp4jAppenderName))
            .addAttribute("additivity", false));

        return builder.build();
    }
}
