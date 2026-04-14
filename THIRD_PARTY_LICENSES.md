# Third-Party Licenses

This repository's source code is licensed under Apache License 2.0 so the
project follows the same licensing family used by Apache Cordova and many
official Cordova plugins.

That does not make every runtime dependency Apache-2.0. Native dependencies are
resolved during app builds and keep their own licenses and terms.

## Direct runtime/build dependencies

| Component | Used for | License / terms |
| --- | --- | --- |
| Plugin source in this repository | JS, Android and iOS plugin code | Apache License 2.0 |
| AndroidX Camera, AppCompat, concurrent-futures | Android camera UI and lifecycle integration | Apache License 2.0 |
| Guava | Android `ListenableFuture` runtime compatibility | Apache License 2.0 |
| Google Play Services Code Scanner | Android low-friction scanner path | ML Kit Terms of Service |
| Google ML Kit Barcode Scanning | Android fallback scanner engine | ML Kit Terms of Service |
| AVFoundation, AudioToolbox | iOS native scanning and feedback | Apple platform SDK terms; system frameworks are not redistributed by this repo |

## Practical publication guidance

- Publishing this repository to GitHub under Apache-2.0 is fine.
- Publishing this package to npm under Apache-2.0 is fine for the plugin source itself.
- Android apps that consume this plugin should review the Google ML Kit terms as part of their normal release compliance process.
- This repository does not bundle Google AAR binaries directly; Android resolves them during the build.

## Primary upstream references

- Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>
- Apache Cordova project: <https://cordova.apache.org/>
- AndroidX Camera release notes: <https://developer.android.com/jetpack/androidx/releases/camera>
- Google ML Kit terms: <https://developers.google.com/ml-kit/terms>
