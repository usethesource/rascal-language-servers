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
/* eslint-disable @typescript-eslint/naming-convention */
import { readFile, stat } from "fs/promises";

export interface ReleaseInfo {
    implementor?: string,
    implementor_version?: string,
    java_runtime_version?: string,
    java_version?: string,
    java_version_date?: string,
    libc?: string,
    modules?: string,
    os_arch?: string,
    os_name?: string,
    source?: string,
    build_source?: string,
    build_source_repo?: string,
    source_repo?: string,
    full_version?: string,
    semantic_version?: string,
    build_info?: string,
    jvm_variant?: string,
    jvm_version?: string,
    image_type?: string,
}

export async function readReleaseInfo(path:string) : Promise<ReleaseInfo> {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const releaseInfo: any = {};
    if(await stat(path)){
        const content = await readFile(path, "utf-8");
        const lines = content.split('\n');
        lines.forEach(line => {
            const [key, value] = line.split("=");
            if(key && value){
                releaseInfo[key.toLowerCase()] = value.substring(1, value.length-2).trim();
            }
        });
    }
    return releaseInfo as ReleaseInfo;
}
