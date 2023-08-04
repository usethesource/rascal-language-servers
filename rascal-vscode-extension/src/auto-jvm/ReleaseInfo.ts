/* eslint-disable @typescript-eslint/naming-convention */
import { existsSync, readFileSync } from "fs";

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

export function readReleaseInfo(path:string) : ReleaseInfo {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const releaseInfo: any = {};
    if(existsSync(path)){
        const content = readFileSync(path, {  }).toString();
        const lines = content.split('\n');
        lines.forEach(line => {
            const keyValue = line.split("=");
            if(keyValue.length === 2){
                releaseInfo[keyValue[0].toLowerCase()] = keyValue[1].replaceAll('"', '').replace("\r", "");
            }
        });
    }
    return releaseInfo as ReleaseInfo;
}