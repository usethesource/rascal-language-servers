@license{
Copyright (c) 2018-2025, NWO-I CWI and Swat.engineering
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
}
module lang::rascal::lsp::Common

@memo
@synopsis{Infers the root of the project that `member` is in.}
loc inferProjectRoot(loc member) {
    parentRoot = member;
    root = parentRoot;

    do {
        root = parentRoot;
        parentRoot = inferDeepestProjectRoot(root.parent);
    } while (root.parent? && parentRoot != root.parent);
    return root;
}

@synopsis{Infers the longest project root-like path that `member` is in.}
@pitfalls{Might return a sub-directory of `target/`.}
loc inferDeepestProjectRoot(loc member) {
    current = targetToProject(member);
    if (!isDirectory(current)) {
        current = current.parent;
    }

    while (exists(current), isDirectory(current)) {
        if (exists(current + "META-INF" + "RASCAL.MF")) {
            return current;
        }
        if (!current.parent?) {
            return isDirectory(member) ? member : member.parent;
        }
        current = current.parent;
    }

    return current;
}

loc targetToProject(loc l) {
    if (l.scheme == "target") {
        return l[scheme="project"];
    }
    return l;
}
