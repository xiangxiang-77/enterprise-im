# Android PJSIP Native Runtime

Put PJSIP/pjsua2 Android native libraries here before final device media build.

Expected layout:

```text
jniLibs/
  arm64-v8a/
    libpjsua2.so
    libpj.so
    libpjlib-util.so
    libpjmedia.so
    libpjnath.so
    libpjsip.so
    libpjsip-simple.so
    libpjsip-ua.so
  armeabi-v7a/
    same libraries
```

Use PJSIP 2.10-2.14.

Full native Android PJSIP needs both:

- native `.so` files under `jniLibs/<abi>/`
- pjsua2 Java binding, usually `pjsua2.jar` or an Android `.aar`, under
  `android/app/libs/`

The Flutter app already exposes `MethodChannel('enterprise_im/sip')`; Android
code first checks for native `libpjsua2.so`, then falls back to Android platform
SIP when the native SDK is not bundled.
