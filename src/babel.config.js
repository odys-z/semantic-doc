// babel-loader passes webpack's target as caller.target ('web' | 'node')
module.exports = (api) => {
  const target = api.caller((caller) => caller && caller.target);
  return {
    presets: [
      ['@babel/preset-env', { targets: target === 'node' ? { node: 'current' } : 'defaults' }],
      '@babel/preset-typescript',
    ],
    // JSX in .tsx / .jsx only: .ts files may use <T>x casts (e.g. @anclient/semantier)
    // development: jsxDEV (needs React's dev build); production: jsx
    overrides: [{
      test: /\.[jt]sx$/,
      presets: [['@babel/preset-react', { runtime: 'automatic', development: api.env('development') }]],
    }],
  };
};
