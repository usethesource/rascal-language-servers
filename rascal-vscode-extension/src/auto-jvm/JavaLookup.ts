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
import * as vscode from 'vscode';
import * as path from 'path';
import * as os from 'os';
import * as cp from 'child_process';
import * as dl from './downloaders';
import {  existsSync, readdirSync } from 'fs';
import { promisify } from 'util';
import { readReleaseInfo } from './ReleaseInfo';


const currentJVMEngineMin = 11;
const currentJVMEngineMax = 17;
const currentPreferredJVMEngine = 11;
const temurin = "Eclipse Temurin";
const msJava = "Microsoft Build of OpenJDK";
const amazon = "Amazon Corretto";
const mainJVMPath = path.join(os.homedir(), ".jvm", `jdk${currentPreferredJVMEngine}`);

let lookupCompleted: Thenable<string> | undefined;

const pexec = promisify(cp.exec);

export async function getJavaExecutable(): Promise<string> {
    if (lookupCompleted) {
        console.log("Returning: " + lookupCompleted);
        return lookupCompleted;
    }
    for (const possibleCandidate of getJavaCandidates()) {
        try {
            // we check the availability of javac, since we need a JDK instead of a JRE
            const versionRun = await pexec(`"${makeJavac(possibleCandidate)}" -version`);
            const versionsFound = /javac (?:1\.)?(\d+)\./.exec(versionRun.stdout);
            if (versionsFound && versionsFound.length > 0) {
                if (Number(versionsFound[1]) >= currentJVMEngineMin && Number(versionsFound[1]) <= currentJVMEngineMax) {
                    lookupCompleted = Promise.resolve(possibleCandidate);
                    return possibleCandidate;
                }
            }
        }
        catch (e) {
            // ignore exceptions, most likely the candidate wasn't in the path
        }
    }
    // okay, so we don't have a working java interpreter, so we ask the user
    lookupCompleted = askUserForJVM();
    lookupCompleted.then(_good => {
        // ignore success, this callback is only for failures

    }, e => {
        console.log("Automatic download failed: ", e);
        lookupCompleted = undefined;
    });
    return lookupCompleted;
}

function makeJavac(javaPath: string): string {
    if (javaPath.endsWith(".exe")) {
        return javaPath.replace(/java.exe$/, "javac.exe");
    }
    return javaPath + "c";
}

function getJavaCandidates(): string[] {
    const result = [];
    const { JAVA_HOME } = process.env;
    const name = os.platform() === 'win32' ? 'java.exe' : 'java';
    if (JAVA_HOME) {
        result.push(path.join(JAVA_HOME, "bin", name));
    }
    else {
        result.push(name);
    }
    if (existsSync(mainJVMPath)) {
        const jvms = readdirSync(mainJVMPath, {  }).sort().reverse();
        for (const ent of jvms) {
            let possiblePath = "";
            switch (os.platform()) {
                case 'win32': possiblePath = path.join(mainJVMPath, String(ent), "bin", "java.exe"); break;
                case 'linux': possiblePath = path.join(mainJVMPath, String(ent), "bin", "java"); break;
                case 'darwin': possiblePath = path.join(mainJVMPath, String(ent), "Contents", "Home", "bin", "java"); break;
            }
            if (existsSync(makeJavac(possiblePath))) {
                result.push(possiblePath);
            }
        }
    }
    return result;
}

export async function askUserForJVM() : Promise<string> {
    const selfInstall = "Install myself & restart vscode";
    const extensionInstall = `Automatically download Java ${currentPreferredJVMEngine} `;
    const configurePath = "Configure JDK path";

    const opt = await vscode.window.showErrorMessage(
        `Rascal (or a DSL that uses Rascal) requires at least Java ${currentJVMEngineMin} runtime (max ${currentJVMEngineMax}). Shall we download it, or will you set it up yourself?`,
        { modal: true},
        selfInstall, extensionInstall, configurePath
    );
    if (opt === extensionInstall) {
        return startAutoInstall();
    }
    if (opt === configurePath) {
        return openSettings();
    }
    throw Error("User setup required for jdk " + currentJVMEngineMin);
}

