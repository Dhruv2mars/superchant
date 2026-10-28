// The runtime fingerprint decides which installed builds an update reaches (app.config.ts runtimeVersion).
// package.json scripts (test, lint, ...) never reach the native app, so a new script must not cut every
// installed build off from updates. The defaults stay (they skip the date-based version, for one).
/** @type {import('expo/fingerprint').Config} */
const { DEFAULT_SOURCE_SKIPS, SourceSkips } = require('expo/fingerprint');

module.exports = {
  sourceSkips: DEFAULT_SOURCE_SKIPS | SourceSkips.PackageJsonScriptsAll,
};
