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
package org.rascalmpl.vscode.lsp.util;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.PolyNull;

public class Versioned<T> {

    private static final Logger logger = LogManager.getLogger(Versioned.class);

    private final int version;
    private final T object;
    private final long timestamp;

    public Versioned(int version, T object) {
        this(version, object, System.currentTimeMillis());
    }

    public Versioned(int version, T object, long timestamp) {
        this.version = version;
        this.object = object;
        this.timestamp = timestamp;
    }

    public int version() {
        return version;
    }

    public T get() {
        return object;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("%s [version %d]", object, version);
    }

    public static <T> AtomicReference<Versioned<T>> atomic(int version, T object) {
        return new AtomicReference<>(new Versioned<>(version, object));
    }

    public static <T> boolean replaceIfNewer(AtomicReference<@PolyNull Versioned<T>> current, Versioned<T> maybeNewer) {
        logger.debug("Versioned.replaceIfNewer({}, {})", current, maybeNewer);
        logger.debug("current==null: {}; maybeNewer.version: {}", current == null, maybeNewer.version());
        int i = 1;
        while (true) {
            Versioned<T> old = null;
            logger.debug("Iteration {}: Getting `old`...", i++);
            try {
                old = current.get();
            } catch (Throwable t) {
                logger.error("{}: {}", t.getClass(), t.getMessage());
                t.printStackTrace();
                throw t;
            }
            logger.debug("Iteration {}: `old` = `{}`", i++, old);
            if (old == null) {
                logger.debug("old == null");
                if (current.compareAndSet(old, maybeNewer)) {
                    return true;
                }
            } else if (old.version() < maybeNewer.version()) {
                logger.debug("old version ({}) < new version ({})", old.version, maybeNewer.version);
                if (current.compareAndSet(old, maybeNewer)) {
                    return true;
                } else {
                    logger.debug("compareAndSet failed: {}, {}", current, old);
                }
            } else {
                logger.debug("old version ({}) >= new version ({})", old.version, maybeNewer.version);
                return false;
            }
        }
    }
}
