//@ts-check

'use strict';

const path = require('path');
const CopyPlugin = require("copy-webpack-plugin");

//@ts-check
/** @typedef {import('webpack').Configuration} WebpackConfig **/

/** @type WebpackConfig */
const extensionConfig = {
  target: "node",
  mode: "none",
  entry: "./src/extension.ts",
  output: {
    path: path.resolve(__dirname, "dist"),
    filename: "extension.js",
    libraryTarget: "commonjs2",
  },
  externals: {
    vscode: "commonjs vscode",
  },
  resolve: {
    extensions: [".ts", ".js"],
  },
  module: {
    rules: [
      {
        test: /\.ts$/,
        exclude: /node_modules/,
        use: [
          {
            loader: "ts-loader",
          },
        ],
      },
    ],
  },
  devtool: "nosources-source-map",
  infrastructureLogging: {
    level: "log",
  },
  plugins: [
    new CopyPlugin({
      patterns: [
        {
          // we copy the jars to an an easy to predict location
          from: path.resolve(
            __dirname,
            "node_modules/@usethesource/rascal-vscode-dsl-runtime/assets/jars/"
          ),
          to: path.resolve(__dirname, "dist/rascal-lsp/"),
        },
      ],
    }),
  ],
};
module.exports = [ extensionConfig ];