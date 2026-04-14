# cordova-plugin-synapse-qr-scanner

Cordova plugin for QR and barcode scanning on Android and iOS.
Plugin Cordova para escaneo de códigos QR y de barras en Android e iOS.

Built and maintained by [Synapse](https://synapse.es).
Desarrollado y mantenido por [Synapse](https://synapse.es).

## English

### Overview

`cordova-plugin-synapse-qr-scanner` is a native scanner plugin for Cordova projects that need a modern, low-friction setup and clean coexistence with `cordova-plugin-firebasex`.

Highlights:

- Android uses `auto` engine selection:
  - Google Code Scanner first when the request is compatible and Google Play Services is available.
  - CameraX + ML Kit fallback when Google Code Scanner is unavailable or the request needs a custom live-scanner UI.
- iOS uses `AVFoundation` directly, with no third-party iOS scanning dependency.
- Promise-first JavaScript API with optional Cordova-style callbacks.
- Normalized results across platforms.
- No manual edits required in `Gradle`, `Podfile`, `AndroidManifest.xml`, or `Info.plist`.

This plugin does not apply `google-services`, does not depend on deprecated Firebase ML Vision artifacts, and does not inject extra iOS pods.

It is an independent community plugin. It is not an official Apache Cordova plugin and is not affiliated with Apache, Google, Apple, or Firebase.

### Install

From npm:

```bash
cordova plugin add cordova-plugin-synapse-qr-scanner
```

From a local checkout:

```bash
cordova plugin add /absolute/path/to/plugin-folder
```

Optional install-time overrides:

```bash
cordova plugin add cordova-plugin-synapse-qr-scanner \
  --variable CAMERA_USAGE_DESCRIPTION="Scan inventory labels with the camera." \
  --variable ANDROIDX_CAMERA_VERSION=1.6.0 \
  --variable MLKIT_BARCODE_SCANNING_VERSION=17.3.0 \
  --variable PLAY_SERVICES_CODE_SCANNER_VERSION=16.1.0
```

### JavaScript API

The plugin is exposed as:

- `window.NativeCodeScanner`
- `cordova.plugins.nativeCodeScanner`

Promise-based usage:

```js
const result = await NativeCodeScanner.scan({
  formats: ['QR_CODE', 'EAN_13', 'CODE_128'],
  prompt: 'Aim at the code',
  preferFrontCamera: false,
  showTorchButton: true,
  showFlipCameraButton: false,
  beepOnSuccess: true,
  vibrateOnSuccess: false,
  timeoutMs: 0,
  androidEngine: 'auto',
  returnRawBytes: false
});

if (!result.cancelled) {
  console.log(result.text, result.format, result.engine);
}
```

Optional callback wrapper:

```js
NativeCodeScanner.scan(
  { formats: ['QR_CODE'] },
  function onSuccess(result) {
    console.log(result);
  },
  function onError(error) {
    console.error(error.code, error.message);
  }
);
```

API surface:

- `isSupported(): Promise<SupportInfo>`
- `checkPermission(): Promise<PermissionInfo>`
- `requestPermission(): Promise<PermissionInfo>`
- `scan(options?): Promise<ScanResult>`
- `cancel(): Promise<void>`
- `prewarm(): Promise<void>`

TypeScript definitions ship in [`types/index.d.ts`](./types/index.d.ts).

Supported format names:

- `QR_CODE`
- `AZTEC`
- `PDF_417`
- `DATA_MATRIX`
- `CODABAR`
- `CODE_39`
- `CODE_93`
- `CODE_128`
- `EAN_8`
- `EAN_13`
- `ITF`
- `UPC_A`
- `UPC_E`

### Engine behavior

#### Android

`androidEngine` accepts:

- `auto`
- `google`
- `mlkit`

`auto` selects Google Code Scanner when:

- Google Play Services is available
- the device has a camera
- the request does not require custom live-scanner UI

`auto` falls back to CameraX + ML Kit when the request needs features Google Code Scanner does not expose directly, including:

- `prompt`
- `preferFrontCamera`
- `showTorchButton`
- `showFlipCameraButton`
- `timeoutMs`
- `vibrateOnSuccess`
- `beepOnSuccess: false`

If `androidEngine: 'google'` is forced with incompatible options, the plugin rejects with `INCOMPATIBLE_OPTIONS`.

#### iOS

iOS always uses the native `AVFoundation` implementation.

### Permission model

#### Android

Permissions are reported from the point of view of the default `auto` engine.

- If Google Code Scanner is available, `checkPermission()` may resolve with `granted: true` and `requiresPermission: false` even before camera permission is granted.
- If the request resolves to the ML Kit engine, camera permission is required and the plugin requests it automatically during `scan()` when possible.

#### iOS

Live scanning always requires camera permission.

### Supported formats by platform

| Format | Android Google | Android ML Kit | iOS AVFoundation |
| --- | --- | --- | --- |
| `QR_CODE` | Yes | Yes | Yes |
| `AZTEC` | Yes | Yes | Yes |
| `PDF_417` | Yes | Yes | Yes |
| `DATA_MATRIX` | Yes | Yes | Yes |
| `CODABAR` | Yes | Yes | Yes, iOS 15.4+ |
| `CODE_39` | Yes | Yes | Yes |
| `CODE_93` | Yes | Yes | Yes |
| `CODE_128` | Yes | Yes | Yes |
| `EAN_8` | Yes | Yes | Yes |
| `EAN_13` | Yes | Yes | Yes |
| `ITF` | Yes | Yes | Yes |
| `UPC_A` | Yes | Yes | Yes, normalized from EAN-13 subset on iOS |
| `UPC_E` | Yes | Yes | Yes |

iOS normalization notes:

- `UPC_A` is normalized from the `EAN_13` subset when the scanned value starts with `0` and the request asked for `UPC_A` without `EAN_13`.
- `ITF` is normalized from `interleaved2of5` and `ITF14`.
- `CODABAR` depends on iOS 15.4+ runtime support from `AVFoundation`.

### Result contract

Successful scan:

```js
{
  cancelled: false,
  text: '0123456789012',
  format: 'EAN_13',
  nativeFormat: 'EAN_13',
  engine: 'google',
  platform: 'android',
  rawBytesBase64: null,
  valueType: 'PRODUCT',
  bounds: { left: 0, top: 0, width: 100, height: 50 },
  cornerPoints: [{ x: 0, y: 0 }],
  timestamp: 1713072000000
}
```

User cancellation resolves normally:

```js
{
  cancelled: true,
  text: null,
  format: null,
  nativeFormat: null,
  engine: 'mlkit',
  platform: 'android',
  rawBytesBase64: null,
  valueType: 'UNKNOWN',
  bounds: null,
  cornerPoints: [],
  timestamp: 1713072000000
}
```

### Error codes

Common codes:

- `INVALID_ARGUMENT`
- `INVALID_FORMAT`
- `INVALID_ENGINE`
- `SCAN_IN_PROGRESS`
- `UNSUPPORTED`
- `GOOGLE_SCANNER_UNAVAILABLE`
- `INCOMPATIBLE_OPTIONS`
- `PERMISSION_DENIED`
- `PERMISSION_RESTRICTED`
- `CAMERA_UNAVAILABLE`
- `TORCH_FAILED`
- `UNSUPPORTED_FORMAT`
- `SCAN_FAILED`
- `RESULT_ENCODING_FAILED`
- `UI_UNAVAILABLE`

### Firebasex compatibility

Validated against `cordova-plugin-firebasex@20.0.1`.

Compatibility rules:

- No `google-services` plugin application.
- No Firebase SDK dependency.
- No deprecated Firebase ML Vision artifacts.
- No iOS CocoaPods dependency injection.
- No mutation of Firebasex variables or Gradle plugin setup.
- Android dependencies are limited to Google Code Scanner, CameraX, ML Kit Barcode Scanning, and a small Guava runtime dependency used by the CameraX provider future type.

What was validated:

- `cordova plugin add` in a clean Cordova Android app
- Android debug APK build
- iOS simulator build
- install and `prepare` with `cordova-plugin-firebasex@20.0.1`

The Firebasex smoke app intentionally stops at `prepare`, because Firebasex itself requires real Firebase config files to complete a native build.

### Licensing and publication

The repository source is licensed under **Apache License 2.0**, which keeps it aligned with the license family used by Apache Cordova and many official Cordova plugins.

Important nuance:

- This repository is Apache-2.0.
- AndroidX and Guava dependencies are Apache-2.0.
- `Google Play Services Code Scanner` and `Google ML Kit Barcode Scanning` are distributed under **ML Kit Terms of Service**.
- iOS uses Apple system frameworks and adds no third-party pods.

That means the plugin can be published on GitHub and npm under Apache-2.0, while Android teams should still review ML Kit terms as part of their own compliance process.

See:

- [`LICENSE`](./LICENSE)
- [`NOTICE`](./NOTICE)
- [`THIRD_PARTY_LICENSES.md`](./THIRD_PARTY_LICENSES.md)

### Example app

A minimal example UI lives in [`example-app/www`](./example-app/www).

### Development

Tests:

```bash
npm test
```

Smoke tests:

```bash
npm run smoke:android
npm run smoke:ios
npm run smoke:firebasex
npm run smoke:all:latest-cli
npm run smoke:firebasex:latest-cli
```

Performance benchmark:

```bash
npm run perf:bench
```

This benchmark covers JavaScript API normalization overhead with a mocked native bridge and hot paths for `isSupported()`, `checkPermission()`, `scan()`, and cancellation normalization.

It does not measure physical camera startup time, live Android frame analysis throughput, or device-specific iOS camera latency.

Packaging check:

```bash
npm run pack:check
```

### Manual device check

```bash
npx -y cordova@13.0.0 create native-scanner-smoke com.acme.nativescanner NativeScannerSmoke
cd native-scanner-smoke
npx -y cordova@13.0.0 platform add android@15.0.0
npx -y cordova@13.0.0 platform add ios@8.0.1
npx -y cordova@13.0.0 plugin add /absolute/path/to/plugin-folder
```

Then:

- copy the sample UI from [`example-app/www`](./example-app/www)
- run `npx -y cordova@13.0.0 run android --device`
- or run `npx -y cordova@13.0.0 run ios --device`
- verify `isSupported()`, `requestPermission()`, `prewarm()`, `scan()`, cancellation, torch toggle, and at least one QR and one linear barcode scan

### Publishing checklist

1. Run `npm test`
2. Run `npm run smoke:android`
3. Run `npm run smoke:ios`
4. Run `npm run smoke:firebasex`
5. Run `npm run smoke:all:latest-cli`
6. Run `npm run smoke:firebasex:latest-cli`
7. Run `npm run perf:bench`
8. Run `npm run pack:check`
9. Review [`THIRD_PARTY_LICENSES.md`](./THIRD_PARTY_LICENSES.md) if dependency versions changed

### Notes

- `prewarm()` is best-effort:
  - Android tries to warm Google Code Scanner when possible.
  - iOS is a safe no-op.
- `cancel()` is best-effort:
  - Android ML Kit activity and the iOS custom scanner can be dismissed.
  - Android Google Code Scanner is a safe no-op because the system UI is external.
- `scanFromImage()` is intentionally left out of v1 to keep the live-camera path stable first.

## Español

### Resumen

`cordova-plugin-synapse-qr-scanner` es un plugin nativo de escaneo para proyectos Cordova que necesitan una instalación sencilla, mantenimiento razonable y buena convivencia con `cordova-plugin-firebasex`.

Puntos clave:

- En Android usa selección automática de motor:
  - Primero Google Code Scanner cuando la petición es compatible y Google Play Services está disponible.
  - Fallback a CameraX + ML Kit cuando hace falta una UI de escaneo propia o Google Code Scanner no está disponible.
- En iOS usa `AVFoundation` directamente, sin dependencias iOS de terceros.
- API JavaScript orientada a Promises, con callbacks estilo Cordova opcionales.
- Resultados normalizados entre plataformas.
- Instalación sin tocar `Gradle`, `Podfile`, `AndroidManifest.xml` ni `Info.plist`.

Este plugin no aplica `google-services`, no depende de Firebase ML Vision y no inyecta pods extra en iOS.

Es un plugin comunitario e independiente. No es un plugin oficial de Apache Cordova ni está afiliado a Apache, Google, Apple o Firebase.

### Instalación

Desde npm:

```bash
cordova plugin add cordova-plugin-synapse-qr-scanner
```

Desde un checkout local:

```bash
cordova plugin add /absolute/path/to/plugin-folder
```

Overrides opcionales en instalación:

```bash
cordova plugin add cordova-plugin-synapse-qr-scanner \
  --variable CAMERA_USAGE_DESCRIPTION="Escanear etiquetas con la cámara." \
  --variable ANDROIDX_CAMERA_VERSION=1.6.0 \
  --variable MLKIT_BARCODE_SCANNING_VERSION=17.3.0 \
  --variable PLAY_SERVICES_CODE_SCANNER_VERSION=16.1.0
```

### API JavaScript

El plugin se expone como:

- `window.NativeCodeScanner`
- `cordova.plugins.nativeCodeScanner`

Uso con Promises:

```js
const result = await NativeCodeScanner.scan({
  formats: ['QR_CODE', 'EAN_13', 'CODE_128'],
  prompt: 'Apunta al código',
  preferFrontCamera: false,
  showTorchButton: true,
  showFlipCameraButton: false,
  beepOnSuccess: true,
  vibrateOnSuccess: false,
  timeoutMs: 0,
  androidEngine: 'auto',
  returnRawBytes: false
});

if (!result.cancelled) {
  console.log(result.text, result.format, result.engine);
}
```

Wrapper opcional con callbacks:

```js
NativeCodeScanner.scan(
  { formats: ['QR_CODE'] },
  function onSuccess(result) {
    console.log(result);
  },
  function onError(error) {
    console.error(error.code, error.message);
  }
);
```

Superficie de API:

- `isSupported(): Promise<SupportInfo>`
- `checkPermission(): Promise<PermissionInfo>`
- `requestPermission(): Promise<PermissionInfo>`
- `scan(options?): Promise<ScanResult>`
- `cancel(): Promise<void>`
- `prewarm(): Promise<void>`

Las definiciones TypeScript están en [`types/index.d.ts`](./types/index.d.ts).

Formatos soportados:

- `QR_CODE`
- `AZTEC`
- `PDF_417`
- `DATA_MATRIX`
- `CODABAR`
- `CODE_39`
- `CODE_93`
- `CODE_128`
- `EAN_8`
- `EAN_13`
- `ITF`
- `UPC_A`
- `UPC_E`

### Comportamiento del motor

#### Android

`androidEngine` acepta:

- `auto`
- `google`
- `mlkit`

`auto` elige Google Code Scanner cuando:

- Google Play Services está disponible
- el dispositivo tiene cámara
- la petición no necesita UI personalizada de escaneo en vivo

`auto` hace fallback a CameraX + ML Kit cuando la petición necesita opciones que Google Code Scanner no expone directamente, como:

- `prompt`
- `preferFrontCamera`
- `showTorchButton`
- `showFlipCameraButton`
- `timeoutMs`
- `vibrateOnSuccess`
- `beepOnSuccess: false`

Si fuerzas `androidEngine: 'google'` con opciones incompatibles, el plugin rechaza con `INCOMPATIBLE_OPTIONS`.

#### iOS

iOS usa siempre la implementación nativa basada en `AVFoundation`.

### Modelo de permisos

#### Android

Los permisos se informan desde el punto de vista del motor por defecto `auto`.

- Si Google Code Scanner está disponible, `checkPermission()` puede resolver con `granted: true` y `requiresPermission: false` aunque el permiso de cámara todavía no se haya concedido.
- Si la petición acaba usando ML Kit, el permiso de cámara es obligatorio y el plugin lo solicita automáticamente durante `scan()` cuando es posible.

#### iOS

El escaneo en vivo siempre requiere permiso de cámara.

### Formatos soportados por plataforma

| Formato | Android Google | Android ML Kit | iOS AVFoundation |
| --- | --- | --- | --- |
| `QR_CODE` | Sí | Sí | Sí |
| `AZTEC` | Sí | Sí | Sí |
| `PDF_417` | Sí | Sí | Sí |
| `DATA_MATRIX` | Sí | Sí | Sí |
| `CODABAR` | Sí | Sí | Sí, iOS 15.4+ |
| `CODE_39` | Sí | Sí | Sí |
| `CODE_93` | Sí | Sí | Sí |
| `CODE_128` | Sí | Sí | Sí |
| `EAN_8` | Sí | Sí | Sí |
| `EAN_13` | Sí | Sí | Sí |
| `ITF` | Sí | Sí | Sí |
| `UPC_A` | Sí | Sí | Sí, normalizado desde el subconjunto EAN-13 en iOS |
| `UPC_E` | Sí | Sí | Sí |

Notas de normalización en iOS:

- `UPC_A` se normaliza desde el subconjunto `EAN_13` cuando el valor empieza por `0` y la petición pidió `UPC_A` sin `EAN_13`.
- `ITF` se normaliza desde `interleaved2of5` e `ITF14`.
- `CODABAR` depende del soporte runtime de `AVFoundation` en iOS 15.4+.

### Contrato de resultado

Escaneo correcto:

```js
{
  cancelled: false,
  text: '0123456789012',
  format: 'EAN_13',
  nativeFormat: 'EAN_13',
  engine: 'google',
  platform: 'android',
  rawBytesBase64: null,
  valueType: 'PRODUCT',
  bounds: { left: 0, top: 0, width: 100, height: 50 },
  cornerPoints: [{ x: 0, y: 0 }],
  timestamp: 1713072000000
}
```

La cancelación del usuario resuelve de forma normal:

```js
{
  cancelled: true,
  text: null,
  format: null,
  nativeFormat: null,
  engine: 'mlkit',
  platform: 'android',
  rawBytesBase64: null,
  valueType: 'UNKNOWN',
  bounds: null,
  cornerPoints: [],
  timestamp: 1713072000000
}
```

### Códigos de error

Códigos habituales:

- `INVALID_ARGUMENT`
- `INVALID_FORMAT`
- `INVALID_ENGINE`
- `SCAN_IN_PROGRESS`
- `UNSUPPORTED`
- `GOOGLE_SCANNER_UNAVAILABLE`
- `INCOMPATIBLE_OPTIONS`
- `PERMISSION_DENIED`
- `PERMISSION_RESTRICTED`
- `CAMERA_UNAVAILABLE`
- `TORCH_FAILED`
- `UNSUPPORTED_FORMAT`
- `SCAN_FAILED`
- `RESULT_ENCODING_FAILED`
- `UI_UNAVAILABLE`

### Compatibilidad con Firebasex

Validado contra `cordova-plugin-firebasex@20.0.1`.

Reglas de compatibilidad:

- No aplica el plugin `google-services`.
- No depende del SDK de Firebase.
- No usa artefactos obsoletos de Firebase ML Vision.
- No inyecta dependencias CocoaPods en iOS.
- No modifica variables de Firebasex ni la configuración de plugins de Gradle.
- Las dependencias Android se limitan a Google Code Scanner, CameraX, ML Kit Barcode Scanning y una pequeña dependencia runtime de Guava usada por el future del provider de CameraX.

Qué se validó:

- `cordova plugin add` en una app Cordova Android limpia
- compilación del APK debug Android
- compilación iOS para simulador
- instalación y `prepare` con `cordova-plugin-firebasex@20.0.1`

La smoke app de Firebasex se detiene a propósito en `prepare`, porque Firebasex necesita ficheros Firebase reales para completar un build nativo.

### Licenciamiento y publicación

El código fuente del repositorio está licenciado bajo **Apache License 2.0**, lo que mantiene el paquete dentro de la familia de licencias usada por Apache Cordova y muchos plugins oficiales.

Matices importantes:

- Este repositorio es Apache-2.0.
- AndroidX y Guava son Apache-2.0.
- `Google Play Services Code Scanner` y `Google ML Kit Barcode Scanning` se distribuyen bajo los **ML Kit Terms of Service**.
- iOS usa frameworks del sistema de Apple y no añade pods de terceros.

En la práctica, el plugin puede publicarse en GitHub y npm bajo Apache-2.0, pero los equipos Android deben revisar igualmente los términos de ML Kit como parte de su proceso de compliance.

Consulta:

- [`LICENSE`](./LICENSE)
- [`NOTICE`](./NOTICE)
- [`THIRD_PARTY_LICENSES.md`](./THIRD_PARTY_LICENSES.md)

### App de ejemplo

Hay una UI mínima en [`example-app/www`](./example-app/www).

### Desarrollo

Tests:

```bash
npm test
```

Pruebas smoke:

```bash
npm run smoke:android
npm run smoke:ios
npm run smoke:firebasex
npm run smoke:all:latest-cli
npm run smoke:firebasex:latest-cli
```

Benchmark de rendimiento:

```bash
npm run perf:bench
```

Este benchmark mide la sobrecarga de la API JavaScript usando un bridge nativo mockeado y los hot paths de `isSupported()`, `checkPermission()`, `scan()` y la normalización de cancelación.

No mide el arranque físico de cámara, el throughput de análisis de frames en Android ni la latencia de cámara iOS en hardware concreto.

Verificación de empaquetado:

```bash
npm run pack:check
```

### Verificación manual en dispositivo

```bash
npx -y cordova@13.0.0 create native-scanner-smoke com.acme.nativescanner NativeScannerSmoke
cd native-scanner-smoke
npx -y cordova@13.0.0 platform add android@15.0.0
npx -y cordova@13.0.0 platform add ios@8.0.1
npx -y cordova@13.0.0 plugin add /absolute/path/to/plugin-folder
```

Después:

- copia la UI de ejemplo desde [`example-app/www`](./example-app/www)
- ejecuta `npx -y cordova@13.0.0 run android --device`
- o ejecuta `npx -y cordova@13.0.0 run ios --device`
- verifica `isSupported()`, `requestPermission()`, `prewarm()`, `scan()`, cancelación, linterna y al menos un escaneo QR y uno lineal

### Checklist de publicación

1. Ejecuta `npm test`
2. Ejecuta `npm run smoke:android`
3. Ejecuta `npm run smoke:ios`
4. Ejecuta `npm run smoke:firebasex`
5. Ejecuta `npm run smoke:all:latest-cli`
6. Ejecuta `npm run smoke:firebasex:latest-cli`
7. Ejecuta `npm run perf:bench`
8. Ejecuta `npm run pack:check`
9. Revisa [`THIRD_PARTY_LICENSES.md`](./THIRD_PARTY_LICENSES.md) si cambian las versiones de dependencias

### Notas

- `prewarm()` es best-effort:
  - Android intenta precalentar Google Code Scanner cuando es posible.
  - iOS es un no-op seguro.
- `cancel()` también es best-effort:
  - la activity ML Kit de Android y el escáner personalizado de iOS pueden cerrarse.
  - con Google Code Scanner en Android es un no-op seguro porque la UI pertenece al sistema.
- `scanFromImage()` se deja fuera de la v1 para mantener primero un camino de cámara en vivo estable.
