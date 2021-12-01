import * as vscode from 'vscode';
import * as path from 'path';
import * as os from 'os';
import * as cp from 'child_process';
import { correttoSupported, downloadCorretto, downloadMicrosoftJDK, downloadTemurin, microsoftSupported, temurinSupported } from './downloaders';
import { exists, existsSync, readdirSync } from 'fs';
import { promisify } from 'util';


const currentJVMEngineTarget = 11;
const mainJVMPath = path.join(os.homedir(), ".jvm", `jdk${currentJVMEngineTarget}`);

let lookupCompleted: Thenable<string> | undefined;

const pexec = promisify(cp.exec);

export async function getJavaExecutable(): Promise<string> {
    if (lookupCompleted) {
        console.log("Returning: " + lookupCompleted);
        return lookupCompleted;
    }
    for (const possibleCandidate of getJavaCandidates()) {
        try {
            const versionRun = await pexec(`"${possibleCandidate}" -version`);
            const versionsFound = /version "([0-9]+)\./.exec(versionRun.stderr);
            if (versionsFound && versionsFound.length > 0) {
                if (Number(versionsFound[1]) >= 11) {
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
    lookupCompleted.then(good => {}, e => { lookupCompleted = undefined; });
    return lookupCompleted;
}

function getJavaCandidates(): string[] {
    let result = [];
    const { JAVA_HOME } = process.env;
    const name = os.platform() === 'win32' ? 'java.exe' : 'java';
    if (JAVA_HOME) {
        result.push(path.join(JAVA_HOME, "bin", name));
    }
    else {
        result.push(name);
    }
    if (existsSync(mainJVMPath)) {
        for (const ent of readdirSync(mainJVMPath, {  })) {
            let possiblePath = "";
            switch (os.platform()) {
                case 'win32': possiblePath = path.join(mainJVMPath, String(ent), "bin", "java.exe"); break;
                case 'linux': possiblePath = path.join(mainJVMPath, String(ent), "bin", "java"); break;
                case 'darwin': possiblePath = path.join(mainJVMPath, String(ent), "Contents", "Home", "bin", "java"); break;
            }
            if (existsSync(possiblePath)) {
                result.push(possiblePath);
            }
        }
    }
    return result;
}

export async function askUserForJVM() : Promise<string> {
    const selfInstall = "Install myself & restart vscode";
    const extensionInstall = "Automatically download Java 11";
    const configurePath = "Configure JDK path";

    const opt = await vscode.window.showErrorMessage(
        "Rascal (or a DSL that uses Rascal) requires a Java 11+ runtime. Shall we download it, or will you set it up yourself?",
        { modal: true},
        selfInstall, extensionInstall, configurePath
    );
    if (opt === extensionInstall) {
        return startAutoInstall();
    }
    if (opt === configurePath) {
        return openSettings();
    }
    throw Error("User setup required for jdk 11");
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
    throw new Error("User setup required for jdk 11");
}

async function openSettings(): Promise<string> {
    await vscode.commands.executeCommand('workbench.action.openSettings', '@ext:rascalmpl');
    throw new Error("User setup required for jdk 11");
}

async function downloadJDK(): Promise<string> {
    const temurin = "Eclipse Temurin";
    const msJava = "Microsoft Build of OpenJDK";
    const amazon = "Amazon Corretto";
    let options = [];
    if (temurinSupported(currentJVMEngineTarget)) {
        options.push(temurin);
    }
    if (microsoftSupported(currentJVMEngineTarget)) {
        options.push(msJava);
    }
    if (correttoSupported(currentJVMEngineTarget)) {
        options.push(amazon);
    }
    const choice = await vscode.window.showQuickPick(options, { canPickMany: false, placeHolder: "Select OpenJDK build"});

    const result = await vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: `Downloading ${choice} JDK:`,
        cancellable: false
    }, async (progress, cancelled ) => {
        function actualProgress(percIncrement: number, message: string) {
            progress.report({message: message, increment: percIncrement});
        }
        switch (choice) {
            case temurin: return downloadTemurin(mainJVMPath, currentJVMEngineTarget, actualProgress);
            case msJava: return downloadMicrosoftJDK(mainJVMPath, currentJVMEngineTarget, actualProgress);
            case amazon: return downloadCorretto(mainJVMPath, currentJVMEngineTarget, actualProgress);
            default:  throw new Error("User setup required");
        }
    });
    vscode.window.showInformationMessage(`Finished downloading ${choice} JDK, rascal will now start`);
    return result;
}




