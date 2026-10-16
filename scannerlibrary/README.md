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
