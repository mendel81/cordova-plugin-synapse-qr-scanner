import AVFoundation
import AudioToolbox
import Cordova
import CoreGraphics
import Foundation
import UIKit

@objc(NativeCodeScanner)
final class NativeCodeScanner: CDVPlugin {
    private var activeScanCommand: CDVInvokedUrlCommand?
    private weak var scannerViewController: NativeCodeScannerViewController?

    @objc(isSupported:)
    func isSupported(command: CDVInvokedUrlCommand) {
        let support = NativeCodeScannerFormatCatalog.currentSupport(cameraAvailable: Self.isCameraAvailable())
        sendOk(payload: [
            "supported": Self.isCameraAvailable(),
            "available": Self.isCameraAvailable(),
            "platform": "ios",
            "defaultEngine": Self.isCameraAvailable() ? "avfoundation" : NSNull(),
            "availableEngines": Self.isCameraAvailable() ? ["avfoundation"] : [],
            "supportedFormats": support.supportedFormats,
            "unsupportedFormats": support.unsupportedFormats,
            "requiresPermission": Self.isCameraAvailable(),
            "permissionOptional": false,
            "details": [
                "cameraAvailable": Self.isCameraAvailable(),
                "codabarRequiresIOS154": !support.supportedFormats.contains("CODABAR")
            ]
        ], command: command)
    }

    @objc(checkPermission:)
    func checkPermission(command: CDVInvokedUrlCommand) {
        sendOk(payload: buildPermissionInfo(), command: command)
    }

    @objc(requestPermission:)
    func requestPermission(command: CDVInvokedUrlCommand) {
        guard Self.isCameraAvailable() else {
            sendOk(payload: buildPermissionInfo(), command: command)
            return
        }

        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized, .denied, .restricted:
            sendOk(payload: buildPermissionInfo(), command: command)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] _ in
                DispatchQueue.main.async {
                    self?.sendOk(payload: self?.buildPermissionInfo() ?? [:], command: command)
                }
            }
        @unknown default:
            sendOk(payload: buildPermissionInfo(), command: command)
        }
    }

    @objc(prewarm:)
    func prewarm(command: CDVInvokedUrlCommand) {
        sendVoid(command: command)
    }

    @objc(cancel:)
    func cancel(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async { [weak self] in
            self?.scannerViewController?.cancelFromPlugin()
            self?.sendVoid(command: command)
        }
    }

    @objc(scan:)
    func scan(command: CDVInvokedUrlCommand) {
        guard activeScanCommand == nil else {
            sendError(code: "SCAN_IN_PROGRESS", message: "A scan is already in progress", command: command)
            return
        }

        guard Self.isCameraAvailable() else {
            sendError(code: "UNSUPPORTED", message: "This device does not have a usable camera", command: command)
            return
        }

        let options = NativeCodeScannerOptions(rawValue: command.argument(at: 0) as? [String: Any] ?? [:])
        let authStatus = AVCaptureDevice.authorizationStatus(for: .video)

        switch authStatus {
        case .authorized:
            presentScanner(options: options, command: command)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    guard let self else { return }
                    if granted {
                        self.presentScanner(options: options, command: command)
                    } else {
                        self.sendError(code: "PERMISSION_DENIED", message: "Camera access was denied", command: command)
                    }
                }
            }
        case .denied:
            sendError(code: "PERMISSION_DENIED", message: "Camera access was denied", command: command)
        case .restricted:
            sendError(code: "PERMISSION_RESTRICTED", message: "Camera access is restricted on this device", command: command)
        @unknown default:
            sendError(code: "PERMISSION_FAILED", message: "Could not determine the camera permission state", command: command)
        }
    }

    override func onReset() {
        super.onReset()
        scannerViewController?.cancelFromPlugin()
        activeScanCommand = nil
    }

    private func presentScanner(options: NativeCodeScannerOptions, command: CDVInvokedUrlCommand) {
        guard let presentingViewController = topViewController() else {
            sendError(code: "UI_UNAVAILABLE", message: "Could not present the scanner interface", command: command)
            return
        }

        activeScanCommand = command
        let viewController = NativeCodeScannerViewController(options: options)
        viewController.modalPresentationStyle = .fullScreen
        viewController.onResult = { [weak self] payload in
            self?.finishScan(with: payload)
        }
        viewController.onCancel = { [weak self] in
            self?.finishScan(with: NativeCodeScannerPayload.cancelled(engine: "avfoundation"))
        }
        viewController.onError = { [weak self] code, message in
            self?.finishScan(errorCode: code, message: message)
        }

        scannerViewController = viewController
        presentingViewController.present(viewController, animated: true)
    }

    private func finishScan(with payload: [String: Any]) {
        guard let command = activeScanCommand else { return }
        activeScanCommand = nil
        scannerViewController = nil
        sendOk(payload: payload, command: command)
    }

    private func finishScan(errorCode: String, message: String) {
        guard let command = activeScanCommand else { return }
        activeScanCommand = nil
        scannerViewController = nil
        sendError(code: errorCode, message: message, command: command)
    }

    private func sendOk(payload: [String: Any], command: CDVInvokedUrlCommand) {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus.ok, messageAs: payload)
        commandDelegate.send(pluginResult, callbackId: command.callbackId)
    }

    private func sendVoid(command: CDVInvokedUrlCommand) {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus.ok)
        commandDelegate.send(pluginResult, callbackId: command.callbackId)
    }

    private func sendError(code: String, message: String, command: CDVInvokedUrlCommand) {
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus.error,
            messageAs: [
                "code": code,
                "message": message
            ]
        )
        commandDelegate.send(pluginResult, callbackId: command.callbackId)
    }

    private func buildPermissionInfo() -> [String: Any] {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            return [
                "granted": true,
                "status": "granted",
                "canRequest": false,
                "requiresPermission": true,
                "platform": "ios",
                "engine": "avfoundation"
            ]
        case .notDetermined:
            return [
                "granted": false,
                "status": "not_determined",
                "canRequest": true,
                "requiresPermission": true,
                "platform": "ios",
                "engine": "avfoundation"
            ]
        case .restricted:
            return [
                "granted": false,
                "status": "restricted",
                "canRequest": false,
                "requiresPermission": true,
                "platform": "ios",
                "engine": "avfoundation"
            ]
        case .denied:
            return [
                "granted": false,
                "status": "denied",
                "canRequest": false,
                "requiresPermission": true,
                "platform": "ios",
                "engine": "avfoundation"
            ]
        @unknown default:
            return [
                "granted": false,
                "status": "unknown",
                "canRequest": false,
                "requiresPermission": true,
                "platform": "ios",
                "engine": "avfoundation"
            ]
        }
    }

    private func topViewController() -> UIViewController? {
        var current: UIViewController? = viewController
        while let presented = current?.presentedViewController {
            current = presented
        }
        return current
    }

    private static func isCameraAvailable() -> Bool {
        AVCaptureDevice.default(for: .video) != nil
    }
}

