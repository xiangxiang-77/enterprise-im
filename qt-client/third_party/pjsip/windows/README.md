# Windows PJSIP Runtime

Windows PJSIP 2.10-2.14 runtime used by the final VS2017 package:

- `pjsua.exe`
- PJSIP/PJMEDIA/PJLIB DLL files required by `pjsua.exe`
- OpenSSL/runtime DLL files required by your PJSIP build

The bundled runtime is built from PJSIP 2.14. `pjsua.exe --help` must expose
`--video`, `--vcapture-dev`, and `--vrender-dev` before claiming desktop video
capability. Rebuild with:

```powershell
.\scripts\build-pjsip-windows-mingw.ps1 -EnableVideo -Clean
```

`scripts/package-qt-vs2017-webengine.ps1` copies this folder into the desktop
distribution next to `EnterpriseIMQtClient.exe`.

Expected final behavior: desktop audio/video buttons start local `pjsua.exe`
against the SIP config returned by `/api/calls/{id}/media-config`.
