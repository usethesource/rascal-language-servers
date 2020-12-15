"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const assert = require("assert");
// You can import and use all API from the 'vscode' module
// as well as import your extension to test it
const vscode = require("vscode");
const rascalExtension = require("../../extension");
suite('Extension Test Suite', () => {
    vscode.window.showInformationMessage('Start all tests.');
    test('Never commit debug mode', () => {
        assert.strictEqual(rascalExtension.getRascalExtensionDeploymode(), true);
    });
});
//# sourceMappingURL=extension.test.js.map