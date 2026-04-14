'use strict';

const SUPPORTED_FORMATS = Object.freeze([
  'AZTEC',
  'CODABAR',
  'CODE_39',
  'CODE_93',
  'CODE_128',
  'DATA_MATRIX',
  'EAN_8',
  'EAN_13',
  'ITF',
  'PDF_417',
  'QR_CODE',
  'UPC_A',
  'UPC_E'
]);
const SUPPORTED_FORMATS_SET = new Set(SUPPORTED_FORMATS);

const VALID_ANDROID_ENGINES = Object.freeze(['auto', 'google', 'mlkit']);
const VALID_ANDROID_ENGINES_SET = new Set(VALID_ANDROID_ENGINES);
const BOOLEAN_OPTION_KEYS = Object.freeze([
  'preferFrontCamera',
  'showTorchButton',
  'showFlipCameraButton',
  'beepOnSuccess',
  'vibrateOnSuccess',
  'returnRawBytes'
]);
const DEFAULT_OPTIONS = Object.freeze({
  formats: [],
  prompt: '',
  preferFrontCamera: false,
  showTorchButton: false,
  showFlipCameraButton: false,
  beepOnSuccess: true,
  vibrateOnSuccess: false,
  timeoutMs: 0,
  androidEngine: 'auto',
  returnRawBytes: false
});

const SERVICE_NAME = 'NativeCodeScanner';
const SCAN_CANCEL_CODES = new Set(['SCAN_CANCELLED', 'CANCELLED', 'ERR_CANCELLED']);

function createError(message, code, details) {
  const error = new Error(message || code || 'NativeCodeScanner returned an unknown error');
  error.code = code || 'UNKNOWN';
  if (details !== undefined) {
    error.details = details;
  }
  return error;
}

function normalizeExecError(payload) {
  if (!payload) {
    return createError('NativeCodeScanner returned an unknown error', 'UNKNOWN');
  }

  if (payload instanceof Error) {
    return payload;
  }

  if (typeof payload === 'string') {
    try {
      const parsed = JSON.parse(payload);
      return normalizeExecError(parsed);
    } catch (error) {
      return createError(payload, 'UNKNOWN');
    }
  }

  if (typeof payload === 'object') {
    return createError(payload.message, payload.code, payload.details);
  }

  return createError(String(payload), 'UNKNOWN');
}

function toCallbackPromise(promise, success, error) {
  if (typeof success === 'function' || typeof error === 'function') {
    promise.then(
      function handleSuccess(result) {
        if (typeof success === 'function') {
          success(result);
        }
      },
      function handleError(err) {
        if (typeof error === 'function') {
          error(err);
        }
      }
    );
  }

  return promise;
}

function normalizeFormats(formats) {
  if (!Array.isArray(formats)) {
    throw createError('formats must be an array of strings', 'INVALID_ARGUMENT');
  }

  const normalized = [];
  const seen = new Set();
  for (let index = 0; index < formats.length; index += 1) {
    const format = formats[index];
    if (typeof format !== 'string') {
      throw createError('Each format must be a string', 'INVALID_ARGUMENT');
    }

    const value = format.trim().toUpperCase();
    if (!SUPPORTED_FORMATS_SET.has(value)) {
      throw createError('Unsupported format: ' + format, 'INVALID_FORMAT', {
        format: format,
        supportedFormats: SUPPORTED_FORMATS
      });
    }

    if (!seen.has(value)) {
      seen.add(value);
      normalized.push(value);
    }
  }

  return normalized;
}

function normalizeScanOptions(options) {
  const source = options || {};
  if (source && typeof source !== 'object') {
    throw createError('scan options must be an object', 'INVALID_ARGUMENT');
  }

  const normalized = {
    formats: DEFAULT_OPTIONS.formats,
    prompt: DEFAULT_OPTIONS.prompt,
    preferFrontCamera: DEFAULT_OPTIONS.preferFrontCamera,
    showTorchButton: DEFAULT_OPTIONS.showTorchButton,
    showFlipCameraButton: DEFAULT_OPTIONS.showFlipCameraButton,
    beepOnSuccess: DEFAULT_OPTIONS.beepOnSuccess,
    vibrateOnSuccess: DEFAULT_OPTIONS.vibrateOnSuccess,
    timeoutMs: DEFAULT_OPTIONS.timeoutMs,
    androidEngine: DEFAULT_OPTIONS.androidEngine,
    returnRawBytes: DEFAULT_OPTIONS.returnRawBytes
  };

  if (Object.prototype.hasOwnProperty.call(source, 'formats')) {
    normalized.formats = normalizeFormats(source.formats || []);
  }

  if (Object.prototype.hasOwnProperty.call(source, 'prompt')) {
    if (typeof source.prompt !== 'string') {
      throw createError('prompt must be a string', 'INVALID_ARGUMENT');
    }
    normalized.prompt = source.prompt;
  }

  for (let index = 0; index < BOOLEAN_OPTION_KEYS.length; index += 1) {
    const key = BOOLEAN_OPTION_KEYS[index];
    if (Object.prototype.hasOwnProperty.call(source, key)) {
      normalized[key] = Boolean(source[key]);
    }
  }

  if (Object.prototype.hasOwnProperty.call(source, 'timeoutMs')) {
    if (!Number.isFinite(source.timeoutMs) || source.timeoutMs < 0) {
      throw createError('timeoutMs must be a number >= 0', 'INVALID_ARGUMENT');
    }
    normalized.timeoutMs = Math.floor(source.timeoutMs);
  }

  if (Object.prototype.hasOwnProperty.call(source, 'androidEngine')) {
    if (typeof source.androidEngine !== 'string') {
      throw createError('androidEngine must be a string', 'INVALID_ARGUMENT');
    }
    const androidEngine = source.androidEngine.trim().toLowerCase();
    if (!VALID_ANDROID_ENGINES_SET.has(androidEngine)) {
      throw createError('Invalid androidEngine: ' + source.androidEngine, 'INVALID_ENGINE', {
        engines: VALID_ANDROID_ENGINES
      });
    }
    normalized.androidEngine = androidEngine;
  }

  return normalized;
}

