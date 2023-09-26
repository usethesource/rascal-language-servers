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

import path = require("path");

export async function sleep(ms: number) {
    return new Promise(r => setTimeout(r, ms));
}

export class TestWorkspace {
    private static get workspacePrefix() { return 'test-workspace'; }
    public static get workspaceFile() { return path.join(this.workspacePrefix, 'test.code-workspace'); }
    public static get testProject() { return path.join(this.workspacePrefix, 'test-project'); }
    public static get libProject() { return path.join(this.workspacePrefix, 'test-lib'); }
    public static get mainFile() { return path.join(this.testProject, 'src', 'main', 'rascal', 'Main.rsc'); }
    public static get mainFileTpl() { return path.join(this.testProject, 'target', 'classes', 'rascal','Main.tpl'); }
    public static get libCallFile() { return path.join(this.testProject, 'src', 'main', 'rascal', 'LibCall.rsc'); }
    public static get libCallFileTpl() { return path.join(this.testProject, 'target', 'classes', 'rascal','LibCall.tpl'); }
    public static get libFile() { return path.join(this.libProject, 'src', 'main', 'rascal', 'Lib.rsc'); }
    public static get libFileTpl() { return path.join(this.libProject, 'target', 'classes', 'rascal','Lib.tpl'); }
}
