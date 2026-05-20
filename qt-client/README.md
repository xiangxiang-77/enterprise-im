# Enterprise IM Qt Client

Minimal Qt Widgets client for the Java Netty IM protocol and HTTP call signaling API.

## Scope

- Connect to the TCP gateway.
- Send `AUTH`, `PING`, and `TEXT` JSON-line frames.
- Display inbound frames including `AUTH_OK`, `PONG`, `ACK`, and `TEXT_DELIVER`.
- Check `/api/calls/readiness`.
- Start audio/video calls through `POST /api/calls`.
- Answer, reject, hang up, and list call records through `/api/calls`.

## Build

Use Qt 5.9.3 for the delivery build:

```powershell
qmake qt-client.pro
nmake
```

or with a MinGW Qt kit:

```powershell
qmake qt-client.pro
mingw32-make
```

Local note: this workstation currently exposes Qt 5.15.2 qmake from Anaconda, but no configured `cl`, `g++`, `nmake`, or `mingw32-make`, so full exe build requires a Qt 5.9.3 kit plus compiler environment.

For the requested VS2017 + WebEngine delivery build:

```powershell
.\scripts\package-qt-vs2017-webengine.ps1 -QtRoot D:\Qt\5.9.3\msvc2017
```

Put PJSIP 2.10-2.14 Windows runtime files under
`qt-client\third_party\pjsip\windows` before final media package.

## Protocol Defaults

- Host: `127.0.0.1`
- HTTP port: `18080` for Docker Compose, or `8080` for direct local server.
- TCP port: `19090` for Docker Compose, or `9000` for direct local server.
- Token format: `demo-token-<userId>`
- Frame ending: newline `\n`
