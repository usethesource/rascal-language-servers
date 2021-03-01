package org.rascalmpl.vscode.lsp;

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

        builder.add(builder
            .newAppender("Console", "CONSOLE")
            .addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR)
            .add(builder.newLayout("PatternLayout").addAttribute("pattern", "%d [%t] %p - %c %m%n")));

        builder.add(builder
            .newRootLogger(targetLevel)
            .add(builder.newAppenderRef("Console")));

        return builder.build();
    }
}
