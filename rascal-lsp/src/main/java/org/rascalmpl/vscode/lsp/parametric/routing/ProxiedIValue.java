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
package org.rascalmpl.vscode.lsp.parametric.routing;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

import io.usethesource.vallang.IExternalValue;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;

/**
 * Wraps a JSON element representing an IValue as an IValue.
 *
 * This class allows passing IValues through JSON-RPC-enabled servers without requiring to decode/encode them.
 */
class ProxiedIValue implements IExternalValue {

    private static final Gson gson = new Gson();

    private final JsonElement element;

    private ProxiedIValue(JsonElement element) {
        this.element = element;
    }

    @Override
    public int getMatchFingerprint() {
        throw new UnsupportedOperationException("ProxiedIValue::getMatchFingerprint");
    }

    @Override
    public Type getType() {
        throw new UnsupportedOperationException("ProxiedIValue::getType");
    }

    /**
     * Unwrap the {@link IValue}'s JSON representation.
     * @param writer the writer to write the unwrapped JSON to
     * @param value the value to proxy
     * @throws IOException if an unexpected input occurs
     */
    /*package*/ static void toJson(JsonWriter writer, ProxiedIValue value) {
        gson.toJson(value.element, writer);
    }

    /**
     * Wrap the {@link IValue}'s JSON representation.
     * @param reader the reader to read the JSON from
     * @return the JSON as an {@link IValue}
     */
    /*package*/ static ProxiedIValue fromJson(JsonReader reader) {
        return new ProxiedIValue(JsonParser.parseReader(reader));
    }

}
