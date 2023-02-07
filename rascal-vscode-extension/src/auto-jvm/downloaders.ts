/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
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
import * as os from 'os';
import * as path from 'path';
import { default as nfetch, RequestInfo, Response } from 'node-fetch';
import * as tar from 'tar';
import {pipeline, Transform} from 'stream';
import {promisify} from 'util';
import * as fs from 'fs';
import { mkdir } from 'fs/promises';
import * as yauzl from 'yauzl';
import * as winca from 'win-ca';
import * as macca from 'mac-ca'; // just importing is is enough

type ProgressFunc = (percIncrement: number, message: string) => void;

interface AdoptiumVersions {
    versions: AdoptiumVersion[]
}
interface AdoptiumVersion {
    major: number;
    minor: number;
    security: number;
    patch: number;
    pre: string;
    // eslint-disable-next-line @typescript-eslint/naming-convention
    adopt_build_number: number;
    semver: string;
    // eslint-disable-next-line @typescript-eslint/naming-convention
    openjdk_version: string;
    build: number;
    optional: string;
}
export type TemurinArchitectures = "arm" | "aarch64" | "x64";
export type TemurinPlatforms = "mac" | "linux" | "windows";
export type CorrettoArchitectures = TemurinArchitectures;
export type CorrettoPlatforms = "macos" | "linux" | "windows";

export type MSArchitectures = "x64" | "aarch64";
export type MSPlatforms = "macOS" | "linux" | "windows";

const ppipeline = promisify(pipeline);

export function temurinSupported(jdkVersion: number): boolean {
    switch(os.arch()) {
        case 'x32': return true;
        case 'x64': return true;
        case 'arm': return os.platform() === "linux";
        case 'arm64':
            switch (os.platform()) {
                case 'linux': return true;
                case 'darwin': return true;
                default: return false;
            }
        default: return false;
    }
}

export function correttoSupported(jdkVersion: number): boolean {
    switch(os.arch()) {
        case 'x32': return true;
        case 'x64': return true;
        case 'arm': return os.platform() === "linux" && jdkVersion === 11;
        case 'arm64':
            switch (os.platform()) {
                case 'linux': return true;
                case 'darwin': return jdkVersion === 17;
                default: return false;
            }
        default: return false;
    }
}

export function microsoftSupported(jdkVersion: number): boolean {
    if (jdkVersion === 8) {
        return false;
    }
    switch(os.arch()) {
        case 'x32': return true;
        case 'x64': return true;
        case 'arm64':
            switch (os.platform()) {
                case 'linux': return true;
                case 'win32': return true;
                case 'darwin': return jdkVersion === 17;
                default: return false;
            }
        default: return false;
    }
}

let injected = false;

function fetch(url: RequestInfo): Promise<Response> {
    if (!injected) {
        if (process.platform === 'win32') {
            winca({
                async: false,
                fallback: true,
                save: false,
                inject: 'append'
            });
        }
        injected = true;
    }
    return nfetch(url);
}


export async function downloadTemurin(mainJVMPath: string, jdkVersion: number, progress: ProgressFunc): Promise<string> {

    const arch = mapTemuringCorrettoArch();
    let platform: TemurinPlatforms;
    switch (os.platform()) { // again, it's the compile-time platform, not the runtime one
        case 'darwin': platform="mac"; break;
        case 'linux': platform = "linux";  break;
        case 'win32': platform = "windows";  break;
        default: throw new Error("Unsupported platform: " + os.platform());
    }
    const jdkRelease = await identifyLatestTemurinLTSRelease(jdkVersion, arch, platform);
    return fetchAndUnpackTemurin(arch, platform, jdkRelease, mainJVMPath, progress);
}

export async function downloadCorretto(mainJVMPath: string, jdkVersion: number, progress: ProgressFunc): Promise<string> {
    const arch = mapTemuringCorrettoArch();
    let platform: CorrettoPlatforms;
    switch (os.platform()) { // again, it's the compile-time platform, not the runtime one
        case 'darwin': platform="macos"; break;
        case 'linux': platform = "linux";  break;
        case 'win32': platform = "windows";  break;
        default: throw new Error("Unsupported platform: " + os.platform());
    }

    return fetchAndUnpackCorretto(arch, platform, jdkVersion, mainJVMPath, progress);
}