private struct NativeCodeScannerOptions {
    let formats: [String]
    let prompt: String
    let preferFrontCamera: Bool
    let showTorchButton: Bool
    let showFlipCameraButton: Bool
    let beepOnSuccess: Bool
    let vibrateOnSuccess: Bool
    let timeoutMs: Int
    let returnRawBytes: Bool

    init(rawValue: [String: Any]) {
        self.formats = (rawValue["formats"] as? [String] ?? []).map { $0.uppercased() }
        self.prompt = rawValue["prompt"] as? String ?? ""
        self.preferFrontCamera = rawValue["preferFrontCamera"] as? Bool ?? false
        self.showTorchButton = rawValue["showTorchButton"] as? Bool ?? false
        self.showFlipCameraButton = rawValue["showFlipCameraButton"] as? Bool ?? false
        self.beepOnSuccess = rawValue["beepOnSuccess"] as? Bool ?? true
        self.vibrateOnSuccess = rawValue["vibrateOnSuccess"] as? Bool ?? false
        self.timeoutMs = rawValue["timeoutMs"] as? Int ?? 0
        self.returnRawBytes = rawValue["returnRawBytes"] as? Bool ?? false
    }
}

private enum NativeCodeScannerPayload {
    static func cancelled(engine: String) -> [String: Any] {
        [
            "cancelled": true,
            "text": NSNull(),
            "format": NSNull(),
            "nativeFormat": NSNull(),
            "engine": engine,
            "platform": "ios",
            "rawBytesBase64": NSNull(),
            "valueType": "UNKNOWN",
            "bounds": NSNull(),
            "cornerPoints": [],
            "timestamp": Int(Date().timeIntervalSince1970 * 1000)
        ]
    }
}

