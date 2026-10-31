// babel-loader passes webpack's target as caller.target ('web' | 'node')
module.exports = (api) => {
  const target = api.caller((caller) => caller && caller.target);
  return {
    presets: [
      ['@babel/preset-env', { targets: target === 'node' ? { node: 'current' } : 'defaults' }],
      ['@babel/preset-react', { runtime: 'automatic' }],
      '@babel/preset-typescript',
    ],
  };
};
