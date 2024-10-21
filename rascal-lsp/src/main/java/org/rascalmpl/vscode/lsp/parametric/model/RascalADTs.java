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
package org.rascalmpl.vscode.lsp.parametric.model;

/**
 * Models util::LanguageServer ADTs and their fields
 */
public class RascalADTs {
    private RascalADTs() {}
    public static class LanguageContributions {
        private LanguageContributions () {}

        public static final String PARSING         = "parsing";
        public static final String ANALYSIS        = "analysis";
        public static final String BUILD           = "build";
        public static final String DOCUMENT_SYMBOL = "documentSymbol";
        public static final String CODE_LENS       = "codeLens";
        public static final String INLAY_HINT      = "inlayHint";
        public static final String EXECUTION       = "execution";
        public static final String HOVER           = "hover";
        public static final String DEFINITION      = "definition";
        public static final String REFERENCES      = "references";
        public static final String IMPLEMENTATION  = "implementation";
        public static final String CODE_ACTION     = "codeAction";

        public static class Summarizers {
            private Summarizers() {}

            public static final String PROVIDES_HOVERS          = "providesHovers";
            public static final String PROVIDES_DEFINITIONS     = "providesDefinitions";
            public static final String PROVIDES_REFERENCES      = "providesReferences";
            public static final String PROVIDES_IMPLEMENTATIONS = "providesImplementations";
        }
    }

    public static class SummaryFields {
        private SummaryFields() {}

        public static final String DEPRECATED_DOCUMENTATION = "documentation";

        public static final String HOVERS          = "hovers";
        public static final String DEFINITIONS     = "definitions";
        public static final String REFERENCES      = "references";
        public static final String IMPLEMENTATIONS = "implementations";
    }

    public static class CommandFields {
        private CommandFields() {}

        public static final String TITLE = "title";
    }

    public static class CodeActionFields {
        private CodeActionFields() { }

        public static final String EDITS = "edits";
        public static final String COMMAND = "command";
        public static final String TITLE = "title";
        public static final String KIND = "kind";
    }
}
