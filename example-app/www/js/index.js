'use strict';

function writeOutput(payload) {
  const output = document.getElementById('output');
  output.textContent = JSON.stringify(payload, null, 2);
}

function writeError(error) {
  writeOutput({
    ok: false,
    code: error && error.code ? error.code : 'UNKNOWN',
    message: error && error.message ? error.message : 'Unexpected error'
  });
}

async function runAction(action) {
  try {
    await action();
  } catch (error) {
    writeError(error);
  }
}

document.addEventListener('deviceready', function onDeviceReady() {
  const scanner = window.NativeCodeScanner;
  if (!scanner) {
    writeOutput({
      ready: true,
      hasScanner: false,
      message: 'NativeCodeScanner is not available in this build'
    });
    return;
  }

  writeOutput({
    ready: true,
    hasScanner: true,
    message: 'NativeCodeScanner is ready'
  });

  document.getElementById('supportButton').addEventListener('click', async function () {
    await runAction(async function () {
      writeOutput(await scanner.isSupported());
    });
  });

  document.getElementById('permissionButton').addEventListener('click', async function () {
    await runAction(async function () {
      writeOutput(await scanner.requestPermission());
    });
  });

  document.getElementById('prewarmButton').addEventListener('click', async function () {
    await runAction(async function () {
      await scanner.prewarm();
      writeOutput({
        ok: true,
        prewarmed: true,
        message: 'Scanner warm-up finished'
      });
    });
  });

  document.getElementById('scanButton').addEventListener('click', async function () {
    await runAction(async function () {
      const result = await scanner.scan({
        formats: ['QR_CODE', 'EAN_13', 'CODE_128'],
        prompt: 'Point the camera at a code',
        preferFrontCamera: false,
        showTorchButton: true,
        showFlipCameraButton: false,
        beepOnSuccess: true,
        vibrateOnSuccess: false,
        timeoutMs: 0,
        androidEngine: 'auto',
        returnRawBytes: false
      });

      writeOutput(result);
    });
  });

  document.getElementById('cancelButton').addEventListener('click', async function () {
    await runAction(async function () {
      await scanner.cancel();
      writeOutput({
        ok: true,
        cancelled: true,
        message: 'Cancel request sent'
      });
    });
  });
});