private enum NativeCodeScannerFormatCatalog {
    private static let canonicalFormats = [
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
    ]

    static func currentSupport(cameraAvailable: Bool) -> (supportedFormats: [String], unsupportedFormats: [String]) {
        guard cameraAvailable else {
            return ([], canonicalFormats)
        }

        var supported = canonicalFormats.filter { $0 != "CODABAR" }
        var unsupported: [String] = []

        if #available(iOS 15.4, *) {
            supported.insert("CODABAR", at: 1)
        } else {
            unsupported.append("CODABAR")
        }

        return (supported, unsupported)
    }

    static func metadataTypes(for requestedFormats: [String]) -> [AVMetadataObject.ObjectType] {
        let formats = requestedFormats.isEmpty ? currentSupport(cameraAvailable: true).supportedFormats : requestedFormats
        var types: [AVMetadataObject.ObjectType] = []

        func appendIfNeeded(_ type: AVMetadataObject.ObjectType) {
            if !types.contains(type) {
                types.append(type)
            }
        }

        for format in formats {
            switch format {
            case "QR_CODE":
                appendIfNeeded(.qr)
            case "AZTEC":
                appendIfNeeded(.aztec)
            case "PDF_417":
                appendIfNeeded(.pdf417)
            case "DATA_MATRIX":
                appendIfNeeded(.dataMatrix)
            case "CODABAR":
                if #available(iOS 15.4, *) {
                    appendIfNeeded(.codabar)
                }
            case "CODE_39":
                appendIfNeeded(.code39)
            case "CODE_93":
                appendIfNeeded(.code93)
            case "CODE_128":
                appendIfNeeded(.code128)
            case "EAN_8":
                appendIfNeeded(.ean8)
            case "EAN_13":
                appendIfNeeded(.ean13)
            case "ITF":
                appendIfNeeded(.interleaved2of5)
                appendIfNeeded(.itf14)
            case "UPC_A":
                appendIfNeeded(.ean13)
            case "UPC_E":
                appendIfNeeded(.upce)
            default:
                break
            }
        }

        return types
    }

    static func nativeFormatName(for type: AVMetadataObject.ObjectType) -> String? {
        switch type {
        case .qr:
            return "QR_CODE"
        case .aztec:
            return "AZTEC"
        case .pdf417:
            return "PDF_417"
        case .dataMatrix:
            return "DATA_MATRIX"
        case .code39, .code39Mod43:
            return "CODE_39"
        case .code93:
            return "CODE_93"
        case .code128:
            return "CODE_128"
        case .ean8:
            return "EAN_8"
        case .ean13:
            return "EAN_13"
        case .upce:
            return "UPC_E"
        case .interleaved2of5, .itf14:
            return "ITF"
        default:
            if #available(iOS 15.4, *), type == .codabar {
                return "CODABAR"
            }
            return nil
        }
    }

    static func normalizedFormatName(
        for type: AVMetadataObject.ObjectType,
        value: String,
        requestedFormats: [String]
    ) -> String? {
        if type == .ean13,
           value.count == 13,
           value.hasPrefix("0"),
           requestedFormats.contains("UPC_A"),
           !requestedFormats.contains("EAN_13") {
            return "UPC_A"
        }

        return nativeFormatName(for: type)
    }

    static func valueType(for text: String) -> String {
        let lowercased = text.lowercased()
        if lowercased.hasPrefix("http://") || lowercased.hasPrefix("https://") {
            return "URL"
        }
        if lowercased.hasPrefix("wifi:") {
            return "WIFI"
        }
        if lowercased.hasPrefix("smsto:") || lowercased.hasPrefix("sms:") {
            return "SMS"
        }
        if lowercased.hasPrefix("mailto:") {
            return "EMAIL"
        }
        if lowercased.contains("begin:vcard") {
            return "CONTACT_INFO"
        }
        return "TEXT"
    }
}

