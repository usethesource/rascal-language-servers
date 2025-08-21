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

enum LogLevel {
    fatal = "FATAL",
    error = "ERROR",
    warn = "WARN",
    info = "INFO",
    debug = "DEBUG",
    trace = "TRACE",
}

interface LogMessage {
    readonly timestamp: Date;
    readonly level: LogLevel;
    readonly message: string;
    readonly threadName: string;
    readonly loggerName: string;
}

export class JsonParserOutputChannel implements vscode.OutputChannel {
    readonly title: string;

    private readonly logChannel: vscode.LogOutputChannel;

    constructor(name: string) {
        this.logChannel = vscode.window.createOutputChannel(name, {log: true});
        this.title = name;
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

    private printPayload(payload: string): void {
        for (const line of payload.trim().split("\n")) {
            try {
                this.printJsonPayLoad(line);
            } catch (e) {
                if (e instanceof SyntaxError) {
                    // this was not JSON at all
                    this.printNonJsonPayload(line);
                } else {
                    this.logChannel.appendLine(`Error while logging ${line}: ${e}`);
                    throw e;
                }
            }
        }
    }

    private printNonJsonPayload(payload: string): void {
        // try to find the log level and message
        const log4jDefault = /^\s*[^\s]+\s+(?<thread>\w+)\s+(?<level>\w+)\s+(?<message>.*)$/si;

        const matches = log4jDefault.exec(payload);
        if (matches?.groups && "level" in matches.groups && "message" in matches.groups) {
            const logLevel = matches.groups["level"].toLocaleUpperCase() as LogLevel;
            const message = `[${matches.groups["thread"]}] ${matches.groups["message"]}`;
            this.printLogOutput(logLevel, message);
        } else {
            // we do not understand the structure of this log message; just print it as-is
            this.logChannel.appendLine(payload);
        }
    }

    private printJsonPayLoad(payload: string): void {
        const json = JSON.parse(payload) as LogMessage;
        // no timestamp or log level, since LogOutputChannel functions add those
        const log = `[${json.threadName}] ${json.loggerName} ${json.message}`;
        this.printLogOutput(json.level, log);
    }

    append(payload: string): void {
        this.printPayload(payload);
    }

    appendLine(payload: string): void {
        this.printPayload(payload);
    }

    show(preserveFocus?: unknown): void {
        this.logChannel.show(preserveFocus as boolean);
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
        this.logChannel.dispose();
    }
}
