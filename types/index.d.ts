export type BarcodeFormat =
  | 'AZTEC'
  | 'CODABAR'
  | 'CODE_39'
  | 'CODE_93'
  | 'CODE_128'
  | 'DATA_MATRIX'
  | 'EAN_8'
  | 'EAN_13'
  | 'ITF'
  | 'PDF_417'
  | 'QR_CODE'
  | 'UPC_A'
  | 'UPC_E';

export type AndroidEngine = 'auto' | 'google' | 'mlkit';

export interface SupportInfo {
  supported: boolean;
  available: boolean;
  platform: 'android' | 'ios' | null;
  defaultEngine: string | null;
  availableEngines: string[];
  supportedFormats: BarcodeFormat[];
  unsupportedFormats: BarcodeFormat[];
  requiresPermission: boolean;
  permissionOptional: boolean;
  details: Record<string, unknown> | null;
}

export interface PermissionInfo {
  granted: boolean;
  status: 'granted' | 'denied' | 'not_determined' | 'restricted' | 'unknown';
  canRequest: boolean;
  requiresPermission: boolean;
  platform: 'android' | 'ios' | null;
  engine: string | null;
}

export interface ScanPoint {
  x: number;
  y: number;
}

export interface ScanBounds {
  left: number;
  top: number;
  width: number;
  height: number;
}

export interface ScanOptions {
  formats?: BarcodeFormat[];
  prompt?: string;
  preferFrontCamera?: boolean;
  showTorchButton?: boolean;
  showFlipCameraButton?: boolean;
  beepOnSuccess?: boolean;
  vibrateOnSuccess?: boolean;
  timeoutMs?: number;
  androidEngine?: AndroidEngine;
  returnRawBytes?: boolean;
}

export interface ScanResult {
  cancelled: boolean;
  text: string | null;
  format: BarcodeFormat | null;
  nativeFormat: BarcodeFormat | null;
  engine: string | null;
  platform: 'android' | 'ios' | null;
  rawBytesBase64: string | null;
  valueType: string;
  bounds: ScanBounds | null;
  cornerPoints: ScanPoint[];
  timestamp: number;
}

export interface NativeCodeScannerError extends Error {
  code: string;
  details?: Record<string, unknown>;
}

export interface NativeCodeScannerApi {
  readonly SUPPORTED_FORMATS: BarcodeFormat[];
  isSupported(
    success?: (info: SupportInfo) => void,
    error?: (error: NativeCodeScannerError) => void
  ): Promise<SupportInfo>;
  checkPermission(
    success?: (info: PermissionInfo) => void,
    error?: (error: NativeCodeScannerError) => void
  ): Promise<PermissionInfo>;
  requestPermission(
    success?: (info: PermissionInfo) => void,
    error?: (error: NativeCodeScannerError) => void
  ): Promise<PermissionInfo>;
  scan(
    options?: ScanOptions,
    success?: (result: ScanResult) => void,
    error?: (error: NativeCodeScannerError) => void
  ): Promise<ScanResult>;
  cancel(success?: () => void, error?: (error: NativeCodeScannerError) => void): Promise<void>;
  prewarm(success?: () => void, error?: (error: NativeCodeScannerError) => void): Promise<void>;
}

declare const NativeCodeScanner: NativeCodeScannerApi;

export default NativeCodeScanner;