private final class NativeCodeScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    let options: NativeCodeScannerOptions
    var onResult: (([String: Any]) -> Void)?
    var onCancel: (() -> Void)?
    var onError: ((String, String) -> Void)?

    private let captureSession = AVCaptureSession()
    private let metadataOutput = AVCaptureMetadataOutput()
    private let sessionQueue = DispatchQueue(label: "nativecodescanner.session")

    private var currentInput: AVCaptureDeviceInput?
    private var currentPosition: AVCaptureDevice.Position
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var overlayView = NativeCodeScannerOverlayView()
    private var timeoutWorkItem: DispatchWorkItem?
    private var hasFinished = false
    private var torchEnabled = false

    init(options: NativeCodeScannerOptions) {
        self.options = options
        self.currentPosition = options.preferFrontCamera ? .front : .back
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        let previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)
        self.previewLayer = previewLayer

        overlayView.translatesAutoresizingMaskIntoConstraints = false
        overlayView.configure(prompt: options.prompt, showTorchButton: options.showTorchButton, showFlipCameraButton: options.showFlipCameraButton)
        overlayView.onCancel = { [weak self] in
            self?.finishCancelled()
        }
        overlayView.onTorch = { [weak self] in
            self?.toggleTorch()
        }
        overlayView.onFlip = { [weak self] in
            self?.flipCamera()
        }
        view.addSubview(overlayView)
        NSLayoutConstraint.activate([
            overlayView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlayView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlayView.topAnchor.constraint(equalTo: view.topAnchor),
            overlayView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        configureSession()
        scheduleTimeoutIfNeeded()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        sessionQueue.async { [weak self] in
            self?.captureSession.startRunning()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        sessionQueue.async { [weak self] in
            if self?.captureSession.isRunning == true {
                self?.captureSession.stopRunning()
            }
        }
    }

    func cancelFromPlugin() {
        finishCancelled()
    }

    private func configureSession() {
        sessionQueue.async { [weak self] in
            guard let self else { return }

            self.captureSession.beginConfiguration()
            defer { self.captureSession.commitConfiguration() }

            self.captureSession.sessionPreset = .high

            do {
                let device = try self.bestDevice(for: self.currentPosition)
                let input = try AVCaptureDeviceInput(device: device)

                if let currentInput = self.currentInput {
                    self.captureSession.removeInput(currentInput)
                }

                if self.captureSession.canAddInput(input) {
                    self.captureSession.addInput(input)
                    self.currentInput = input
                }

                if self.captureSession.outputs.isEmpty, self.captureSession.canAddOutput(self.metadataOutput) {
                    self.captureSession.addOutput(self.metadataOutput)
                }

                self.metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
                let desiredTypes = NativeCodeScannerFormatCatalog.metadataTypes(for: self.options.formats)
                let availableTypes = self.metadataOutput.availableMetadataObjectTypes
                let configuredTypes = desiredTypes.filter { availableTypes.contains($0) }
                if !self.options.formats.isEmpty && configuredTypes.isEmpty {
                    DispatchQueue.main.async {
                        self.finishError(code: "UNSUPPORTED_FORMAT", message: "None of the requested barcode formats are available on this iOS version")
                    }
                    return
                }

                self.metadataOutput.metadataObjectTypes = self.options.formats.isEmpty ? availableTypes : configuredTypes

                DispatchQueue.main.async {
                    self.overlayView.setTorchAvailable(device.hasTorch)
                    self.overlayView.setFlipAvailable(self.hasBothCameraPositions())
                }
            } catch {
                DispatchQueue.main.async {
                    self.finishError(code: "CAMERA_UNAVAILABLE", message: "Could not start the camera")
                }
            }
        }
    }

    private func bestDevice(for position: AVCaptureDevice.Position) throws -> AVCaptureDevice {
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) {
            currentPosition = position
            return device
        }

        let fallbackPosition: AVCaptureDevice.Position = position == .front ? .back : .front
        if let fallbackDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: fallbackPosition) {
            currentPosition = fallbackPosition
            return fallbackDevice
        }

        throw NSError(domain: "NativeCodeScanner", code: 1)
    }

    private func hasBothCameraPositions() -> Bool {
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front) != nil
            && AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil
    }

    private func scheduleTimeoutIfNeeded() {
        guard options.timeoutMs > 0 else { return }
        let workItem = DispatchWorkItem { [weak self] in
            self?.finishCancelled()
        }
        timeoutWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(options.timeoutMs), execute: workItem)
    }

    private func clearTimeout() {
        timeoutWorkItem?.cancel()
        timeoutWorkItem = nil
    }

    private func toggleTorch() {
        guard let device = currentInput?.device, device.hasTorch else { return }

        do {
            try device.lockForConfiguration()
            torchEnabled.toggle()
            if torchEnabled {
                try device.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel)
            } else {
                device.torchMode = .off
            }
            device.unlockForConfiguration()
            overlayView.setTorchEnabled(torchEnabled)
        } catch {
            finishError(code: "TORCH_FAILED", message: "Could not change the torch state")
        }
    }

    private func flipCamera() {
        currentPosition = currentPosition == .front ? .back : .front
        torchEnabled = false
        overlayView.setTorchEnabled(false)
        configureSession()
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard !hasFinished else { return }

        for metadataObject in metadataObjects {
            guard let readable = metadataObject as? AVMetadataMachineReadableCodeObject,
                  let stringValue = readable.stringValue,
                  !stringValue.isEmpty else {
                continue
            }

            let transformedObject = previewLayer?.transformedMetadataObject(for: readable) as? AVMetadataMachineReadableCodeObject
            let normalizedFormat = NativeCodeScannerFormatCatalog.normalizedFormatName(
                for: readable.type,
                value: stringValue,
                requestedFormats: options.formats
            )
            let nativeFormat = NativeCodeScannerFormatCatalog.nativeFormatName(for: readable.type)
            let resolvedFormat: Any = normalizedFormat ?? nativeFormat ?? NSNull()
            let resolvedNativeFormat: Any = nativeFormat ?? NSNull()
            let resolvedBounds: Any = transformedObject.map { Self.boundsPayload(for: $0.bounds) } ?? NSNull()
            let resolvedCornerPoints: Any = transformedObject.map { Self.cornerPayloads(for: $0.corners) } ?? []

            let payload: [String: Any] = [
                "cancelled": false,
                "text": stringValue,
                "format": resolvedFormat,
                "nativeFormat": resolvedNativeFormat,
                "engine": "avfoundation",
                "platform": "ios",
                "rawBytesBase64": NSNull(),
                "valueType": NativeCodeScannerFormatCatalog.valueType(for: stringValue),
                "bounds": resolvedBounds,
                "cornerPoints": resolvedCornerPoints,
                "timestamp": Int(Date().timeIntervalSince1970 * 1000)
            ]

            playSuccessFeedback()
            finishWithResult(payload)
            return
        }
    }

    private func finishWithResult(_ payload: [String: Any]) {
        guard !hasFinished else { return }
        hasFinished = true
        clearTimeout()
        dismiss(animated: true) { [onResult] in
            onResult?(payload)
        }
    }

    private func finishCancelled() {
        guard !hasFinished else { return }
        hasFinished = true
        clearTimeout()
        dismiss(animated: true) { [onCancel] in
            onCancel?()
        }
    }

    private func finishError(code: String, message: String) {
        guard !hasFinished else { return }
        hasFinished = true
        clearTimeout()
        dismiss(animated: true) { [onError] in
            onError?(code, message)
        }
    }

    private func playSuccessFeedback() {
        if options.beepOnSuccess {
            AudioServicesPlaySystemSound(1057)
        }

        if options.vibrateOnSuccess {
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        }
    }

    private static func boundsPayload(for rect: CGRect) -> [String: CGFloat] {
        [
            "left": rect.origin.x,
            "top": rect.origin.y,
            "width": rect.width,
            "height": rect.height
        ]
    }

    private static func cornerPayloads(for corners: [Any]) -> [[String: CGFloat]] {
        corners.compactMap { corner in
            guard let dictionary = corner as? NSDictionary,
                  let point = CGPoint(dictionaryRepresentation: dictionary) else {
                return nil
            }

            return [
                "x": point.x,
                "y": point.y
            ]
        }
    }
}

