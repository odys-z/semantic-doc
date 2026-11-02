const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');

// symlinks: false: the linked @anclient packages (TS sources) resolve their imports
// (react, MUI, ...) from this project's node_modules, so there is one React
// csstype: types only, imported by @anclient sources
const resolve = { extensions: ['.tsx', '.ts', '.jsx', '.js'], symlinks: false, alias: { csstype: false } };
// Babel compiles our sources, the linked @anclient packages and TS-only packages they use
const notOurs = /node_modules[\\/](?!(@anclient|mime-types-no-nodejs)[\\/])/;

module.exports = (env, argv) => {
  // pass webpack's mode to Babel as its env, so JSX is compiled for prod / dev React
  const rules = [
    { test: /\.[jt]sx?$/, exclude: notOurs, loader: 'babel-loader', options: { envName: argv.mode || 'production' } },
    { test: /\.css$/, use: ['style-loader', 'css-loader'] },
    // KaTeX fonts (demo / test pages; host apps bring their own KaTeX CSS)
    { test: /\.(woff2?|ttf)$/, type: 'asset/resource', generator: { filename: 'fonts/[name][ext]' } },
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

  // beautifier
  const { html: beautify } = require('js-beautify');

  class BeautifyHtml {
    apply(compiler) {
      compiler.hooks.compilation.tap('BeautifyHtml', (compilation) => {
        HtmlWebpackPlugin.getHooks(compilation).beforeEmit.tap('BeautifyHtml', (data) => {
          data.html = beautify(data.html, { indent_size: 2 });
          return data;
        });
      });
    }
  }

  // npm start / npm run build: browser pages
  const prod = argv.mode === 'production';
  const pages = [
    { name: 'demo', entry: './demo/index.tsx', filename: 'index.html', title: 'semantic-doc editor' },
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
    plugins: [...pages.map(
      (p) =>
        new HtmlWebpackPlugin({
          template: './demo/index.html',
          filename: p.filename,
          title: p.title,
          chunks: [p.name],
          minify: false
        })), new BeautifyHtml()],
    devServer: { port: 8080 },
    performance: { hints: false },
    optimization: { minimizeOptions: { html: false } }
  };
};
