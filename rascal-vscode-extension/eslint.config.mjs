import { defineConfig } from "eslint/config";
import editorconfig from "eslint-plugin-editorconfig";
import globals from "globals";
import tsParser from "@typescript-eslint/parser";
import typescriptEslint from "@typescript-eslint/eslint-plugin";
import path from "node:path";
import { fileURLToPath } from "node:url";
import js from "@eslint/js";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const compat = new FlatCompat({
    baseDirectory: __dirname,
    recommendedConfig: js.configs.recommended,
    allConfig: js.configs.all
});

export default defineConfig([{
    extends: compat.extends("eslint:recommended"),

    plugins: {
        editorconfig,
    },

    languageOptions: {
        globals: {
            ...globals.node,
            ...globals.browser,
        },

        parser: tsParser,
        ecmaVersion: 6,
        sourceType: "module",

        parserOptions: {
            projectService: true,
        },
    },

    rules: {
        curly: "warn",
        eqeqeq: "warn",
        "no-throw-literal": "warn",
        semi: "off",
        "no-unused-vars": "off",
        "no-console": "error",
    },
}, {
    files: ["**/*.ts"],

    extends: compat.extends(
        "plugin:@typescript-eslint/eslint-recommended",
        "plugin:@typescript-eslint/recommended",
    ),

    plugins: {
        "@typescript-eslint": typescriptEslint,
    },

    rules: {
        "@typescript-eslint/naming-convention": "warn",

        "@typescript-eslint/no-unused-vars": ["warn", {
            varsIgnorePattern: "^_",
            argsIgnorePattern: "^_",
            caughtErrorsIgnorePattern: "^_",
        }],

        "@typescript-eslint/no-floating-promises": "error",
        "@typescript-eslint/no-misused-promises": "error",
    },
}]);