export async function downloadMicrosoftJDK(mainJVMPath: string, jdkVersion: number, progress: ProgressFunc): Promise<string> {
    let arch: MSArchitectures;
    switch (os.arch()) { // warning, nodejs arch gives the compile-time arch, not the runtime cpu one.
        case "arm64": arch = "aarch64"; break;
        case "x32": // fall through since vscode might be running 32bit version of node
        case "x64": arch = "x64"; break;
        default: throw new Error("Unsupported architecture: " + os.arch());
    }
    let platform: MSPlatforms;
    switch (os.platform()) { // again, it's the compile-time platform, not the runtime one
        case 'darwin': platform="macOS"; break;
        case 'linux': platform = "linux";  break;
        case 'win32': platform = "windows";  break;
        default: throw new Error("Unsupported platform: " + os.platform());
    }
    return fetchAndUnpackMicrosoftJDK(arch, platform, jdkVersion, mainJVMPath, progress);

}


// locate newest java <version> release
export async function identifyLatestTemurinLTSRelease(version: number, arch: TemurinArchitectures, platform: TemurinPlatforms): Promise<string> {
    const releaseRange = encodeURIComponent(`[${version}.0,${version}.999]`);
    const releasesRaw = await fetch(`https://api.adoptium.net/v3/info/release_versions?architecture=${arch}&os=${platform}&heap_size=normal&image_type=jdk&jvm_impl=hotspot&lts=true&page=0&page_size=1&project=jdk&release_type=ga&sort_method=DATE&sort_order=DESC&vendor=eclipse&version=${releaseRange}`);

    if (!releasesRaw.ok) {
        throw new Error(`unexpected response ${releasesRaw.statusText}`);
    }

    const releases = await releasesRaw.json() as AdoptiumVersions;
    if (releases.versions.length <= 0) {
        throw new Error("Adoptium returned no releases");
    }
    const rel =releases.versions[0];
    if (version === 8) {
        return `jdk8u${rel.security}-b${rel.build.toString().padStart(2, '0')}`;
    }
    return `jdk-${rel.openjdk_version}`;
}

function mapTemuringCorrettoArch(): TemurinArchitectures {
    switch (os.arch()) { // warning, nodejs arch gives the compile-time arch, not the runtime cpu one.
        case "arm": return "arm";
        case "arm64": return "aarch64";
        case "x32": // fall through since vscode might be running 32bit version of node
        case "x64": return "x64";
        default: throw new Error("Unsupported architecture: " + os.arch());
    }
}


export async function fetchAndUnpackMicrosoftJDK(arch: MSArchitectures, platform: MSPlatforms, jdkVersion: number, mainJVMPath: string, progress: ProgressFunc): Promise<string> {
    // https://aka.ms/download-jdk/microsoft-jdk-17-linux-x64.tar.gz
    const url = `https://aka.ms/download-jdk/microsoft-jdk-${jdkVersion}-${platform}-${arch}.${platform === "windows" ? "zip" : "tar.gz"}`;
    switch (platform) {
        case "windows": return fetchUnpackZipInMemory(url, path.join("bin", "java.exe"), mainJVMPath, progress);
        case "macOS": return fetchUnpackTarGZ(url, path.join("Contents", "Home", "bin", "java"), mainJVMPath, progress, 1);
        case "linux": return fetchUnpackTarGZ(url, path.join("bin", "java"), mainJVMPath, progress);
        default: return "";
    }
}

export async function fetchAndUnpackCorretto(arch: CorrettoArchitectures, platform: CorrettoPlatforms, jdkVersion: number, mainJVMPath: string, progress: ProgressFunc): Promise<string> {
    // https://corretto.aws/[latest/latest_checksum]/amazon-corretto-[corretto_version]-[cpu_arch]-[os]-[package_type].[file_extension]
    // https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz
    const url = `https://corretto.aws/downloads/latest/amazon-corretto-${jdkVersion}-${arch}-${platform}-jdk.${platform === "windows" ? "zip" : "tar.gz"}`;
    switch (platform) {
        case "windows": return fetchUnpackZipInMemory(url, path.join("bin", "java.exe"), mainJVMPath, progress);
        case "macos": return fetchUnpackTarGZ(url, path.join("Contents", "Home", "bin", "java"), mainJVMPath, progress);
        case "linux": return fetchUnpackTarGZ(url, path.join("bin", "java"), mainJVMPath, progress);
        default: return "";
    }
}


export async function fetchAndUnpackTemurin(arch: TemurinArchitectures, platform: TemurinPlatforms, jdk11Release: string, mainJVMPath: string, progress: ProgressFunc): Promise<string> {
    // https://api.adoptium.net/v3/binary/version/jdk-11.0.13%2B8/linux/x64/jdk/hotspot/normal/eclipse?project=jdk
    const url = `https://api.adoptium.net/v3/binary/version/${encodeURIComponent(jdk11Release)}/${platform}/${arch}/jdk/hotspot/normal/eclipse?project=jdk`;
    switch (platform) {
        case "windows": return fetchUnpackZipInMemory(url, path.join("bin", "java.exe"), mainJVMPath, progress);
        case "mac": return fetchUnpackTarGZ(url, path.join("Contents", "Home", "bin", "java"), mainJVMPath, progress);
        case "linux": return fetchUnpackTarGZ(url, path.join("bin", "java"), mainJVMPath, progress);
        default: return "";
    }
}

