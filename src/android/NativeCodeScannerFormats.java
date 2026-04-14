package com.acme.cordova.nativecodescanner;

import android.graphics.Point;
import android.graphics.Rect;
import android.util.Base64;

import com.google.mlkit.vision.barcode.common.Barcode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class NativeCodeScannerFormats {
    private static final List<String> CANONICAL_FORMATS = Collections.unmodifiableList(Arrays.asList(
            "AZTEC",
            "CODABAR",
            "CODE_39",
            "CODE_93",
            "CODE_128",
            "DATA_MATRIX",
            "EAN_8",
            "EAN_13",
            "ITF",
            "PDF_417",
            "QR_CODE",
            "UPC_A",
            "UPC_E"
    ));

    private NativeCodeScannerFormats() {
    }

    static List<String> getCanonicalFormats() {
        return CANONICAL_FORMATS;
    }

    static JSONArray toJsonArray(Collection<String> values) {
        JSONArray array = new JSONArray();
        if (values == null) {
            return array;
        }

        for (String value : values) {
            array.put(value);
        }
        return array;
    }

    static int[] toBarcodeFormats(Collection<String> requestedFormats) {
        if (requestedFormats == null || requestedFormats.isEmpty()) {
            return new int[0];
        }

        List<Integer> formats = new ArrayList<>();
        for (String requestedFormat : requestedFormats) {
            Integer mapped = toBarcodeFormat(requestedFormat);
            if (mapped != null) {
                formats.add(mapped);
            }
        }

        int[] output = new int[formats.size()];
        for (int index = 0; index < formats.size(); index += 1) {
            output[index] = formats.get(index);
        }
        return output;
    }

    static Integer toBarcodeFormat(String requestedFormat) {
        if (requestedFormat == null) {
            return null;
        }

        switch (requestedFormat) {
            case "AZTEC":
                return Barcode.FORMAT_AZTEC;
            case "CODABAR":
                return Barcode.FORMAT_CODABAR;
            case "CODE_39":
                return Barcode.FORMAT_CODE_39;
            case "CODE_93":
                return Barcode.FORMAT_CODE_93;
            case "CODE_128":
                return Barcode.FORMAT_CODE_128;
            case "DATA_MATRIX":
                return Barcode.FORMAT_DATA_MATRIX;
            case "EAN_8":
                return Barcode.FORMAT_EAN_8;
            case "EAN_13":
                return Barcode.FORMAT_EAN_13;
            case "ITF":
                return Barcode.FORMAT_ITF;
            case "PDF_417":
                return Barcode.FORMAT_PDF417;
            case "QR_CODE":
                return Barcode.FORMAT_QR_CODE;
            case "UPC_A":
                return Barcode.FORMAT_UPC_A;
            case "UPC_E":
                return Barcode.FORMAT_UPC_E;
            default:
                return null;
        }
    }

    static String nativeFormatName(int nativeFormat) {
        switch (nativeFormat) {
            case Barcode.FORMAT_AZTEC:
                return "AZTEC";
            case Barcode.FORMAT_CODABAR:
                return "CODABAR";
            case Barcode.FORMAT_CODE_39:
                return "CODE_39";
            case Barcode.FORMAT_CODE_93:
                return "CODE_93";
            case Barcode.FORMAT_CODE_128:
                return "CODE_128";
            case Barcode.FORMAT_DATA_MATRIX:
                return "DATA_MATRIX";
            case Barcode.FORMAT_EAN_8:
                return "EAN_8";
            case Barcode.FORMAT_EAN_13:
                return "EAN_13";
            case Barcode.FORMAT_ITF:
                return "ITF";
            case Barcode.FORMAT_PDF417:
                return "PDF_417";
            case Barcode.FORMAT_QR_CODE:
                return "QR_CODE";
            case Barcode.FORMAT_UPC_A:
                return "UPC_A";
            case Barcode.FORMAT_UPC_E:
                return "UPC_E";
            default:
                return null;
        }
    }

    static String normalizedFormatName(int nativeFormat, String rawValue, Collection<String> requestedFormats) {
        String nativeName = nativeFormatName(nativeFormat);

        if (nativeFormat == Barcode.FORMAT_EAN_13
                && rawValue != null
                && rawValue.length() == 13
                && rawValue.startsWith("0")
                && requestedFormats != null
                && requestedFormats.contains("UPC_A")
                && !requestedFormats.contains("EAN_13")) {
            return "UPC_A";
        }

        return nativeName;
    }

    static String mapValueType(int valueType) {
        switch (valueType) {
            case Barcode.TYPE_CONTACT_INFO:
                return "CONTACT_INFO";
            case Barcode.TYPE_EMAIL:
                return "EMAIL";
            case Barcode.TYPE_GEO:
                return "GEO";
            case Barcode.TYPE_ISBN:
                return "ISBN";
            case Barcode.TYPE_PHONE:
                return "PHONE";
            case Barcode.TYPE_PRODUCT:
                return "PRODUCT";
            case Barcode.TYPE_SMS:
                return "SMS";
            case Barcode.TYPE_TEXT:
                return "TEXT";
            case Barcode.TYPE_URL:
                return "URL";
            case Barcode.TYPE_WIFI:
                return "WIFI";
            case Barcode.TYPE_CALENDAR_EVENT:
                return "CALENDAR_EVENT";
            case Barcode.TYPE_DRIVER_LICENSE:
                return "DRIVER_LICENSE";
            default:
                return "UNKNOWN";
        }
    }

    static JSONObject barcodeToResultJson(
            Barcode barcode,
            String engine,
            Collection<String> requestedFormats,
            boolean returnRawBytes
    ) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("cancelled", false);
        result.put("text", barcode.getRawValue());
        result.put("format", normalizedFormatName(barcode.getFormat(), barcode.getRawValue(), requestedFormats));
        result.put("nativeFormat", nativeFormatName(barcode.getFormat()));
        result.put("engine", engine);
        result.put("platform", "android");
        result.put("rawBytesBase64", returnRawBytes ? encodeRawBytes(barcode.getRawBytes()) : JSONObject.NULL);
        result.put("valueType", mapValueType(barcode.getValueType()));
        result.put("bounds", rectToJson(barcode.getBoundingBox()));
        result.put("cornerPoints", pointsToJson(barcode.getCornerPoints()));
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    private static String encodeRawBytes(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            return null;
        }
        return Base64.encodeToString(rawBytes, Base64.NO_WRAP);
    }

    private static JSONObject rectToJson(Rect rect) throws JSONException {
        if (rect == null) {
            return null;
        }

        JSONObject json = new JSONObject();
        json.put("left", rect.left);
        json.put("top", rect.top);
        json.put("width", rect.width());
        json.put("height", rect.height());
        return json;
    }

    private static JSONArray pointsToJson(Point[] points) throws JSONException {
        JSONArray array = new JSONArray();
        if (points == null) {
            return array;
        }

        for (Point point : points) {
            JSONObject pointJson = new JSONObject();
            pointJson.put("x", point.x);
            pointJson.put("y", point.y);
            array.put(pointJson);
        }
        return array;
    }
}
