# scannerlibrary

Independent Android barcode scanning capability for this repository.

## Modules

- `:scannerlibrary`: CameraX preview, zxing-cpp decoding, tap-to-focus, torch control,
  consecutive-frame confirmation, and a vendor-neutral public API.
- `:scanner-demo`: installable manual test application. It contains no business logic and
  is intended for real-label/device acceptance testing.

## Run the demo

```shell
./gradlew :scanner-demo:installDebug
```

The demo requests camera permission and scans the `COMMON` profile: QR Code (including
Micro QR and rMQR), Data Matrix, Code 128, EAN-13, and EAN-8.

## Integrate with another app module

```gradle
dependencies {
    implementation project(':scannerlibrary')
}
```

The application manifest, not this library, must explicitly declare camera permission:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

Create `ScannerView`, assign a `ScannerListener`, configure a `ScannerProfile`, and bind it
to the screen's `LifecycleOwner`. Call `stop()` when the screen is no longer visible and
`release()` when the view is permanently destroyed.

The library returns the raw decoded value and symbology. Device binding, SN/MAC validation,
GS1 application-identifier parsing, persistence, and navigation belong to the consuming app.

### Selecting among several codes

By default the view decodes a single symbol per frame and confirms it once it has been seen in
`confirmationFrames` consecutive frames — point and go. When the scene may hold more than one
code and the user should pick, turn on tap-to-select:

```kotlin
scannerView.config = ScannerConfig(
    formats = setOf(BarcodeFormat.DATA_MATRIX),
    maxSymbolsPerFrame = 6,
    tapToSelect = true,
)
```

- A code is boxed once it has been decoded in `confirmationFrames` consecutive frames; a box
  survives one missed frame and drops on the second.
- A **lone** code stays automatic: it confirms after it has been the only boxed code for
  `confirmationFrames * 4` frames — longer than the boxing threshold, so that a slow detector
  still has time to find a second code before the first one auto-confirms.
- **Two or more** stable codes latch the view into select mode. Every code gets a box, nothing
  is confirmed, and the latch holds even after the others leave the frame — until the whole
  scene has been empty for a short while.
- Tapping a box delivers `ScannerListener.onCodeSelected`; tapping anywhere else keeps the usual
  tap-to-focus behavior.
- `maxSymbolsPerFrame` must be greater than 1; `ScannerConfig` rejects the combination, because
  a decoder capped at one symbol can never see the second code.

Rotation, the preview scale type, and any difference between the analysis and preview
resolutions are handled inside the library. Consumers never compute overlay coordinates.
