package com.acme.cordova.nativecodescanner;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class NativeCodeScannerActivity extends AppCompatActivity {
    private static final WeakReferenceHolder ACTIVE_INSTANCE = new WeakReferenceHolder();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean frameInFlight = new AtomicBoolean(false);

    private PreviewView previewView;
    private NativeCodeScannerOverlayView overlayView;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private BarcodeScanner barcodeScanner;
    private Camera camera;

    private NativeCodeScannerContract.ScanOptions scanOptions;
    private Runnable timeoutRunnable;
    private boolean finishingResult;
    private boolean torchEnabled;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;

    static void cancelActiveScan() {
        NativeCodeScannerActivity activity = ACTIVE_INSTANCE.get();
        if (activity != null) {
            activity.runOnUiThread(activity::finishCancelled);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ACTIVE_INSTANCE.set(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        try {
            scanOptions = NativeCodeScannerContract.parseOptions(new JSONObject(getIntent().getStringExtra(NativeCodeScannerContract.EXTRA_OPTIONS_JSON)));
        } catch (Exception exception) {
            finishWithError("INVALID_OPTIONS", "Could not open the scanner");
            return;
        }

        lensFacing = scanOptions.preferFrontCamera ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        cameraExecutor = Executors.newSingleThreadExecutor();
        barcodeScanner = BarcodeScanning.getClient(buildScannerOptions(scanOptions));

        FrameLayout root = new FrameLayout(this);
        previewView = new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(previewView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        overlayView = new NativeCodeScannerOverlayView(this);
        overlayView.configure(scanOptions.prompt, scanOptions.showTorchButton, scanOptions.showFlipCameraButton);
        overlayView.setOnCancelClickListener(view -> finishCancelled());
        overlayView.setOnTorchClickListener(view -> toggleTorch());
        overlayView.setOnFlipClickListener(view -> flipCamera());
        root.addView(overlayView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));

        setContentView(root);
        startCamera();
        scheduleTimeout();
    }

    @Override
    public void onBackPressed() {
        finishCancelled();
    }

    @Override
    protected void onDestroy() {
        ACTIVE_INSTANCE.clear(this);
        clearTimeout();

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        if (barcodeScanner != null) {
            barcodeScanner.close();
            barcodeScanner = null;
        }

        if (cameraExecutor != null) {
            cameraExecutor.shutdownNow();
            cameraExecutor = null;
        }

        super.onDestroy();
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindUseCases();
            } catch (Exception exception) {
                finishWithError("CAMERA_UNAVAILABLE", "Could not start the camera");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases() {
        if (cameraProvider == null) {
            finishWithError("CAMERA_UNAVAILABLE", "Camera provider not ready");
            return;
        }

        cameraProvider.unbindAll();

        CameraSelector preferredSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        if (!hasCamera(preferredSelector)) {
            lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                    ? CameraSelector.LENS_FACING_BACK
                    : CameraSelector.LENS_FACING_FRONT;
            preferredSelector = new CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build();
        }

        if (!hasCamera(preferredSelector)) {
            finishWithError("CAMERA_UNAVAILABLE", "No compatible camera was found");
            return;
        }

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        camera = cameraProvider.bindToLifecycle(this, preferredSelector, preview, analysis);
        overlayView.setTorchAvailable(camera.getCameraInfo().hasFlashUnit());
        overlayView.setFlipAvailable(hasCamera(new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build())
                && hasCamera(new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()));
    }

    private boolean hasCamera(CameraSelector selector) {
        try {
            return cameraProvider != null && cameraProvider.hasCamera(selector);
        } catch (Exception exception) {
            return false;
        }
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (finishingResult || !frameInFlight.compareAndSet(false, true)) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            frameInFlight.set(false);
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        barcodeScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> onBarcodesDetected(barcodes))
                .addOnCompleteListener(task -> {
                    frameInFlight.set(false);
                    imageProxy.close();
                });
    }

    private void onBarcodesDetected(List<Barcode> barcodes) {
        if (finishingResult || barcodes == null || barcodes.isEmpty()) {
            return;
        }

        for (Barcode barcode : barcodes) {
            if (barcode.getRawValue() != null && barcode.getRawValue().length() > 0) {
                finishWithSuccess(barcode);
                return;
            }
        }
    }

    private void finishWithSuccess(Barcode barcode) {
        if (finishingResult) {
            return;
        }

        finishingResult = true;
        clearTimeout();
        playSuccessFeedback();

        try {
            JSONObject payload = NativeCodeScannerFormats.barcodeToResultJson(
                    barcode,
                    NativeCodeScannerContract.ENGINE_MLKIT,
                    scanOptions.formats,
                    scanOptions.returnRawBytes
            );
            Intent intent = new Intent();
            intent.putExtra(NativeCodeScannerContract.EXTRA_RESULT_JSON, payload.toString());
            setResult(RESULT_OK, intent);
        } catch (JSONException exception) {
            Intent intent = new Intent();
            intent.putExtra(
                    NativeCodeScannerContract.EXTRA_ERROR_JSON,
                    NativeCodeScannerContract.buildError("RESULT_ENCODING_FAILED", "Could not encode the scanner result").toString()
            );
            setResult(RESULT_CANCELED, intent);
        }

        finish();
    }

    private void finishCancelled() {
        if (finishingResult) {
            return;
        }

        finishingResult = true;
        clearTimeout();

        Intent intent = new Intent();
        intent.putExtra(
                NativeCodeScannerContract.EXTRA_RESULT_JSON,
                NativeCodeScannerContract.buildCancelledResult(NativeCodeScannerContract.ENGINE_MLKIT).toString()
        );
        setResult(RESULT_OK, intent);
        finish();
    }

    private void finishWithError(String code, String message) {
        if (finishingResult) {
            return;
        }

        finishingResult = true;
        clearTimeout();

        Intent intent = new Intent();
        intent.putExtra(
                NativeCodeScannerContract.EXTRA_ERROR_JSON,
                NativeCodeScannerContract.buildError(code, message).toString()
        );
        setResult(RESULT_CANCELED, intent);
        finish();
    }

    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) {
            return;
        }

        torchEnabled = !torchEnabled;
        camera.getCameraControl().enableTorch(torchEnabled);
        overlayView.setTorchEnabled(torchEnabled);
    }

    private void flipCamera() {
        if (cameraProvider == null) {
            return;
        }

        lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;
        torchEnabled = false;
        overlayView.setTorchEnabled(false);
        bindUseCases();
    }

    private void scheduleTimeout() {
        clearTimeout();

        if (scanOptions.timeoutMs <= 0) {
            return;
        }

        timeoutRunnable = this::finishCancelled;
        mainHandler.postDelayed(timeoutRunnable, scanOptions.timeoutMs);
    }

    private void clearTimeout() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void playSuccessFeedback() {
        if (scanOptions.beepOnSuccess) {
            ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            toneGenerator.release();
        }

        if (scanOptions.vibrateOnSuccess) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = getSystemService(VibratorManager.class);
                if (vibratorManager != null) {
                    vibratorManager.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(80);
                    }
                }
            }
        }
    }

    private BarcodeScannerOptions buildScannerOptions(NativeCodeScannerContract.ScanOptions options) {
        BarcodeScannerOptions.Builder builder = new BarcodeScannerOptions.Builder();
        int[] requestedFormats = NativeCodeScannerFormats.toBarcodeFormats(options.formats);
        if (requestedFormats.length > 0) {
            builder.setBarcodeFormats(requestedFormats[0], Arrays.copyOfRange(requestedFormats, 1, requestedFormats.length));
        }
        return builder.build();
    }

    private static final class WeakReferenceHolder {
        private WeakReference<NativeCodeScannerActivity> reference = new WeakReference<>(null);

        void set(NativeCodeScannerActivity activity) {
            reference = new WeakReference<>(activity);
        }

        NativeCodeScannerActivity get() {
            return reference.get();
        }

        void clear(NativeCodeScannerActivity candidate) {
            NativeCodeScannerActivity current = reference.get();
            if (current == candidate) {
                reference.clear();
            }
        }
    }
}
