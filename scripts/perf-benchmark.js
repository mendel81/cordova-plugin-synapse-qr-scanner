'use strict';

const { performance } = require('node:perf_hooks');
const createApi = require('../www/nativecodescanner-core');

const ITERATIONS = Number.parseInt(process.env.NATIVE_CODE_SCANNER_BENCH_ITERATIONS || '20000', 10);
const WARMUP_ITERATIONS = Number.parseInt(process.env.NATIVE_CODE_SCANNER_BENCH_WARMUP || '1000', 10);

function createMockApi() {
  return createApi(function exec(success, error, service, action) {
    switch (action) {
      case 'isSupported':
        success({
          supported: true,
          available: true,
          platform: 'android',
          defaultEngine: 'google',
          availableEngines: ['google', 'mlkit'],
          supportedFormats: ['QR_CODE', 'EAN_13', 'CODE_128'],
          unsupportedFormats: [],
          requiresPermission: false,
          permissionOptional: true,
          details: null
        });
        return;
      case 'checkPermission':
      case 'requestPermission':
        success({
          granted: true,
          status: 'granted',
          canRequest: false,
          requiresPermission: false,
          platform: 'android',
          engine: 'google'
        });
        return;
      case 'scan':
        success({
          cancelled: false,
          text: '1234567890123',
          format: 'EAN_13',
          nativeFormat: 'EAN_13',
          engine: 'google',
          platform: 'android',
          rawBytesBase64: null,
          valueType: 'PRODUCT',
          bounds: null,
          cornerPoints: [],
          timestamp: Date.now()
        });
        return;
      case 'cancel':
      case 'prewarm':
      default:
        success({});
    }
  });
}

function createCancelledScanApi() {
  return createApi(function exec(success, error, service, action) {
    if (action === 'scan') {
      error({
        code: 'SCAN_CANCELLED',
        message: 'User cancelled scan'
      });
      return;
    }

    success({});
  });
}

function percentile(sortedSamples, fraction) {
  return sortedSamples[Math.min(sortedSamples.length - 1, Math.floor(sortedSamples.length * fraction))];
}

function summarize(name, samples) {
  const sorted = samples.slice().sort(function ascending(left, right) {
    return left - right;
  });
  const total = sorted.reduce(function accumulate(sum, sample) {
    return sum + sample;
  }, 0);
  const mean = total / sorted.length;

  return {
    name: name,
    iterations: sorted.length,
    p50Ms: percentile(sorted, 0.5),
    p95Ms: percentile(sorted, 0.95),
    p99Ms: percentile(sorted, 0.99),
    meanMs: mean,
    opsPerMinute: 60000 / mean
  };
}

async function runCase(name, iterations, invoke) {
  const samples = [];

  for (let index = 0; index < WARMUP_ITERATIONS; index += 1) {
    await invoke();
  }

  for (let index = 0; index < iterations; index += 1) {
    const start = performance.now();
    await invoke();
    samples.push(performance.now() - start);
  }

  return summarize(name, samples);
}

async function main() {
  const api = createMockApi();
  const cancelledScanApi = createCancelledScanApi();
  const richScanOptions = {
    formats: ['QR_CODE', 'EAN_13', 'CODE_128', 'QR_CODE'],
    prompt: 'Apunta al código',
    preferFrontCamera: false,
    showTorchButton: true,
    showFlipCameraButton: false,
    beepOnSuccess: true,
    vibrateOnSuccess: false,
    timeoutMs: 0,
    androidEngine: 'auto',
    returnRawBytes: false
  };

  const results = [];
  results.push(await runCase('isSupported', ITERATIONS, function invoke() {
    return api.isSupported();
  }));
  results.push(await runCase('checkPermission', ITERATIONS, function invoke() {
    return api.checkPermission();
  }));
  results.push(await runCase('scan-rich-options', ITERATIONS, function invoke() {
    return api.scan(richScanOptions);
  }));
  results.push(await runCase('scan-cancelled', ITERATIONS, function invoke() {
    return cancelledScanApi.scan(richScanOptions);
  }));

  console.log(JSON.stringify({
    environment: {
      iterations: ITERATIONS,
      warmupIterations: WARMUP_ITERATIONS,
      node: process.version
    },
    results: results
  }, null, 2));
}

main().catch(function onError(error) {
  console.error(error);
  process.exitCode = 1;
});
