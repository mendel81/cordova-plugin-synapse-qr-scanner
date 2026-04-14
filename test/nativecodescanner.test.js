'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const createApi = require('../www/nativecodescanner-core');

test('scan normalizes options and resolves a normalized result', async function () {
  let call;
  const api = createApi(function exec(success, error, service, action, args) {
    call = {
      service: service,
      action: action,
      args: args
    };

    success({
      cancelled: false,
      text: 'hello',
      format: 'QR_CODE',
      nativeFormat: 'QR_CODE',
      platform: 'android',
      engine: 'google'
    });
  });

  const result = await api.scan({
    formats: ['qr_code', 'ean_13', 'QR_CODE'],
    timeoutMs: 1250,
    androidEngine: 'AUTO'
  });

  assert.equal(call.service, 'NativeCodeScanner');
  assert.equal(call.action, 'scan');
  assert.deepEqual(call.args[0].formats, ['QR_CODE', 'EAN_13']);
  assert.equal(call.args[0].timeoutMs, 1250);
  assert.equal(call.args[0].androidEngine, 'auto');
  assert.equal(result.text, 'hello');
  assert.equal(result.cancelled, false);
});

test('scan turns cancelled native errors into resolved cancelled results', async function () {
  const api = createApi(function exec(success, error) {
    error({
      code: 'SCAN_CANCELLED',
      message: 'User cancelled'
    });
  });

  const result = await api.scan();
  assert.equal(result.cancelled, true);
  assert.equal(result.text, null);
});

test('callback wrappers still return promises', async function () {
  const api = createApi(function exec(success) {
    success({
      supported: true,
      available: true,
      platform: 'ios',
      availableEngines: ['avfoundation'],
      supportedFormats: ['QR_CODE']
    });
  });

  let callbackPayload;
  const promise = api.isSupported(function onSuccess(result) {
    callbackPayload = result;
  });

  const result = await promise;
  assert.equal(result.supported, true);
  assert.deepEqual(callbackPayload, result);
});

test('invalid options fail fast before hitting native', async function () {
  let called = false;
  const api = createApi(function exec() {
    called = true;
  });

  await assert.rejects(
    api.scan({
      formats: ['not-a-real-format']
    }),
    function verify(error) {
      assert.equal(error.code, 'INVALID_FORMAT');
      return true;
    }
  );

  assert.equal(called, false);
});
