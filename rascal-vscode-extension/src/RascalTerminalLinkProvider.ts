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
/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
import * as vscode from 'vscode';
import { CancellationToken, ProviderResult, TerminalLink, TerminalLinkContext, TerminalLinkProvider } from 'vscode';

interface ExtendedLink extends TerminalLink {
    loc: SourceLocation;
}

interface SourceLocation {
    uri: string;
    offsetLength?: [number, number];
    beginLineColumn?: [number, number];
    endLineColumn?: [number, number];
}

/**
 * We only detect Source Locations, as normal markdown links are already detected correctly by vscode
 */
export class RascalTerminalLinkProvider implements TerminalLinkProvider<ExtendedLink> {

    linkDetector() {
        // sadly java script regex store state, so we have to create a new one everytime
        return new RegExp(
        "\\|[^\\t-\\n\\r\\s\\|:]+://[^\\t-\\n\\r\\s\\|]*\\|" // |location|
        + "(\\([^\\)]*\\))?" // (optional offset)
      , "g");
    }

    provideTerminalLinks(context: TerminalLinkContext, token: CancellationToken): ProviderResult<ExtendedLink[]> {
        if (!context.terminal.name.includes("Rascal")) {
            return null;
        }
        const matcher = this.linkDetector();
        let match: RegExpExecArray | null;
        let result: ExtendedLink[] = [];
        while ((match = matcher.exec(context.line)) !== null && !token.isCancellationRequested) {
            result.push(buildLink(match));
        }
        return result === [] ? null : result;
    }

    async handleTerminalLink(link: ExtendedLink): Promise<void> {
        const sloc = link.loc;
        if (sloc.uri.startsWith("http")) {
            return vscode.commands.executeCommand("vscode.open", sloc.uri) ;
        }

        const td = await vscode.workspace.openTextDocument(vscode.Uri.parse(sloc.uri));
        const te = await vscode.window.showTextDocument(td);

        const targetRange = translateRange(sloc, td);
        if (targetRange) {
            te.revealRange(targetRange);
            te.selection = new vscode.Selection(
                targetRange.start.line,
                targetRange.start.character,
                targetRange.end.line,
                targetRange.end.character,
            );
        }
    }
}

function translateRange(sloc: SourceLocation, td: vscode.TextDocument): vscode.Range | undefined {
    if (sloc.beginLineColumn && sloc.endLineColumn) {
        const beginLine = sloc.beginLineColumn[0] - 1;
        const endLine = sloc.endLineColumn[0] - 1;
        return new vscode.Range(
            beginLine,
            fixedColumn(td, beginLine, sloc.beginLineColumn[1]),
            endLine,
            fixedColumn(td, endLine, sloc.endLineColumn[1]),
        );
    }
    else if (sloc.offsetLength) {
        const rangePositions = fixedOffsetLengthPositions(td, sloc.offsetLength[0], sloc.offsetLength[1]);
        return new vscode.Range(
            td.positionAt(rangePositions[0]),
            td.positionAt(rangePositions[1])
        );
    }
    return undefined;
}

function buildLink(match: RegExpExecArray): ExtendedLink {
    const linkMatch = match[0];
    const linkOffset = match.index + 1;
    const linkLength = linkMatch.indexOf('|', 2);
    const sloc = <SourceLocation>{ uri: linkMatch.substring(1, linkLength) };
    const numbers = linkMatch.substring(linkLength).match(/\d+/g,);
    if (numbers && numbers.length >= 2) {
        sloc.offsetLength = [Number(numbers[0]), Number(numbers[1])];
        if (numbers.length === 6) {
            // we have a full loc
            sloc.beginLineColumn = [Number(numbers[2]), Number(numbers[3])];
            sloc.endLineColumn = [Number(numbers[4]), Number(numbers[5])];
        }
    }

    return <ExtendedLink>{
        startIndex: linkOffset,
        length: linkLength,
        loc: sloc
    };
}

// from https://github.com/microsoft/vscode/blob/main/src/vs/base/common/strings.ts
function isHighSurrogate(charCode: number): boolean {
    return (0xD800 <= charCode && charCode <= 0xDBFF);
}

// from https://github.com/microsoft/vscode/blob/main/src/vs/base/common/strings.ts
function isLowSurrogate(charCode: number): boolean {
    return (0xDC00 <= charCode && charCode <= 0xDFFF);
}


/**
 * locate surrogate pairs on the current line, and if present, offset rascal columns by the surrogate pairs before it
 */
function fixedColumn(td: vscode.TextDocument, line: number, originalColumn: number): number {
    const fullLine = td.lineAt(line).text;
    let result = originalColumn;
    for (let i = 0; i < fullLine.length && i < result; i++) {
        const c = fullLine.charCodeAt(i);
        if (isHighSurrogate(c) && (i + 1) < fullLine.length && isLowSurrogate(fullLine.charCodeAt(i + 1))) {
            i++;
            result++;
        }
    }
    return result;
}


function fixedOffsetLengthPositions(td: vscode.TextDocument, offset: number, length: number): [number, number] {
    const fullText = td.getText();
    let endOffset = offset+length;
    for (let i = 0; i < fullText.length && i < endOffset; i++) {
        const c = fullText.charCodeAt(i);
        if (isHighSurrogate(c) && (i + 1) < fullText.length && isLowSurrogate(fullText.charCodeAt(i + 1))) {
            if (i <= offset) {
                offset++;
            }
            endOffset++;
            i++;
        }
    }
    return [offset, endOffset];
}
