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
import * as vscode from 'vscode';

import { writeFileSync } from 'fs';

import { integer } from 'vscode-languageclient/node';
import { getJavaExecutable } from './auto-jvm/JavaLookup';
import { RascalLanguageServer } from './lsp/RascalLanguageServer';
import { LanguageParameter, ParameterizedLanguageServer } from './lsp/ParameterizedLanguageServer';
import { RascalTerminalLinkProvider } from './RascalTerminalLinkProvider';
import { VSCodeUriResolverServer } from './fs/VSCodeURIResolver';

const vsfs = vscode.workspace.fs;
const URI = vscode.Uri;

export class RascalExtension implements vscode.Disposable {
    private readonly vfsServer: VSCodeUriResolverServer;
    private readonly dsls:ParameterizedLanguageServer;
    private readonly rascal: RascalLanguageServer;


    constructor(private readonly context: vscode.ExtensionContext, private readonly jarRootPath: string, private readonly isDeploy = true) {
        this.vfsServer = new VSCodeUriResolverServer(!isDeploy);

        this.dsls = new ParameterizedLanguageServer(context, this.vfsServer, jarRootPath, isDeploy);
        this.rascal = new RascalLanguageServer(context, this.vfsServer, jarRootPath, this.dsls, isDeploy);

        this.registerTerminalCommand();
        this.registerMainRun();
        this.registerImportModule();
        this.createProject();

        vscode.window.registerTerminalLinkProvider(new RascalTerminalLinkProvider(this.rascal.rascalClient));
    }

    dispose() {
        this.vfsServer.dispose();
        this.dsls.dispose();
        this.rascal.dispose();
    }

    externalLanguageRegistry() {
        return {
            registerLanguage: (lang:LanguageParameter) => this.dsls.registerLanguage(lang),
            unregisterLanguage: (lang:LanguageParameter) => this.dsls.unregisterLanguage(lang),
            getRascalExtensionDeploymode: () => this.isDeploy,
        };
    }

    private registerTerminalCommand() {
        this.context.subscriptions.push(
            vscode.commands.registerCommand("rascalmpl.createTerminal", () => {
                this.startTerminal(vscode.window.activeTextEditor?.document.uri);
            })
        );
    }

    private registerMainRun() {
        this.context.subscriptions.push(
            vscode.commands.registerTextEditorCommand("rascalmpl.runMain", (text, _edit, moduleName) => {
                if (!text.document.uri || !moduleName) {
                    return;
                }
                this.startTerminal(text.document.uri, "--loadModule", moduleName, "--runModule");
            })
        );
    }

    private createProject() {
        this.context.subscriptions.push(
            vscode.commands.registerCommand("rascalmpl.createProject", 
            async () => {
                try {
                    const filePath = await vscode.window.showOpenDialog({
                        canSelectFiles: false,
                        canSelectFolders: true,
                        canSelectMany: false,
                        title: "New Rascal project",
                    });

                    if (filePath) {
                        const dest = filePath[0].path;
                        const destUri = URI.file(dest);
                        const projectName = dest.split("/").at(-1) as string;

                        newRascalProject(dest, projectName);

                        // Open a workspace window
                        await vscode.commands.executeCommand("vscode.openFolder", destUri, true);
                    }
                } catch (e) {
                    console.error(e);
                }
            })
        );
    }

    private registerImportModule() {
        this.context.subscriptions.push(
            vscode.commands.registerTextEditorCommand("rascalmpl.importModule", (text, _edit, moduleName) => {
                if (!text.document.uri || !moduleName) {
                    return;
                }
                this.startTerminal(text.document.uri, "--loadModule", moduleName);
            })
        );
    }

