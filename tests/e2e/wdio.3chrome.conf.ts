import wdioConf = require('./wdio.2chrome.conf');

const config = <any> wdioConf.config;
const defCaps = config.capabilities.browserA.desiredCapabilities;

config.capabilities.browserC = {
  capabilities: { ...defCaps }
};

export = wdioConf;