// unpack
async function fetchUnpackTarGZ(url: string, subpath: string, mainJVMPath: string, progress: ProgressFunc, strip = 0): Promise<string> {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`unexpected response ${response.statusText}`);
    }
    await mkdir(mainJVMPath, { recursive : true });
    return new Promise((resolve, reject) => {
        if (!response.body) {
            reject(new Error("Empty body in response"));
        }
        let detectedRootPath = "";
        const size = Number(response.headers.get("Content-Length") || "0");
        pipeline(response.body,
            new Transform({
                transform: function (chunk, encoding, callback) {
                    progress(100 * (chunk.length / size), "Downloading and extracting tar");
                    this.push(chunk);
                    callback();
                }
            }),
            tar.extract({
                cwd: mainJVMPath,
                strip: strip,
                onentry: e => {
                    if ((detectedRootPath === "" || detectedRootPath === ".") && !e.meta) {
                        detectedRootPath = e.path.split('/')[strip];
                    }
                }
            }), e => { if (e) { reject(e);}}
            ).on("close", () => {
                const jdkPath = path.join(mainJVMPath, detectedRootPath);
                fs.createWriteStream(path.join(jdkPath, "rascal-auto-download")).close();
                resolve(path.join(jdkPath, subpath));
            })
            .on("error", e => {
                reject(e);
            });

    });
}




async function streamToBuffer(response: Response, progress: ProgressFunc): Promise<Buffer> {
    const size = Number(response.headers.get("Content-Length") || "0");
    if (size === 0) {
        console.log("No content-length found");
        return new Promise((resolve, reject) => {
            const data: Buffer[] = [];

            response.body.on('data', (chunk) => {
                data.push(chunk);
            });

            response.body.on('end', () => {
                resolve(Buffer.concat(data));
            });

            response.body.on('error', (err) => {
                progress(50, "Downloaded zip");
                reject(err);
            });
        });
    }
    return new Promise((resolve, reject) => {
        const data = Buffer.alloc(size);
        let written = 0;
        response.body.on('data', (chunk: Buffer) => {
            const wrote = chunk.copy(data, written, 0);
            progress(50 * (wrote / size), "Downloading zip");
            written += wrote;
        });

        response.body.on('end', () => {
            resolve(data);
        });

        response.body.on('error', (err) => {
            reject(err);
        });
    });
}

const zipFromBuffer = promisify<Buffer, yauzl.Options, yauzl.ZipFile | undefined>(yauzl.fromBuffer);

// unpack zip and detect root folder
async function fetchUnpackZipInMemory(url: string, subpath: string, mainJVMPath: string, progress: ProgressFunc): Promise<string> {
    const response = await fetch(url);
    if (!response.ok || !response.body) {
        throw new Error(`unexpected response ${response.statusText} || body: ${response.body}`);
    }

    const zipFileInMemory = await streamToBuffer(response, progress);
    await mkdir(mainJVMPath, { recursive : true });
    const zipFile = await zipFromBuffer(zipFileInMemory, {lazyEntries: true, autoClose: true});
    if (!zipFile) {
        throw new Error("Error opening zip file from stream");
    }
    return new Promise((resolve, reject) => {
        let detectedRootPath = "";
        zipFile.on("entry", async (entry: yauzl.Entry) => {
            progress(50 / zipFile.entryCount, "Unpacking zip file");

            if (detectedRootPath === "" && entry.fileName.includes('/')) {
                detectedRootPath = entry.fileName.split('/')[0];
            }
            const destFile = path.join(mainJVMPath, entry.fileName);
            await mkdir(path.dirname(destFile), {recursive: true});
            const mode = (entry.externalFileAttributes >> 16) & 0xFFFF;
            if ((mode & 0xF000) === 0x4000 || entry.fileName.endsWith('/')) {
                zipFile.readEntry(); // skip directory entries
                return;
            }
            const readStream = await promisify(zipFile.openReadStream.bind(zipFile))(entry);
            if (!readStream) {
                reject("Error opening stream: " + entry.fileName);
                zipFile.close();
                return;
            }
            await ppipeline(readStream, fs.createWriteStream(destFile));



            zipFile.readEntry(); // continue to next entry
        });

        zipFile.on("error", (e) => reject(e));
        zipFile.on("end", () => {
            const jdkPath = path.join(mainJVMPath, detectedRootPath);
            fs.createWriteStream(path.join(jdkPath, "rascal-auto-download")).close();
            resolve(path.join(jdkPath, subpath));
        });

        zipFile.readEntry(); // start chain
    });
}
