import * as vscode from "vscode";
import { SourceLocation } from '../RascalTerminalLinkProvider';

/**
 * This class allows to convert text locations between VS Code and Rascal.
 * Some characters, which VS Code counts as two characters because it uses UTF-16 encoding,
 * are only a single character for Rascal, which uses UTF-32 encoding.
 *
 * Assuming UTF-16 encoding, because this code is executed in TypeScript,
 * all methods in this class therefore iterate character-wise over a text to find pairs of characters
 * that would be encoded as a single character in UTF-32.
 *
 * If they find such a pair, they change the position to encount for the difference in encoding:
 * If the position comes from Rascal, it is too short and must be increased by 1.
 * If the position comes from VS Code, it is too long and must be decreased by 1.
 */
export class PositionConverter {

    /***************************************
     *              Rascal -> VS Code      *
     ***************************************/

    /**
     * Converts the column position from Rascal (UTF-32) to VS Code (UTF-16).
     * @param td the text document where the column information is located in.
     * @param line the line in which the column to be changed is located.
     * @param rascalColumn the column as given by Rascal.
     * @returns the column as understood by VS Code.
     */
    static rascalToVSCodeColumn(td: vscode.TextDocument, line: number, rascalColumn: number): number {
        const fullLine = td.lineAt(line).text;
        let result = rascalColumn;
        for (let i = 0; i < fullLine.length && i < result; i++) {
            const c = fullLine.charCodeAt(i);
            if (PositionConverter.isHighSurrogate(c) && (i + 1) < fullLine.length && PositionConverter.isLowSurrogate(fullLine.charCodeAt(i + 1))) {
                i++;
                result++;
            }
        }
        return result;
    }

    /**
     * Converts the offset and length position from Rascal (UTF-32) to VS Code (UTF-16).
     * @param td the text document where the information is located in.
     * @param offset the offset as given by Rascal.
     * @param length the length as given by Rascal.
     * @returns the offset and length as understood by VS Code.
     */
    static rascalToVSCodeOffsetLength(td: vscode.TextDocument, offset: number, length: number): [number, number] {
        const fullText = td.getText();
        let endOffset = offset + length;
        for (let i = 0; i < fullText.length && i < endOffset; i++) {
            const c = fullText.charCodeAt(i);
            if (PositionConverter.isHighSurrogate(c) && (i + 1) < fullText.length && PositionConverter.isLowSurrogate(fullText.charCodeAt(i + 1))) {
                if (i <= offset) { // the character comes before the offset, so it must shift the offset
                    offset++;
                }
                endOffset++;
                i++;
            }
        }
        return [offset, endOffset];
    }

    /**
     * Converts a range from Rascal (UTF-32) to VS Code (UTF-16).
     * A range is given in the form of a SourceLocation which can encode a range
     * either as an offset and length or using pairs of line and column
     * for begin and end of the range.
     * @param td the text document where the information is located in.
     * @param sloc a source location as given by Rascal.
     * @returns the range as understood by VS Code or `undefined`, if the range is not specified correctly.
     */
    static rascalToVSCodeRange(td: vscode.TextDocument, sloc: SourceLocation): vscode.Range | undefined {
        if (sloc.beginLineColumn && sloc.endLineColumn) {
            const beginLine = sloc.beginLineColumn[0] - 1;
            const endLine = sloc.endLineColumn[0] - 1;
            return new vscode.Range(
                beginLine,
                PositionConverter.rascalToVSCodeColumn(td, beginLine, sloc.beginLineColumn[1]),
                endLine,
                PositionConverter.rascalToVSCodeColumn(td, endLine, sloc.endLineColumn[1])
            );
        }
        else if (sloc.offsetLength) {
            const rangePositions = PositionConverter.rascalToVSCodeOffsetLength(td, sloc.offsetLength[0], sloc.offsetLength[1]);
            return new vscode.Range(
                td.positionAt(rangePositions[0]),
                td.positionAt(rangePositions[1])
            );
        }
        return undefined;
    }