function normalizePermissionInfo(payload) {
  const result = payload || {};
  return {
    granted: Boolean(result.granted),
    status: typeof result.status === 'string' ? result.status : 'unknown',
    canRequest: Boolean(result.canRequest),
    requiresPermission: Boolean(result.requiresPermission),
    platform: result.platform || null,
    engine: result.engine || null
  };
}

function normalizeSupportInfo(payload) {
  const result = payload || {};
  return {
    supported: Boolean(result.supported),
    available: Boolean(result.available),
    platform: result.platform || null,
    defaultEngine: result.defaultEngine || null,
    availableEngines: Array.isArray(result.availableEngines) ? result.availableEngines.slice() : [],
    supportedFormats: Array.isArray(result.supportedFormats) ? result.supportedFormats.slice() : [],
    unsupportedFormats: Array.isArray(result.unsupportedFormats) ? result.unsupportedFormats.slice() : [],
    requiresPermission: Boolean(result.requiresPermission),
    permissionOptional: Boolean(result.permissionOptional),
    details: result.details || null
  };
}

function normalizeScanResult(payload) {
  const result = payload || {};
  return {
    cancelled: Boolean(result.cancelled),
    text: typeof result.text === 'string' ? result.text : null,
    format: result.format || null,
    nativeFormat: result.nativeFormat || null,
    engine: result.engine || null,
    platform: result.platform || null,
    rawBytesBase64: typeof result.rawBytesBase64 === 'string' ? result.rawBytesBase64 : null,
    valueType: result.valueType || 'UNKNOWN',
    bounds: result.bounds || null,
    cornerPoints: Array.isArray(result.cornerPoints) ? result.cornerPoints : [],
    timestamp: typeof result.timestamp === 'number' ? result.timestamp : Date.now()
  };
}

function makeExecPromise(exec, action, args, normalizer) {
  return new Promise(function executor(resolve, reject) {
    exec(
      function onSuccess(result) {
        resolve(typeof normalizer === 'function' ? normalizer(result) : result);
      },
      function onError(rawError) {
        const error = normalizeExecError(rawError);
        if (action === 'scan' && SCAN_CANCEL_CODES.has(error.code)) {
          resolve(
            normalizeScanResult({
              cancelled: true
            })
          );
          return;
        }
        reject(error);
      },
      SERVICE_NAME,
      action,
      args || []
    );
  });
}

function createApi(exec) {
  const api = {
    SUPPORTED_FORMATS: SUPPORTED_FORMATS.slice(),

    isSupported: function isSupported(success, error) {
      return toCallbackPromise(
        makeExecPromise(exec, 'isSupported', [], normalizeSupportInfo),
        success,
        error
      );
    },

    checkPermission: function checkPermission(success, error) {
      return toCallbackPromise(
        makeExecPromise(exec, 'checkPermission', [], normalizePermissionInfo),
        success,
        error
      );
    },

    requestPermission: function requestPermission(success, error) {
      return toCallbackPromise(
        makeExecPromise(exec, 'requestPermission', [], normalizePermissionInfo),
        success,
        error
      );
    },

    prewarm: function prewarm(success, error) {
      return toCallbackPromise(makeExecPromise(exec, 'prewarm', [], function noop() {}), success, error);
    },

    cancel: function cancel(success, error) {
      return toCallbackPromise(makeExecPromise(exec, 'cancel', [], function noop() {}), success, error);
    },

    scan: function scan(options, success, error) {
      let normalizedOptions = options;
      let normalizedSuccess = success;
      let normalizedError = error;

      if (typeof options === 'function') {
        normalizedSuccess = options;
        normalizedError = success;
        normalizedOptions = {};
      }

      try {
        normalizedOptions = normalizeScanOptions(normalizedOptions || {});
      } catch (validationError) {
        return toCallbackPromise(Promise.reject(validationError), normalizedSuccess, normalizedError);
      }

      return toCallbackPromise(
        makeExecPromise(exec, 'scan', [normalizedOptions], normalizeScanResult),
        normalizedSuccess,
        normalizedError
      );
    }
  };

  return api;
}

module.exports = createApi;
module.exports.SUPPORTED_FORMATS = SUPPORTED_FORMATS.slice();
module.exports.DEFAULT_OPTIONS = Object.assign({}, DEFAULT_OPTIONS);
module.exports.normalizeScanOptions = normalizeScanOptions;
module.exports.normalizeExecError = normalizeExecError;