async function startAutoInstall(): Promise<string> {
    const acceptGPL2CE = "Accept GPL+CE";
    const reviewLicense = "Review GPL+CE";
    const readAboutLicense = "Read about GPL+CE";
    const opt = await vscode.window.showInformationMessage(
        "Downloading a Java runtime means you accept the \"GPL2 + Classpath Exception\" license. Note: this holds for the runtime engine, not for your own code.",
        { modal: true},
        acceptGPL2CE, reviewLicense, readAboutLicense);
    if (opt === acceptGPL2CE) {
        return downloadJDK();
    }
    if (opt === reviewLicense || opt === readAboutLicense) {
        let url;
        if (opt === reviewLicense) {
            url = "https://openjdk.java.net/legal/gplv2+ce.html";
        }
        else {
            url = "https://softwareengineering.stackexchange.com/questions/119436/what-does-gpl-with-classpath-exception-mean-in-practice";
        }
        await vscode.env.openExternal(vscode.Uri.parse(url));
        return startAutoInstall();
    }
    throw new Error(`User setup required for jdk ${currentJVMEngineMin}`);
}

async function openSettings(): Promise<string> {
    await vscode.commands.executeCommand('workbench.action.openSettings', '@ext:rascalmpl');
    throw Error("User setup required for jdk " + currentJVMEngineMin);
}

async function downloadJDK(): Promise<string> {
    const options = [];
    if (dl.temurinSupported(currentPreferredJVMEngine)) {
        options.push(temurin);
    }
    if (dl.microsoftSupported(currentPreferredJVMEngine)) {
        options.push(msJava);
    }
    if (dl.correttoSupported(currentPreferredJVMEngine)) {
        options.push(amazon);
    }
    const choice = await vscode.window.showInformationMessage("Select which OpenJDK provider you prefer", {modal:true}, ...options);

    if(!choice){
        throw new Error("User setup required");
    }

    const result = await downloadJDKWithProgress(choice);
    vscode.window.showInformationMessage(`Finished downloading ${choice} JDK, Rascal will now start`);
    return result;
}

async function downloadJDKWithProgress(choice: string) {
    return vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: `Downloading ${choice} JDK:`,
        cancellable: false
    }, async (progress, _cancelled ) => {
        function actualProgress(percIncrement: number, message: string) {
            progress.report({message: message, increment: percIncrement});
        }
        switch (choice) {
            case temurin: return dl.downloadTemurin(mainJVMPath, currentPreferredJVMEngine, actualProgress);
            case msJava: return dl.downloadMicrosoftJDK(mainJVMPath, currentPreferredJVMEngine, actualProgress);
            case amazon: return dl.downloadCorretto(mainJVMPath, currentPreferredJVMEngine, actualProgress);
            default:  throw new Error("User setup required");
        }
    });
}

export async function checkForJVMUpdate(mainJVMsPath:string = mainJVMPath){
    if (existsSync(mainJVMsPath)) {
        for (const ent of readdirSync(mainJVMsPath, {  }).sort().reverse()) {
            const releaseFilePath = path.join(mainJVMsPath, ent.toString(), "release");
            const releaseInfo = readReleaseInfo(releaseFilePath);
            if(!releaseInfo.implementor){
                continue;
            }
            switch (releaseInfo.implementor){
                case "Eclipse Adoptium": {
                    if(!releaseInfo.full_version){
                        break;
                    }
                    const latest = await dl.identifyLatestTemurinLTSRelease(currentPreferredJVMEngine, dl.mapTemuringCorrettoArch(), dl.mapTemurinPlatform());
                    if("jdk-"+releaseInfo.full_version !== latest){
                        askUserForJVMUpdate(temurin);
                    }
                    return;
                }
                case "Microsoft": {
                    if(!releaseInfo.java_version){
                        break;
                    }
                    const latest = await dl.identifyLatestMicrofotJDKRelease(currentPreferredJVMEngine, dl.mapMSArchitectures(), dl.mapMSPlatforms());
                    if(releaseInfo.java_version !== latest){
                        askUserForJVMUpdate(msJava);
                    }
                    return;
                }
                case "Amazon.com Inc.": {
                    if(!releaseInfo.implementor_version){
                        break;
                    }
                    const latest = await dl.identifyLatestCorrettoRelease(currentPreferredJVMEngine, dl.mapTemuringCorrettoArch(), dl.mapCorrettoPlatform());
                    if(releaseInfo.implementor_version.slice(9) !== latest){
                        askUserForJVMUpdate(amazon);
                    }
                    return;
                }
            }
        }
    }
}

export async function askUserForJVMUpdate(jdktype: string){
    vscode.window.showInformationMessage(`A new release is available for ${jdktype} openJDK. Would you like to install it ?`, ...["yes", "no"]).then(async ans => {
        if(ans === "yes"){
            await downloadJDKWithProgress(jdktype);
            vscode.window.showInformationMessage(`Finished updating ${jdktype} JDK. The new version will be used for the next VSCode session.`);
        }
    });
}