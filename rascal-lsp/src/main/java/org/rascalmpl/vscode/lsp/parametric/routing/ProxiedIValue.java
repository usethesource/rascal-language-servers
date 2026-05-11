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

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

import io.usethesource.vallang.IExternalValue;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;

/**
 * Wraps a JSON string representing an IValue as an IValue.
 *
 * This class allows passing IValues through JSON-RPC-enabled servers without requiring to decode/encode them.
 */
class ProxiedIValue implements IExternalValue {

    private String contents;

    /*package*/ ProxiedIValue(String json) {
        this.contents = json;
    }

    /*package*/ String getContents() {
        return this.contents;
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
    /*package*/ static void toJson(JsonWriter writer, ProxiedIValue value) throws IOException {
        writer.jsonValue(value.getContents());
    }

    /**
     * Wrap the {@link IValue}'s JSON representation.
     * @param reader the reader to read the JSON from
     * @return the JSON as an {@link IValue}
     * @throws IOException if an unexpected input occurs
     */
    /*package*/ static ProxiedIValue fromJson(JsonReader reader) throws IOException {
        var sb = new StringBuilder();
        readValue(reader, sb);
        return new ProxiedIValue(sb.toString());
    }

    /**
     * Recursively build a JSON string given a tokenized input.
     * @param reader a tokenized JSON reader
     * @param sb a builder to which to append the result string
     * @throws IOException if an unexpected input occurs
     */
    private static void readValue(JsonReader reader, StringBuilder sb) throws IOException {
        switch (reader.peek()) {
            case BEGIN_ARRAY:
                readArray(reader, sb);
                break;
            case BEGIN_OBJECT:
                readObject(reader, sb);
                break;
            case BOOLEAN:
                readBoolean(reader, sb);
                break;
            case NULL:
                readNull(reader, sb);
                break;
            case NUMBER:
                readNumber(reader, sb);
                break;
            case STRING:
                readString(reader, sb);
                break;
            case NAME: // fall-through
            case END_ARRAY: // fall-through
            case END_DOCUMENT: // fall-through
            case END_OBJECT: // fall-through
                throw new IOException("Malformed JSON");
            default:
                throw new IOException("Unknown JSON token");
        }
    }

    private static void readString(JsonReader reader, StringBuilder sb) throws IOException {
        sb.append('"');
        sb.append(reader.nextString());
        sb.append('"');
    }

    private static void readNumber(JsonReader reader, StringBuilder sb) throws IOException {
        try {
            sb.append(reader.nextInt());
        } catch (NumberFormatException e) {}
        try {
            sb.append(reader.nextLong());
        } catch (NumberFormatException e) {}
        try {
            sb.append(reader.nextDouble());
        } catch (NumberFormatException e) {}
    }

    private static void readNull(JsonReader reader, StringBuilder sb) throws IOException {
        reader.nextNull();
        sb.append("null");
    }

    private static void readBoolean(JsonReader reader, StringBuilder sb) throws IOException {
        sb.append(reader.nextBoolean());
    }

    private static void readArray(JsonReader reader, StringBuilder sb) throws IOException {
        reader.beginArray();
        sb.append('[');
        boolean hasNext = reader.hasNext();
        while (hasNext) {
            readValue(reader, sb);
            hasNext = reader.hasNext();
            if (hasNext) {
                sb.append(',');
            }
        }
        reader.endArray();
        sb.append(']');
    }

    private static void readObject(JsonReader reader, StringBuilder sb) throws IOException {
        reader.beginObject();
        sb.append('{');

        boolean hasNext = reader.hasNext();
        while (hasNext) {
            sb.append('"');
            sb.append(reader.nextName());
            sb.append('"');
            sb.append(':');
            readValue(reader, sb);

            hasNext = reader.hasNext();
            if (hasNext) {
                sb.append(',');
            }
        }
        reader.endObject();
        sb.append('}');
    }

}
