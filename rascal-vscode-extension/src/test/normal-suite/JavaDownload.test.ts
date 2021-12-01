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
import * as assert from 'assert';
import { statSync, rm } from 'fs';
import temp = require('temp');
import * as jd from '../../auto-jvm/downloaders';
import { describe, after } from 'mocha';
import { join } from 'path';


describe('JVM Download', function () {
    const tempDir = temp.mkdirSync();
    function testTemurin(version: number, arch: jd.TemurinArchitectures, platform: jd.TemurinPlatforms) {
        return async function() {
            const newPath = await jd.fetchAndUnpackTemurin(arch,
                platform, await jd.identifyLatestTemurinLTSRelease(version), join(tempDir, "temurin", arch, platform), () => {});
            assert.ok(statSync(newPath).isFile());
        };
    }

    function testCorretto(version: number, arch: jd.CorrettoArchitectures, platform: jd.CorrettoPlatforms) {
        return async function() {
            const newPath = await jd.fetchAndUnpackCorretto(arch, platform, version, join(tempDir, "corretto", arch, platform), () => {});
            assert.ok(statSync(newPath).isFile());
        };
    }

    function testMSOpenJDK(version: number, arch: jd.MSArchitectures, platform: jd.MSPlatforms) {
        return async function() {
            const newPath = await jd.fetchAndUnpackMicrosoftJDK(arch, platform, version, join(tempDir, "msjdk", arch, platform), () => {});
            assert.ok(statSync(newPath).isFile());
        };
    }

    describe(`Eclipse Temurin jdk11`, function() {
        it(`Windows x64`, testTemurin(11, "x64", "windows")).timeout(50000);
        it(`Linux arm`, testTemurin(11, "arm", "linux")).timeout(50000);
        it(`Mac x64`, testTemurin(11, "x64", "mac")).timeout(50000);
    });
    describe(`Eclipse Temurin jdk17`, function() {
        it(`Windows x64`, testTemurin(17, "x64", "windows")).timeout(50000);
        it(`Linux x64`, testTemurin(17, "x64", "linux")).timeout(50000);
        it(`Mac aarch64`, testTemurin(17, "aarch64", "mac")).timeout(50000);
    });
    describe(`Amazon Corretto jdk11`, function() {
        it(`Windows x64`, testCorretto(11, "x64", "windows")).timeout(50000);
        it(`Linux arm`, testCorretto(11, "arm", "linux")).timeout(50000);
        it(`Mac x64`, testCorretto(11, "x64", "macos")).timeout(50000);
    });
    describe(`Amazon Corretto jdk17`, function() {
        it(`Windows x64`, testCorretto(17, "x64", "windows")).timeout(50000);
        it(`Linux x64`, testCorretto(17, "x64", "linux")).timeout(50000);
        it(`Mac aarch64`, testCorretto(17, "aarch64", "macos")).timeout(50000);
    });
    describe(`Microsoft OpenJDK jdk11`, function() {
        it(`Windows x64`, testMSOpenJDK(11, "x64", "windows")).timeout(50000);
        it(`Linux aarch64`, testMSOpenJDK(11, "aarch64", "linux")).timeout(50000);
        it(`Mac x64`, testMSOpenJDK(11, "x64", "macOS")).timeout(50000);
    });
    describe(`Microsoft OpenJDK jdk17`, function() {
        it(`Windows aarch64`, testMSOpenJDK(17, "aarch64", "windows")).timeout(50000);
        it(`Linux x64`, testMSOpenJDK(17, "x64", "linux")).timeout(50000);
        it(`Mac aarch64`, testMSOpenJDK(17, "aarch64", "macOS")).timeout(50000);
    });
    after(() => {
        console.log(tempDir);
        // do the rm in the background, since it might take a while
        rm(tempDir, { recursive: true, force: true }, () => {});
    });
});
