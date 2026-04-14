package com.acme.cordova.nativecodescanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class NativeCodeScannerContract {
    static final String EXTRA_OPTIONS_JSON = "nativeCodeScanner.optionsJson";
    static final String EXTRA_RESULT_JSON = "nativeCodeScanner.resultJson";
    static final String EXTRA_ERROR_JSON = "nativeCodeScanner.errorJson";

    static final int REQUEST_CODE_SCAN_ACTIVITY = 61021;
    static final int REQUEST_CODE_CAMERA_PERMISSION = 61022;

    static final String ENGINE_AUTO = "auto";
    static final String ENGINE_GOOGLE = "google";
    static final String ENGINE_MLKIT = "mlkit";

    static final String PERMISSION_STATUS_GRANTED = "granted";
    static final String PERMISSION_STATUS_DENIED = "denied";
    static final String PERMISSION_STATUS_NOT_DETERMINED = "not_determined";
    static final String PERMISSION_STATUS_UNKNOWN = "unknown";

    static final String PREFS_NAME = "native_code_scanner";
    static final String PREF_CAMERA_PERMISSION_REQUESTED = "camera_permission_requested";

    private NativeCodeScannerContract() {
    }

    static JSONObject buildError(String code, String message) {
        return buildError(code, message, null);
    }

    static JSONObject buildError(String code, String message, JSONObject details) {
        try {
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", message);
            if (details != null) {
                error.put("details", details);
            }
            return error;
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to build error payload", exception);
        }
    }

    static JSONObject buildPermissionInfo(
            boolean granted,
            String status,
            boolean canRequest,
            boolean requiresPermission,
            String engine
    ) {
        try {
            JSONObject info = new JSONObject();
            info.put("granted", granted);
            info.put("status", status);
            info.put("canRequest", canRequest);
            info.put("requiresPermission", requiresPermission);
            info.put("platform", "android");
            info.put("engine", engine);
            return info;
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to build permission payload", exception);
        }
    }

    static JSONObject buildSupportInfo(
            boolean cameraAvailable,
            boolean googlePlayServicesAvailable,
            boolean googleModuleInstalled
    ) {
        try {
            JSONObject info = new JSONObject();
            JSONArray engines = new JSONArray();
            if (googlePlayServicesAvailable && cameraAvailable) {
                engines.put(ENGINE_GOOGLE);
            }
            if (cameraAvailable) {
                engines.put(ENGINE_MLKIT);
            }

            JSONObject details = new JSONObject();
            details.put("cameraAvailable", cameraAvailable);
            details.put("googlePlayServicesAvailable", googlePlayServicesAvailable);
            details.put("googleModuleInstalled", googleModuleInstalled);

            info.put("supported", cameraAvailable);
            info.put("available", cameraAvailable);
            info.put("platform", "android");
            info.put("defaultEngine", googlePlayServicesAvailable && cameraAvailable ? ENGINE_GOOGLE : (cameraAvailable ? ENGINE_MLKIT : JSONObject.NULL));
            info.put("availableEngines", engines);
            info.put("supportedFormats", NativeCodeScannerFormats.toJsonArray(NativeCodeScannerFormats.getCanonicalFormats()));
            info.put("unsupportedFormats", new JSONArray());
            info.put("requiresPermission", !(googlePlayServicesAvailable && cameraAvailable));
            info.put("permissionOptional", googlePlayServicesAvailable && cameraAvailable);
            info.put("details", details);
            return info;
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to build support payload", exception);
        }
    }

    static JSONObject buildCancelledResult(String engine) {
        try {
            JSONObject result = new JSONObject();
            result.put("cancelled", true);
            result.put("text", JSONObject.NULL);
            result.put("format", JSONObject.NULL);
            result.put("nativeFormat", JSONObject.NULL);
            result.put("engine", engine != null ? engine : JSONObject.NULL);
            result.put("platform", "android");
            result.put("rawBytesBase64", JSONObject.NULL);
            result.put("valueType", "UNKNOWN");
            result.put("bounds", JSONObject.NULL);
            result.put("cornerPoints", new JSONArray());
            result.put("timestamp", System.currentTimeMillis());
            return result;
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to build cancelled payload", exception);
        }
    }

    static ScanOptions parseOptions(JSONObject json) throws JSONException {
        JSONObject source = json == null ? new JSONObject() : json;
        JSONArray formatsArray = source.optJSONArray("formats");
        Set<String> formats = new LinkedHashSet<>();

        if (formatsArray != null) {
            for (int index = 0; index < formatsArray.length(); index += 1) {
                formats.add(formatsArray.getString(index));
            }
        }

        return new ScanOptions(
                new ArrayList<>(formats),
                source.optString("prompt", ""),
                source.optBoolean("preferFrontCamera", false),
                source.optBoolean("showTorchButton", false),
                source.optBoolean("showFlipCameraButton", false),
                source.optBoolean("beepOnSuccess", true),
                source.optBoolean("vibrateOnSuccess", false),
                source.optInt("timeoutMs", 0),
                source.optString("androidEngine", ENGINE_AUTO),
                source.optBoolean("returnRawBytes", false)
        );
    }

    static final class ScanOptions {
        final List<String> formats;
        final String prompt;
        final boolean preferFrontCamera;
        final boolean showTorchButton;
        final boolean showFlipCameraButton;
        final boolean beepOnSuccess;
        final boolean vibrateOnSuccess;
        final int timeoutMs;
        final String androidEngine;
        final boolean returnRawBytes;

        ScanOptions(
                List<String> formats,
                String prompt,
                boolean preferFrontCamera,
                boolean showTorchButton,
                boolean showFlipCameraButton,
                boolean beepOnSuccess,
                boolean vibrateOnSuccess,
                int timeoutMs,
                String androidEngine,
                boolean returnRawBytes
        ) {
            this.formats = formats;
            this.prompt = prompt;
            this.preferFrontCamera = preferFrontCamera;
            this.showTorchButton = showTorchButton;
            this.showFlipCameraButton = showFlipCameraButton;
            this.beepOnSuccess = beepOnSuccess;
            this.vibrateOnSuccess = vibrateOnSuccess;
            this.timeoutMs = timeoutMs;
            this.androidEngine = androidEngine;
            this.returnRawBytes = returnRawBytes;
        }

        JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                JSONArray formatsArray = new JSONArray();
                for (String format : formats) {
                    formatsArray.put(format);
                }
                json.put("formats", formatsArray);
                json.put("prompt", prompt);
                json.put("preferFrontCamera", preferFrontCamera);
                json.put("showTorchButton", showTorchButton);
                json.put("showFlipCameraButton", showFlipCameraButton);
                json.put("beepOnSuccess", beepOnSuccess);
                json.put("vibrateOnSuccess", vibrateOnSuccess);
                json.put("timeoutMs", timeoutMs);
                json.put("androidEngine", androidEngine);
                json.put("returnRawBytes", returnRawBytes);
                return json;
            } catch (JSONException exception) {
                throw new IllegalStateException("Unable to serialize scan options", exception);
            }
        }

        boolean requiresCustomUiForAuto() {
            return preferFrontCamera
                    || showTorchButton
                    || showFlipCameraButton
                    || timeoutMs > 0
                    || (prompt != null && prompt.trim().length() > 0)
                    || vibrateOnSuccess
                    || !beepOnSuccess;
        }
    }
}
