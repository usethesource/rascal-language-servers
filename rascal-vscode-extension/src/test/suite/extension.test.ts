import * as assert from 'assert';

import * as vscode from 'vscode';
import * as rascalExtension from '../../extension';

suite('Extension Test Suite', () => {
	vscode.window.showInformationMessage('Start all tests.');

	test('Never commit debug mode', () => {
		assert.strictEqual(rascalExtension.getRascalExtensionDeploymode(), true);
	});
});