private final class NativeCodeScannerOverlayView: UIView {
    var onCancel: (() -> Void)?
    var onTorch: (() -> Void)?
    var onFlip: (() -> Void)?

    private let promptLabel = UILabel()
    private let cancelButton = UIButton(type: .system)
    private let torchButton = UIButton(type: .system)
    private let flipButton = UIButton(type: .system)

    private var showTorchButton = false
    private var showFlipCameraButton = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear

        promptLabel.translatesAutoresizingMaskIntoConstraints = false
        promptLabel.textColor = .white
        promptLabel.font = UIFont.systemFont(ofSize: 17, weight: .semibold)
        promptLabel.textAlignment = .center
        promptLabel.numberOfLines = 0
        promptLabel.backgroundColor = UIColor(white: 0.08, alpha: 0.72)
        promptLabel.layer.cornerRadius = 20
        promptLabel.layer.masksToBounds = true
        promptLabel.layoutMargins = UIEdgeInsets(top: 12, left: 18, bottom: 12, right: 18)
        addSubview(promptLabel)

        [cancelButton, torchButton, flipButton].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            $0.titleLabel?.font = UIFont.systemFont(ofSize: 15, weight: .bold)
            $0.setTitleColor(.white, for: .normal)
            $0.backgroundColor = UIColor(red: 0.06, green: 0.30, blue: 0.50, alpha: 0.92)
            $0.layer.cornerRadius = 22
            $0.contentEdgeInsets = UIEdgeInsets(top: 12, left: 18, bottom: 12, right: 18)
            addSubview($0)
        }

        cancelButton.backgroundColor = UIColor(white: 0.93, alpha: 0.95)
        cancelButton.setTitleColor(.black, for: .normal)
        cancelButton.setTitle("Close", for: .normal)
        torchButton.setTitle("Light", for: .normal)
        flipButton.setTitle("Camera", for: .normal)

        cancelButton.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        torchButton.addTarget(self, action: #selector(torchTapped), for: .touchUpInside)
        flipButton.addTarget(self, action: #selector(flipTapped), for: .touchUpInside)

        NSLayoutConstraint.activate([
            promptLabel.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor, constant: 24),
            promptLabel.centerXAnchor.constraint(equalTo: centerXAnchor),
            promptLabel.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor, constant: 20),
            promptLabel.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -20),

            cancelButton.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            cancelButton.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor, constant: -24),

            torchButton.centerXAnchor.constraint(equalTo: centerXAnchor),
            torchButton.centerYAnchor.constraint(equalTo: cancelButton.centerYAnchor),

            flipButton.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -20),
            flipButton.centerYAnchor.constraint(equalTo: cancelButton.centerYAnchor)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(prompt: String, showTorchButton: Bool, showFlipCameraButton: Bool) {
        promptLabel.isHidden = prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        promptLabel.text = prompt
        self.showTorchButton = showTorchButton
        self.showFlipCameraButton = showFlipCameraButton
        torchButton.isHidden = !showTorchButton
        flipButton.isHidden = !showFlipCameraButton
        setNeedsDisplay()
    }

    func setTorchAvailable(_ available: Bool) {
        torchButton.isHidden = !showTorchButton || !available
        torchButton.alpha = available ? 1 : 0.45
        torchButton.isEnabled = available
    }

    func setFlipAvailable(_ available: Bool) {
        flipButton.isHidden = !showFlipCameraButton || !available
        flipButton.isEnabled = available
        flipButton.alpha = available ? 1 : 0.45
    }

    func setTorchEnabled(_ enabled: Bool) {
        torchButton.setTitle(enabled ? "Light on" : "Light", for: .normal)
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }

        let overlayColor = UIColor(white: 0.0, alpha: 0.60)
        context.setFillColor(overlayColor.cgColor)
        context.fill(bounds)

        let horizontalInset = bounds.width * 0.12
        let finderWidth = bounds.width - (horizontalInset * 2)
        let finderHeight = min(finderWidth * 0.62, bounds.height * 0.35)
        let finderRect = CGRect(
            x: horizontalInset,
            y: (bounds.height - finderHeight) / 2,
            width: finderWidth,
            height: finderHeight
        )

        context.clear(finderRect.insetBy(dx: -1, dy: -1))
        let path = UIBezierPath(roundedRect: finderRect, cornerRadius: 24)
        UIColor.white.setStroke()
        path.lineWidth = 3
        path.stroke()
    }

    @objc private func cancelTapped() {
        onCancel?()
    }

    @objc private func torchTapped() {
        onTorch?()
    }

    @objc private func flipTapped() {
        onFlip?()
    }
}