    /***************************************
     *              VS Code -> Rascal      *
     ***************************************/

    /**
     * Converts the column position from VS Code (UTF-16) to Rascal (UTF-32).
     * @param td the text document where the column information is located in.
     * @param line the line in which the column to be changed is located.
     * @param columnVSCode the column as given by VS Code.
     * @returns the column as understood by Rascal.
     */
    static vsCodeToRascalColumn(td: vscode.TextDocument, line: number, columnVSCode: number): number {
        const fullLine = td.lineAt(line).text;
        let lengthRascal = columnVSCode;

        for (let i = 0; i < columnVSCode - 1; i++) {
            const c = fullLine.charCodeAt(i);
            if (PositionConverter.isHighSurrogate(c) && PositionConverter.isLowSurrogate(fullLine.charCodeAt(i + 1))) {
                lengthRascal--;
                i++; // the following letter is known to be the low surrogate -> we can skip it
            }
        }

        return lengthRascal;
    }

    /**
     * Converts the offset and length position from VS Code (UTF-16) to Rascal (UTF-32).
     * @param td the text document where the information is located in.
     * @param offset the offset as given by VS Code.
     * @param length the length as given by VS Code.
     * @returns the offset and length as understood by Rascal.
     */
    static vsCodeToRascalOffsetLength(td: vscode.TextDocument, offset: number, length: number): [number, number] {
        const fullText = td.getText();
        const endOffset = offset + length;
        let newEndOffset = endOffset;
        for (let i = 0; i < endOffset - 1; i++) {
            const c = fullText.charCodeAt(i);
            if (PositionConverter.isHighSurrogate(c) && PositionConverter.isLowSurrogate(fullText.charCodeAt(i + 1))) {
                if (i <= offset) {
                    offset--;
                }
                newEndOffset--;
                i++; // the following letter is known to be the low surrogate -> we can skip it
            }
        }
        return [offset, newEndOffset];
    }

    /**
     * Converts a range from VS Code (UTF-16) to Rascal (UTF-32).
     * A range is given in the form of a SourceLocation which can encode a range
     * either as an offset and length or using pairs of line and column
     * for begin and end of the range.
     * @param td the text document where the information is located in.
     * @param sloc a source location as given by VS Code.
     * @returns the range as understood by Rascal or `undefined`, if the range is not specified correctly.
     */
    static vsCodeToRascalRange(td: vscode.TextDocument, sloc: SourceLocation): vscode.Range | undefined {
        if (sloc.beginLineColumn && sloc.endLineColumn) {
            const beginLine = sloc.beginLineColumn[0];
            const endLine = sloc.endLineColumn[0];
            return new vscode.Range(
                beginLine,
                PositionConverter.vsCodeToRascalColumn(td, beginLine, sloc.beginLineColumn[1]),
                endLine,
                PositionConverter.vsCodeToRascalColumn(td, endLine, sloc.endLineColumn[1])
            );
        }
        else if (sloc.offsetLength) {
            const rangePositions = PositionConverter.vsCodeToRascalOffsetLength(td, sloc.offsetLength[0], sloc.offsetLength[1]);
            return new vscode.Range(
                td.positionAt(rangePositions[0]),
                td.positionAt(rangePositions[1])
            );
        }
        return undefined;
    }

    /***************************************
     *              Util                   *
     ***************************************/

    // from https://github.com/microsoft/vscode/blob/main/src/vs/base/common/strings.ts
    static isHighSurrogate(charCode: number): boolean {
        return (55296 <= charCode && charCode <= 56319);
    }

    // from https://github.com/microsoft/vscode/blob/main/src/vs/base/common/strings.ts
    static isLowSurrogate(charCode: number): boolean {
        return (56320 <= charCode && charCode <= 57343);
    }
}