    private startTerminal(uri: vscode.Uri | undefined, ...extraArgs: string[]) {
        return vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            cancellable: false,
            title: "Rascal terminal"
        }, async (progress) => {
            progress.report({message: "Starting rascal-lsp"});
            const rascal = await this.rascal.rascalClient;
            console.log(`Starting Rascal REPL: on ${uri} and with args: ${extraArgs}`);
            if (uri && !uri.path.endsWith(".rsc")) {
                // do not try to figure out a rascal project path when the focus is not a rascal file
                uri = undefined;
            }
            progress.report({increment: 25, message: "Requesting IDE configuration"});
            const serverConfig = await rascal.sendRequest<IDEServicesConfiguration>("rascal/supplyIDEServicesConfiguration");
            progress.report({increment: 25, message: "Calculating project class path"});
            const compilationPath = await rascal.sendRequest<string[]>("rascal/supplyProjectCompilationClasspath", { uri: uri?.toString() });
            progress.report({increment: 25, message: "Creating terminal"});
            const terminal = vscode.window.createTerminal({
                cwd: path.dirname(uri?.fsPath || ""),
                shellPath: await getJavaExecutable(),
                shellArgs: this.buildShellArgs(compilationPath, serverConfig, ...extraArgs),
                name: 'Rascal Terminal',
            });

            terminal.show(false);
            progress.report({increment: 25, message: "Finished creating terminal"});
        });
    }

    private buildShellArgs(classPath: string[], ide: IDEServicesConfiguration, ...extraArgs: string[]) {
        const shellArgs = [
                calculateRascalREPLMemory()
                , '-cp'
                , this.buildTerminalJVMPath() + (classPath.length > 0 ? (path.delimiter + classPath.join(path.delimiter)) : ''),
        ];
        if (!this.isDeploy) {
            // for development mode we always start the terminal with debuging ready to go
            shellArgs.push(
                '-Xdebug'
                , '-Xrunjdwp:transport=dt_socket,address=9001,server=y,suspend=n'
            );
        }
        shellArgs.push();
        shellArgs.push(
            '-Drascal.fallbackResolver=org.rascalmpl.vscode.lsp.uri.FallbackResolver'
            , 'org.rascalmpl.vscode.lsp.terminal.LSPTerminalREPL'
            , '--ideServicesPort'
            , '' + ide.port
            , '--vfsPort'
            , '' + this.vfsServer.port
        );
        return shellArgs.concat(extraArgs || []);
    }
    private buildTerminalJVMPath() :string {
        const jars = ['rascal-lsp.jar', 'rascal.jar'];
        return jars.map(j => path.join(this.jarRootPath, j)).join(path.delimiter);
    }

}

interface IDEServicesConfiguration {
    port:integer;
}



function gb(amount: integer) {
    return amount * (1024 * 1024 * 1024);
}

function calculateRascalREPLMemory() {
    if (os.totalmem() >= gb(32)) {
        return "-Xmx9000M";
    }
    if (os.totalmem() >= gb(16)) {
        return "-Xmx4800M";

    }
    if (os.totalmem() >= gb(8)) {
        return "-Xmx2400M";
    }
    return "-Xmx800M";
}

async function newRascalProject(dest: string, name: string) {
        
    const baseDest = URI.file(dest);
    vsfs.createDirectory(baseDest);

    const metaPath = URI.joinPath(baseDest, "META-INF");
    await vsfs.createDirectory(metaPath);

    const srcPath = URI.joinPath(baseDest, "src/main/rascal/");
    await vsfs.createDirectory(srcPath);

    const rascalMFPath = URI.joinPath(metaPath, "RASCAL.MF");
    writeFileSync(rascalMFPath.fsPath, rascalMF(name));

    const pomPath = URI.joinPath(baseDest, "pom.xml");
    writeFileSync(pomPath.fsPath, pomXML(name));

    const mainPath = URI.joinPath(srcPath, "Main.rsc");
    writeFileSync(mainPath.fsPath, emptyModule);

}

function rascalMF(name: string): string {
    return "Manifest-Version: 0.0.1\n" +
            `Project-Name: ${name}\n` +
            "Source: src/main/rascal\n" +
            "Require-Libraries: \n"
            ;
}

function pomXML(name: string, group="org.rascalmpl", version="0.1.0-SNAPSHOT"): string {
    return `<?xml version="1.0" encoding="UTF-8"?>
    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
      <modelVersion>4.0.0</modelVersion>
    
      <groupId>${group}</groupId>
      <artifactId>${name}</artifactId>
      <version>${version}</version>
    
      <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
      </properties>
    
      <repositories>
        <repository>
            <id>usethesource</id>
            <url>https://releases.usethesource.io/maven/</url>
        </repository>
      </repositories>
    
      <pluginRepositories>
        <pluginRepository>
           <id>usethesource</id>
           <url>https://releases.usethesource.io/maven/</url>
        </pluginRepository>
      </pluginRepositories>
    
      <dependencies>
        <dependency>
          <groupId>org.rascalmpl</groupId>
          <artifactId>rascal</artifactId>
          <version><getRascalVersion()></version>
        </dependency>
      </dependencies>
    
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.0</version>
            <configuration>
              <compilerArgument>-parameters</compilerArgument> 
              <release>11</release>
            </configuration>
          </plugin>
          <plugin>
            <groupId>org.rascalmpl</groupId>
            <artifactId>rascal-maven-plugin</artifactId>
            <version>0.8.2</version>
            <configuration>
              <errorsAsWarnings>true</errorsAsWarnings>
              <bin>\${project.build.outputDirectory}</bin>
              <srcs>
                <src>$\{project.basedir}/src/main/rascal</src>
              </srcs>
            </configuration>
          </plugin>
        </plugins>
      </build>
    </project>
    `;
}

const emptyModule = "module Main\n" +
                    "import IO;\n\n"+
                    "int main(int testArgument=0) {\n" +
                    `   println("argument: <testArgument>");\n` +
                    "   return testArgument;\n"+
                    "}";