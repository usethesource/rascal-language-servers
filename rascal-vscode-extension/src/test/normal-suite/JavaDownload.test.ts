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
import * as assert from 'assert';
import { statSync, rm, mkdirSync } from 'fs';
import * as jd from '../../auto-jvm/downloaders';
import { describe, after } from 'mocha';
import { join } from 'path';
import * as os from 'os';


function emptyProgress() {
    // no progress report for tests
}

describe('JVM Download', function () {
    const tempDir = join(os.tmpdir(), "jvm-download-" + (Math.random() + 1).toString(36).substring(7));
    mkdirSync(tempDir);
    function testTemurin(version: number, arch: jd.TemurinArchitectures, platform: jd.TemurinPlatforms) {
        return async function() {
            const newPath = await jd.fetchAndUnpackTemurin(arch,
                platform, await jd.identifyLatestTemurinLTSRelease(version, arch, platform), join(tempDir, "temurin", arch, platform), emptyProgress);
            assert.ok(statSync(newPath).isFile());
        };
    }

    function testCorretto(version: number, arch: jd.CorrettoArchitectures, platform: jd.CorrettoPlatforms) {
        return async function() {
            const newPath = await jd.fetchAndUnpackCorretto(arch, platform, version, join(tempDir, "corretto", arch, platform), emptyProgress);
            assert.ok(statSync(newPath).isFile());
        };
    }

    function testMSOpenJDK(version: number, arch: jd.MSArchitectures, platform: jd.MSPlatforms) {
        return async function() {
            const newPath = await jd.fetchAndUnpackMicrosoftJDK(arch, platform, version, join(tempDir, "msjdk", arch, platform), emptyProgress);
            assert.ok(statSync(newPath).isFile());
        };
    }

    const downloadTimeout = 60_000;
    const testAll = false;

    describe(`Eclipse Temurin jdk11`, function() {
        if (testAll) {
            it(`Mac x64`, testTemurin(11, "x64", "mac")).timeout(downloadTimeout);
            it(`Linux arm`, testTemurin(11, "arm", "linux")).timeout(downloadTimeout);
        }
        it(`Mac aarch64`, testTemurin(11, "aarch64", "mac")).timeout(downloadTimeout);
        it(`Windows x64`, testTemurin(11, "x64", "windows")).timeout(downloadTimeout);
    });
    describe(`Eclipse Temurin jdk17`, function() {
        if (testAll) {
            it(`Mac aarch64`, testTemurin(17, "aarch64", "mac")).timeout(downloadTimeout);
            it(`Windows x64`, testTemurin(17, "x64", "windows")).timeout(downloadTimeout);
        }
        it(`Linux x64`, testTemurin(17, "x64", "linux")).timeout(downloadTimeout);
    });
    describe(`Amazon Corretto jdk11`, function() {
        if (testAll) {
            it(`Windows x64`, testCorretto(11, "x64", "windows")).timeout(downloadTimeout);
            it(`Mac x64`, testCorretto(11, "x64", "macos")).timeout(downloadTimeout);
        }
        it(`Linux arch64`, testCorretto(11, "aarch64", "linux")).timeout(downloadTimeout);
    });
    describe(`Amazon Corretto jdk17`, function() {
        if (testAll) {
            it(`Windows x64`, testCorretto(17, "x64", "windows")).timeout(downloadTimeout);
            it(`Linux x64`, testCorretto(17, "x64", "linux")).timeout(downloadTimeout);
        }
        it(`Mac aarch64`, testCorretto(17, "aarch64", "macos")).timeout(downloadTimeout);
    });
    describe(`Microsoft OpenJDK jdk11`, function() {
        it(`Windows x64`, testMSOpenJDK(11, "x64", "windows")).timeout(downloadTimeout);
        if (testAll) {
            it(`Linux aarch64`, testMSOpenJDK(11, "aarch64", "linux")).timeout(downloadTimeout);
            it(`Mac x64`, testMSOpenJDK(11, "x64", "macOS")).timeout(downloadTimeout);
        }
    });
    describe(`Microsoft OpenJDK jdk17`, function() {
        if (testAll) {
            it(`Windows aarch64`, testMSOpenJDK(17, "aarch64", "windows")).timeout(downloadTimeout);
            it(`Mac aarch64`, testMSOpenJDK(17, "aarch64", "macOS")).timeout(downloadTimeout);
        }
        it(`Linux x64`, testMSOpenJDK(17, "x64", "linux")).timeout(downloadTimeout);
    });

    after(() => {
        console.log(tempDir);
        // do the rm in the background, since it might take a while
        rm(tempDir, { recursive: true, force: true }, emptyProgress);
    });
});
