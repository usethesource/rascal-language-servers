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

public class LogJsonConfiguration extends ConfigurationFactory {

    @Override
    protected String[] getSupportedTypes() {
        return new String[] {"*"};
    }



    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, String name, URI configLocation) {
        return buildConfiguration();
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, String name, URI configLocation,
            ClassLoader loader) {
        return buildConfiguration();
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
        return buildConfiguration();
    }

    private Configuration buildConfiguration() {
        Level targetLevel = Level.getLevel(System.getProperty("log4j2.level", "INFO"));
        if (targetLevel == null) {
            targetLevel = Level.INFO;
        }

        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setConfigurationName("JsonLogger");
        builder.setStatusLevel(targetLevel);

        builder.add(builder
            .newAppender("Console", ConsoleAppender.PLUGIN_NAME)
            .addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR)
            .add(builder.newLayout("JsonTemplateLayout")
                /* The JSON template has a max length (buffer size) of 8192 by default:
                   https://logging.apache.org/log4j/2.x/manual/systemproperties.html#log4j2.encoderByteBufferSize
                   If JSON documents are longer, the buffer will be flushed in between,
                   and the JSON arrives truncated on the client side. To prevent his, we cap the max message length. */
                .addAttribute("maxStringLength", 6000)
                .addAttribute("eventTemplateUri", "classpath:JsonLogTemplate.json")
            )
        );

        builder.add(builder
            .newRootLogger(targetLevel)
            .add(builder.newAppenderRef("Console"))
        );

        return builder.build();
    }

}
