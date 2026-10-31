// babel-loader passes webpack's target as caller.target ('web' | 'node')
module.exports = (api) => {
  const target = api.caller((caller) => caller && caller.target);
  return {
    presets: [
      ['@babel/preset-env', { targets: target === 'node' ? { node: 'current' } : 'defaults' }],
      // development: jsxDEV (needs React's dev build); production: jsx
      ['@babel/preset-react', { runtime: 'automatic', development: api.env('development') }],
      '@babel/preset-typescript',
    ],
  };
};
