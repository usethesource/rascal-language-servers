/*
 * Copyright (c) 2018-2023, NWO-I CWI and Swat.engineering
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
package org.rascalmpl.vscode.lsp.util;

import org.apache.logging.log4j.Logger;
import org.rascalmpl.debug.IRascalMonitor;

import io.usethesource.vallang.ISourceLocation;

public class LoggingMonitor implements IRascalMonitor {
    private final Logger target;

    public LoggingMonitor(Logger target) {
        this.target = target;
    }

    @Override
    public void warning(String message, ISourceLocation src) {
        target.warn("{} : {}", src, message);
    }

    @Override
    public void jobStart(String name, int workShare, int totalWork) {
        target.trace(name);
    }

    @Override
    public void jobStep(String name, String message, int inc) {
        // ignore
    }

    @Override
    public int jobEnd(String name, boolean succeeded) {
        return 0;
    }

    @Override
    public boolean jobIsCanceled(String name) {
        return false;
    }

    @Override
    public void jobTodo(String name, int work) {
        // ignore
    }
}
