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
import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';

/**
 * Log levels that match log4j levels.
 * https://logging.apache.org/log4j/2.x/manual/customloglevels.html
 * Note: same order as {@link vscode.LogLevel}, so we can convert easily.
 */
enum LogLevel {
    off = "OFF",
    trace = "TRACE",
    debug = "DEBUG",
    info = "INFO",
    warn = "WARN",
    error = "ERROR",
    fatal = "FATAL",
}

class LogMessage {
    constructor(
        public readonly timestamp: Date,
        public readonly level: LogLevel,
        public readonly message: string,
        public readonly threadName: string,
        public readonly loggerName: string) {}

    static is(json: object): json is LogMessage {
        const log = json as LogMessage;
        return log.timestamp !== undefined
            && log.level !== undefined
            && log.message !== undefined
            && log.threadName !== undefined
            && log.loggerName !== undefined;
    }
}

/**
 * A VSCode OutputChannel that parses JSON log messages and delegates them to the richer LogOutputChannel.
 * This offers enhanced features, such as filtering by log level/thread, and better formatting.
 *
 * The expected JSON format is:
 * {
 *   "timestamp": "2024-06-10T12:34:56.789Z",
 *   "level": "INFO",
 *   "message": "This is a log message",
 *   "threadName": "main",
 *   "loggerName": "org.rascalmpl"
 * }
 * This format is compatible with log4j's JSONLayout as configured in
 * org.rascalmpl.vscode.lsp.log.LogJsonConfiguration.
 *
 * Received messages that are not JSON or not in the expected format are printed as-is.
 */
export class JsonParserOutputChannel implements vscode.OutputChannel {
    readonly name: string;

    private readonly logChannel: vscode.LogOutputChannel;
    private client: LanguageClient;

    private readonly disposables: Array<vscode.Disposable> = [];

    constructor(name: string) {
        this.logChannel = vscode.window.createOutputChannel(name, {log: true});
        this.disposables.push(this.logChannel);

        this.logChannel.onDidChangeLogLevel(this.didChangeLogLevel, this, this.disposables);

        this.name = name;
    }

    setClient(client: LanguageClient) {
        this.client = client;

        // Initialize log level
        this.didChangeLogLevel(this.logChannel.logLevel);
    }

    private didChangeLogLevel(level: vscode.LogLevel) {
        // since vscode.LogLevel is a subset of LogLevel, and the same order, we can convert easily
        const newLevel = Object.values(LogLevel)[level];
        if (!this.client) {
            // Do nothing for now. this::setClient will take care of this once the client is available.
            return;
        }
        this.client.sendNotification("rascal/logLevel", newLevel);
    }

    getLogChannel() {
        return this.logChannel;
    }

    private printLogOutput(loglevel: LogLevel, message: string) {
        switch (loglevel) {
            case LogLevel.fatal: // intentional fall-trough
            case LogLevel.error: {
                this.logChannel.error(message);
                break;
            }
            case LogLevel.warn: {
                this.logChannel.warn(message);
                break;
            }
            case LogLevel.info: {
                this.logChannel.info(message);
                break;
            }
            case LogLevel.debug: {
                this.logChannel.debug(message);
                break;
            }
            case LogLevel.trace: {
                this.logChannel.trace(message);
                break;
            }
            default: {
                this.logChannel.appendLine(`${loglevel} ${message}`);
            }
        }
    }

    private printPayloads(payload: string): void {
        for (const line of payload.trim().split("\n")) {
            try {
                const log = JSON.parse(line);
                if (LogMessage.is(log)) {
                    // no timestamp or log level, since LogOutputChannel functions add those
                    this.printLogOutput(log.level, this.formatMessage(log.threadName, log.timestamp, log.message, log.loggerName));
                } else {
                    // JSON, but not in the expected format
                    this.logChannel.error(`Unexpected JSON log format: ${line}`);
                }
            } catch (e) {
                if (e instanceof SyntaxError) {
                    // Regular, non-JSON log from somewhere
                    this.logChannel.appendLine(line);
                }
            }
        }
    }

    private formatServerTime(date: Date | string): number {
        if (typeof date === "string") {
            date = new Date(date);
        }
        return date.getTime();
    }

    private formatMessage(thread: string, originalTime: Date | string, message: string, loggerName?: string) {
        const loggerPart = loggerName ? ` ${loggerName}` : "";
        return `[${thread}] ${loggerPart} ${message} (@${this.formatServerTime(originalTime)} ms)`;
    }

    append(payload: string): void {
        this.printPayloads(payload);
    }

    appendLine(payload: string): void {
        this.printPayloads(payload);
    }

    show(columnOrPreserveFocus?: vscode.ViewColumn | boolean, preserveFocus?: boolean): void {
        if (typeof columnOrPreserveFocus === "boolean" || columnOrPreserveFocus === undefined) {
            this.logChannel.show(columnOrPreserveFocus);
        } else {
            this.logChannel.show(preserveFocus);
        }
    }

    replace(value: string): void {
        this.logChannel.replace(value);
    }
    clear(): void {
        this.logChannel.clear();
    }
    hide(): void {
        this.logChannel.hide();
    }
    dispose(): void {
        vscode.Disposable.from(...this.disposables).dispose();
    }
}
