'use strict';

const exec = require('cordova/exec');
const createApi = require('./nativecodescanner-core');

const api = createApi(exec);

if (typeof cordova !== 'undefined') {
  cordova.plugins = cordova.plugins || {};
  cordova.plugins.nativeCodeScanner = api;
}

module.exports = api;
