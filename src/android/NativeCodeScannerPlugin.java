package com.acme.cordova.nativecodescanner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.OptionalModuleApi;
import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallClient;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public final class NativeCodeScannerPlugin extends CordovaPlugin {
    private static final String ACTION_IS_SUPPORTED = "isSupported";
    private static final String ACTION_CHECK_PERMISSION = "checkPermission";
    private static final String ACTION_REQUEST_PERMISSION = "requestPermission";
    private static final String ACTION_SCAN = "scan";
    private static final String ACTION_CANCEL = "cancel";
    private static final String ACTION_PREWARM = "prewarm";

    private CallbackContext pendingPermissionCallback;
    private CallbackContext pendingScanCallback;
    private NativeCodeScannerContract.ScanOptions pendingScanOptions;
    private String pendingPermissionMode;
    private String activeEngine;
    private final List<CallbackContext> pendingPrewarmCallbacks = new ArrayList<>();
    private Boolean cachedCameraAvailable;
    private Boolean cachedGooglePlayServicesAvailable;
    private Boolean cachedGoogleModuleInstalled;
    private boolean googlePrewarmInFlight;
    private boolean mlKitPrewarmed;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case ACTION_IS_SUPPORTED:
                isSupported(callbackContext);
                return true;
            case ACTION_CHECK_PERMISSION:
                callbackContext.success(buildPermissionInfo());
                return true;
            case ACTION_REQUEST_PERMISSION:
                requestPermission(callbackContext);
                return true;
            case ACTION_SCAN:
                JSONObject optionsJson = args != null && args.length() > 0 ? args.getJSONObject(0) : new JSONObject();
                scan(NativeCodeScannerContract.parseOptions(optionsJson), callbackContext);
                return true;
            case ACTION_CANCEL:
                cancel(callbackContext);
                return true;
            case ACTION_PREWARM:
                prewarm(callbackContext);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode != NativeCodeScannerContract.REQUEST_CODE_SCAN_ACTIVITY || pendingScanCallback == null) {
            return;
        }

        CallbackContext callbackContext = pendingScanCallback;
        String engine = activeEngine;
        clearPendingScan();

        if (intent != null && intent.hasExtra(NativeCodeScannerContract.EXTRA_RESULT_JSON)) {
            callbackContext.success(parseJson(intent.getStringExtra(NativeCodeScannerContract.EXTRA_RESULT_JSON)));
            return;
        }

        if (intent != null && intent.hasExtra(NativeCodeScannerContract.EXTRA_ERROR_JSON)) {
            callbackContext.error(parseJson(intent.getStringExtra(NativeCodeScannerContract.EXTRA_ERROR_JSON)));
            return;
        }

        callbackContext.success(NativeCodeScannerContract.buildCancelledResult(engine));
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode != NativeCodeScannerContract.REQUEST_CODE_CAMERA_PERMISSION) {
            return;
        }

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (ACTION_SCAN.equals(pendingPermissionMode) && pendingScanCallback != null) {
            markCameraPermissionRequested();
            if (granted) {
                NativeCodeScannerContract.ScanOptions options = pendingScanOptions;
                CallbackContext callbackContext = pendingScanCallback;
                pendingPermissionMode = null;
                pendingScanOptions = null;
                startScan(options, callbackContext);
            } else {
                CallbackContext callbackContext = pendingScanCallback;
                clearPendingScan();
                pendingPermissionMode = null;
                callbackContext.error(NativeCodeScannerContract.buildError("PERMISSION_DENIED", "Camera access was denied"));
            }
            return;
        }

        if (ACTION_REQUEST_PERMISSION.equals(pendingPermissionMode) && pendingPermissionCallback != null) {
            markCameraPermissionRequested();
            CallbackContext callbackContext = pendingPermissionCallback;
            pendingPermissionCallback = null;
            pendingPermissionMode = null;
            callbackContext.success(buildPermissionInfo());
        }
    }

    @Override
    public void onReset() {
        super.onReset();
        NativeCodeScannerActivity.cancelActiveScan();
        if (pendingScanCallback != null) {
            pendingScanCallback.success(NativeCodeScannerContract.buildCancelledResult(activeEngine));
            clearPendingScan();
        }
    }

    private void isSupported(CallbackContext callbackContext) {
        boolean cameraAvailable = hasCamera();
        boolean googlePlayServicesAvailable = isGooglePlayServicesAvailable();

        if (!cameraAvailable || !googlePlayServicesAvailable) {
            callbackContext.success(NativeCodeScannerContract.buildSupportInfo(cameraAvailable, googlePlayServicesAvailable, false));
            return;
        }

        if (cachedGoogleModuleInstalled != null) {
            callbackContext.success(NativeCodeScannerContract.buildSupportInfo(cameraAvailable, true, cachedGoogleModuleInstalled));
            return;
        }

        Activity activity = cordova.getActivity();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(activity);
        ModuleInstallClient moduleInstallClient = ModuleInstall.getClient(activity);
        moduleInstallClient.areModulesAvailable(new OptionalModuleApi[]{scanner})
                .addOnSuccessListener(response -> {
                    cachedGoogleModuleInstalled = response.areModulesAvailable();
                    callbackContext.success(
                            NativeCodeScannerContract.buildSupportInfo(cameraAvailable, true, cachedGoogleModuleInstalled)
                    );
                })
                .addOnFailureListener(exception -> {
                    cachedGoogleModuleInstalled = false;
                    callbackContext.success(
                            NativeCodeScannerContract.buildSupportInfo(cameraAvailable, true, false)
                    );
                });
    }

    private void requestPermission(CallbackContext callbackContext) {
        if (googleCompatibleAutoPermissionInfo().optBoolean("requiresPermission", false) == false) {
            callbackContext.success(buildPermissionInfo());
            return;
        }

        if (hasCameraPermission()) {
            callbackContext.success(buildPermissionInfo());
            return;
        }

        pendingPermissionCallback = callbackContext;
        pendingPermissionMode = ACTION_REQUEST_PERMISSION;
        markCameraPermissionRequested();
        cordova.requestPermission(this, NativeCodeScannerContract.REQUEST_CODE_CAMERA_PERMISSION, Manifest.permission.CAMERA);
    }

    private void scan(NativeCodeScannerContract.ScanOptions options, CallbackContext callbackContext) {
        if (pendingScanCallback != null) {
            callbackContext.error(NativeCodeScannerContract.buildError("SCAN_IN_PROGRESS", "A scan is already in progress"));
            return;
        }

        pendingScanCallback = callbackContext;
        pendingScanOptions = options;
        startScan(options, callbackContext);
    }

    private void startScan(NativeCodeScannerContract.ScanOptions options, CallbackContext callbackContext) {
        EngineResolution engineResolution = resolveEngine(options);
        if (engineResolution.error != null) {
            clearPendingScan();
            callbackContext.error(engineResolution.error);
            return;
        }

        activeEngine = engineResolution.engine;
        if (NativeCodeScannerContract.ENGINE_MLKIT.equals(engineResolution.engine) && !hasCameraPermission()) {
            pendingPermissionMode = ACTION_SCAN;
            markCameraPermissionRequested();
            cordova.requestPermission(this, NativeCodeScannerContract.REQUEST_CODE_CAMERA_PERMISSION, Manifest.permission.CAMERA);
            return;
        }

        if (NativeCodeScannerContract.ENGINE_GOOGLE.equals(engineResolution.engine)) {
            startGoogleScan(options, callbackContext);
            return;
        }

        startMlKitScan(options);
    }

    private void startGoogleScan(NativeCodeScannerContract.ScanOptions options, CallbackContext callbackContext) {
        Activity activity = cordova.getActivity();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(activity, buildGoogleScannerOptions(options));
        scanner.startScan()
                .addOnSuccessListener(barcode -> {
                    try {
                        callbackContext.success(NativeCodeScannerFormats.barcodeToResultJson(
                                barcode,
                                NativeCodeScannerContract.ENGINE_GOOGLE,
                                options.formats,
                                options.returnRawBytes
                        ));
                    } catch (JSONException exception) {
                        callbackContext.error(NativeCodeScannerContract.buildError("RESULT_ENCODING_FAILED", "Could not encode the scan result"));
                    } finally {
                        clearPendingScan();
                    }
                })
                .addOnCanceledListener(() -> {
                    callbackContext.success(NativeCodeScannerContract.buildCancelledResult(NativeCodeScannerContract.ENGINE_GOOGLE));
                    clearPendingScan();
                })
                .addOnFailureListener(exception -> {
                    if (isCancellationException(exception)) {
                        callbackContext.success(NativeCodeScannerContract.buildCancelledResult(NativeCodeScannerContract.ENGINE_GOOGLE));
                    } else {
                        callbackContext.error(NativeCodeScannerContract.buildError("SCAN_FAILED", exception.getMessage() != null ? exception.getMessage() : "Google Code Scanner could not complete the scan"));
                    }
                    clearPendingScan();
                });
    }

    private void startMlKitScan(NativeCodeScannerContract.ScanOptions options) {
        Activity activity = cordova.getActivity();
        cordova.setActivityResultCallback(this);

        Intent intent = new Intent(activity, NativeCodeScannerActivity.class);
        intent.putExtra(NativeCodeScannerContract.EXTRA_OPTIONS_JSON, options.toJson().toString());
        activity.startActivityForResult(intent, NativeCodeScannerContract.REQUEST_CODE_SCAN_ACTIVITY);
    }

    private void cancel(CallbackContext callbackContext) {
        if (NativeCodeScannerContract.ENGINE_MLKIT.equals(activeEngine)) {
            NativeCodeScannerActivity.cancelActiveScan();
        }
        callbackContext.success();
    }

    private void prewarm(CallbackContext callbackContext) {
        Activity activity = cordova.getActivity();
        if (isGooglePlayServicesAvailable() && hasCamera()) {
            if (Boolean.TRUE.equals(cachedGoogleModuleInstalled)) {
                callbackContext.success();
                return;
            }

            pendingPrewarmCallbacks.add(callbackContext);
            if (googlePrewarmInFlight) {
                return;
            }

            googlePrewarmInFlight = true;
            GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(activity);
            ModuleInstall.getClient(activity)
                    .deferredInstall(new OptionalModuleApi[]{scanner})
                    .addOnCompleteListener(task -> {
                        cachedGoogleModuleInstalled = task.isSuccessful();
                        finishGooglePrewarm();
                    });
            return;
        }

        if (mlKitPrewarmed) {
            callbackContext.success();
            return;
        }

        BarcodeScannerOptions.Builder builder = new BarcodeScannerOptions.Builder();
        BarcodeScanning.getClient(builder.build()).close();
        mlKitPrewarmed = true;
        callbackContext.success();
    }

    private EngineResolution resolveEngine(NativeCodeScannerContract.ScanOptions options) {
        boolean googleAvailable = isGooglePlayServicesAvailable() && hasCamera();
        boolean cameraAvailable = hasCamera();

        if (!cameraAvailable) {
            return new EngineResolution(null, NativeCodeScannerContract.buildError("UNSUPPORTED", "This device does not have a usable camera"));
        }

        switch (options.androidEngine) {
            case NativeCodeScannerContract.ENGINE_GOOGLE:
                if (!googleAvailable) {
                    return new EngineResolution(null, NativeCodeScannerContract.buildError("GOOGLE_SCANNER_UNAVAILABLE", "Google Code Scanner is not available on this device"));
                }
                if (options.requiresCustomUiForAuto()) {
                    return new EngineResolution(null, NativeCodeScannerContract.buildError("INCOMPATIBLE_OPTIONS", "The requested options require the ML Kit engine"));
                }
                return new EngineResolution(NativeCodeScannerContract.ENGINE_GOOGLE, null);
            case NativeCodeScannerContract.ENGINE_MLKIT:
                return new EngineResolution(NativeCodeScannerContract.ENGINE_MLKIT, null);
            case NativeCodeScannerContract.ENGINE_AUTO:
            default:
                if (googleAvailable && !options.requiresCustomUiForAuto()) {
                    return new EngineResolution(NativeCodeScannerContract.ENGINE_GOOGLE, null);
                }
                return new EngineResolution(NativeCodeScannerContract.ENGINE_MLKIT, null);
        }
    }

    private GmsBarcodeScannerOptions buildGoogleScannerOptions(NativeCodeScannerContract.ScanOptions options) {
        GmsBarcodeScannerOptions.Builder builder = new GmsBarcodeScannerOptions.Builder();
        int[] requestedFormats = NativeCodeScannerFormats.toBarcodeFormats(options.formats);
        if (requestedFormats.length > 0) {
            builder.setBarcodeFormats(requestedFormats[0], Arrays.copyOfRange(requestedFormats, 1, requestedFormats.length));
        }
        builder.enableAutoZoom();
        return builder.build();
    }

    private JSONObject buildPermissionInfo() {
        if (!hasCamera()) {
            return NativeCodeScannerContract.buildPermissionInfo(
                    false,
                    NativeCodeScannerContract.PERMISSION_STATUS_UNKNOWN,
                    false,
                    false,
                    null
            );
        }

        boolean googleAvailable = isGooglePlayServicesAvailable();
        boolean cameraPermissionGranted = hasCameraPermission();
        boolean granted = googleAvailable || cameraPermissionGranted;
        boolean requiresPermission = !googleAvailable;
        boolean canRequest = !googleAvailable && canRequestCameraPermission();
        String status;

        if (cameraPermissionGranted) {
            status = NativeCodeScannerContract.PERMISSION_STATUS_GRANTED;
        } else if (wasCameraPermissionRequested()) {
            status = NativeCodeScannerContract.PERMISSION_STATUS_DENIED;
        } else {
            status = NativeCodeScannerContract.PERMISSION_STATUS_NOT_DETERMINED;
        }

        return NativeCodeScannerContract.buildPermissionInfo(
                granted,
                status,
                canRequest,
                requiresPermission,
                googleAvailable ? NativeCodeScannerContract.ENGINE_GOOGLE : NativeCodeScannerContract.ENGINE_MLKIT
        );
    }

    private JSONObject googleCompatibleAutoPermissionInfo() {
        if (isGooglePlayServicesAvailable() && hasCamera()) {
            boolean cameraPermissionGranted = hasCameraPermission();
            return NativeCodeScannerContract.buildPermissionInfo(
                    true,
                    cameraPermissionGranted ? NativeCodeScannerContract.PERMISSION_STATUS_GRANTED : NativeCodeScannerContract.PERMISSION_STATUS_NOT_DETERMINED,
                    false,
                    false,
                    NativeCodeScannerContract.ENGINE_GOOGLE
            );
        }
        return buildPermissionInfo();
    }

    private boolean isGooglePlayServicesAvailable() {
        if (cachedGooglePlayServicesAvailable != null) {
            return cachedGooglePlayServicesAvailable;
        }

        Activity activity = cordova.getActivity();
        cachedGooglePlayServicesAvailable =
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS;
        return cachedGooglePlayServicesAvailable;
    }

    private boolean hasCamera() {
        if (cachedCameraAvailable != null) {
            return cachedCameraAvailable;
        }

        cachedCameraAvailable =
                cordova.getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        return cachedCameraAvailable;
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || cordova.hasPermission(Manifest.permission.CAMERA);
    }

    private boolean wasCameraPermissionRequested() {
        SharedPreferences preferences = cordova.getActivity().getSharedPreferences(
                NativeCodeScannerContract.PREFS_NAME,
                Activity.MODE_PRIVATE
        );
        return preferences.getBoolean(NativeCodeScannerContract.PREF_CAMERA_PERMISSION_REQUESTED, false);
    }

    private void markCameraPermissionRequested() {
        cordova.getActivity().getSharedPreferences(NativeCodeScannerContract.PREFS_NAME, Activity.MODE_PRIVATE)
                .edit()
                .putBoolean(NativeCodeScannerContract.PREF_CAMERA_PERMISSION_REQUESTED, true)
                .apply();
    }

    private boolean canRequestCameraPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || hasCameraPermission()) {
            return false;
        }

        Activity activity = cordova.getActivity();
        return !wasCameraPermissionRequested()
                || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA);
    }

    private boolean isCancellationException(@NonNull Exception exception) {
        if (exception instanceof ApiException) {
            int statusCode = ((ApiException) exception).getStatusCode();
            if (statusCode == CommonStatusCodes.CANCELED) {
                return true;
            }
        }

        String message = exception.getMessage();
        return !TextUtils.isEmpty(message) && message.toLowerCase(Locale.ROOT).contains("cancel");
    }

    private JSONObject parseJson(String rawJson) {
        try {
            return new JSONObject(rawJson);
        } catch (JSONException exception) {
            return NativeCodeScannerContract.buildError("INVALID_PAYLOAD", "Could not parse the native result payload");
        }
    }

    private void clearPendingScan() {
        pendingScanCallback = null;
        pendingScanOptions = null;
        pendingPermissionMode = null;
        activeEngine = null;
    }

    private void finishGooglePrewarm() {
        googlePrewarmInFlight = false;
        for (CallbackContext callbackContext : pendingPrewarmCallbacks) {
            callbackContext.success();
        }
        pendingPrewarmCallbacks.clear();
    }

    private static final class EngineResolution {
        final String engine;
        final JSONObject error;

        EngineResolution(String engine, JSONObject error) {
            this.engine = engine;
            this.error = error;
        }
    }
}
