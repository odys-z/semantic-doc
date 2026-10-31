const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const resolve = { extensions: ['.tsx', '.ts', '.jsx', '.js'] };

module.exports = (env, argv) => {
  // pass webpack's mode to Babel as its env, so JSX is compiled for prod / dev React
  const rules = [
    { test: /\.[jt]sx?$/, exclude: /node_modules/, loader: 'babel-loader', options: { envName: argv.mode || 'production' } },
    { test: /\.css$/, use: ['style-loader', 'css-loader'] },
  ];

  // npm test: bundle the test runner for Node and run it
  if (env.test) {
    return {
      context: __dirname,
      target: 'node',
      entry: { run: './test/run.ts' },
      output: { path: path.resolve(__dirname, 'dist-test'), filename: '[name].js', clean: true },
      devtool: 'source-map',
      resolve,
      module: { rules: [rules[0]] },
    };
  }

  // npm start / npm run build: browser pages
  const prod = argv.mode === 'production';
  const pages = [
    { name: 'editor', entry: './editor/index.tsx', filename: 'index.html', title: 'semantic-doc editor' },
    // the browser test page is served in development only
    ...(prod ? [] : [{ name: 'test', entry: './test/index.tsx', filename: 'test.html', title: 'semantic-doc tests' }]),
  ];

  return {
    context: __dirname,
    entry: Object.fromEntries(pages.map((p) => [p.name, p.entry])),
    output: { path: path.resolve(__dirname, 'dist'), filename: '[name].[contenthash].js', clean: true },
    devtool: 'source-map',
    resolve,
    module: { rules },
    plugins: pages.map(
      (p) =>
        new HtmlWebpackPlugin({
          template: './editor/index.html',
          filename: p.filename,
          title: p.title,
          chunks: [p.name],
        }),
    ),
    devServer: { port: 8080 },
    performance: { hints: false },
  };
};
