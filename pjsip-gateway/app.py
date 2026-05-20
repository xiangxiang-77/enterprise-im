from __future__ import annotations

import json
import os
import shutil
import subprocess
import time
import uuid
from dataclasses import asdict, dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from string import Template
from typing import Any
from urllib.parse import urlparse


@dataclass
class MediaSession:
    callId: str
    sessionId: str
    callerId: str
    calleeId: str
    conversationId: str
    mediaType: str
    turnSessionId: str
    status: str
    engine: str
    createdAt: float
    answeredAt: float | None = None
    endedAt: float | None = None
    processPid: int | None = None
    command: str | None = None
    error: str | None = None


class GatewayEngine:
    name = "simulated-pjsip"

    def health(self) -> dict[str, Any]:
        return {"available": True}

    def create(self, session: MediaSession) -> None:
        return None

    def answer(self, session: MediaSession) -> None:
        return None

    def hangup(self, session: MediaSession) -> None:
        return None


class SimulatedEngine(GatewayEngine):
    name = "simulated-pjsip"


class CommandEngine(GatewayEngine):
    name = "external-pjsip-command"

    def __init__(self) -> None:
        self.create_template = os.environ.get("PJSIP_CREATE_CMD", "").strip()
        self.answer_template = os.environ.get("PJSIP_ANSWER_CMD", "").strip()
        self.hangup_template = os.environ.get("PJSIP_HANGUP_CMD", "").strip()

    def health(self) -> dict[str, Any]:
        return {
            "available": bool(self.create_template),
            "createTemplate": bool(self.create_template),
            "answerTemplate": bool(self.answer_template),
            "hangupTemplate": bool(self.hangup_template),
        }

    def create(self, session: MediaSession) -> None:
        if not self.create_template:
            raise RuntimeError("PJSIP_CREATE_CMD is not configured")
        command = render_command(self.create_template, session)
        process = subprocess.Popen(command, shell=True)
        session.processPid = process.pid
        session.command = command

    def answer(self, session: MediaSession) -> None:
        if self.answer_template:
            run_command(render_command(self.answer_template, session))

    def hangup(self, session: MediaSession) -> None:
        if self.hangup_template:
            run_command(render_command(self.hangup_template, session))
        elif session.processPid:
            terminate_pid(session.processPid)


class PjsuaEngine(GatewayEngine):
    name = "pjsua-adapter"

    def __init__(self, pjsua_bin: str) -> None:
        self.pjsua_bin = pjsua_bin
        self.sip_domain = os.environ.get("PJSIP_SIP_DOMAIN", "127.0.0.1").strip()
        self.registrar = os.environ.get("PJSIP_REGISTRAR", "").strip()
        self.realm = os.environ.get("PJSIP_REALM", "*").strip()
        self.username = os.environ.get("PJSIP_USERNAME", "").strip()
        self.password = os.environ.get("PJSIP_PASSWORD", "").strip()
        self.extra_args = os.environ.get("PJSUA_EXTRA_ARGS", "").strip()

    def health(self) -> dict[str, Any]:
        return {
            "available": True,
            "pjsuaBin": self.pjsua_bin,
            "sipDomain": self.sip_domain,
            "registrarConfigured": bool(self.registrar),
            "usernameConfigured": bool(self.username),
        }

    def create(self, session: MediaSession) -> None:
        target = os.environ.get("PJSIP_TARGET_TEMPLATE", "sip:${calleeId}@${sipDomain}")
        target_uri = Template(target).safe_substitute(**template_values(session, self.sip_domain))
        account_user = self.username or session.callerId
        command = [
            self.pjsua_bin,
            "--id",
            f"sip:{account_user}@{self.sip_domain}",
            "--no-cli-console",
        ]
        if self.registrar:
            command += ["--registrar", self.registrar]
        if self.username:
            command += ["--realm", self.realm, "--username", self.username, "--password", self.password]
        if self.extra_args:
            command += self.extra_args.split()
        command.append(target_uri)
        process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(int(os.environ.get("PJSIP_PROCESS_START_GRACE_MS", "300")) / 1000)
        if process.poll() not in (None, 0):
            raise RuntimeError(f"pjsua exited with code {process.returncode}")
        session.processPid = process.pid
        session.command = " ".join(command)

    def hangup(self, session: MediaSession) -> None:
        if session.processPid:
            terminate_pid(session.processPid)


def select_engine() -> GatewayEngine:
    if os.environ.get("PJSIP_CREATE_CMD", "").strip():
        return CommandEngine()
    pjsua_bin = resolve_pjsua_bin()
    if pjsua_bin:
        return PjsuaEngine(pjsua_bin)
    return SimulatedEngine()


def resolve_pjsua_bin() -> str | None:
    configured = os.environ.get("PJSUA_BIN", "").strip()
    if configured:
        if os.path.exists(configured):
            return configured
        found = shutil.which(configured)
        if found:
            return found
        return None
    return shutil.which("pjsua")


def template_values(session: MediaSession, sip_domain: str = "") -> dict[str, Any]:
    values = asdict(session)
    values["sipDomain"] = sip_domain
    return values


def render_command(template: str, session: MediaSession) -> str:
    return Template(template).safe_substitute(**template_values(session, os.environ.get("PJSIP_SIP_DOMAIN", "")))


def run_command(command: str) -> None:
    subprocess.run(command, shell=True, check=True, timeout=int(os.environ.get("PJSIP_CMD_TIMEOUT_SECONDS", "10")))


def terminate_pid(pid: int) -> None:
    try:
        subprocess.run(["kill", "-TERM", str(pid)], check=False, timeout=3)
    except Exception:
        pass


sessions: dict[str, MediaSession] = {}
engine = select_engine()


class Handler(BaseHTTPRequestHandler):
    server_version = "EnterpriseIMPjsipGateway/0.2"

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path == "/health":
            self.write_json(200, {"status": "UP", "engine": engine.name, "engineHealth": engine.health(), "sessions": len(sessions)})
            return
        if path == "/api/pjsip/sessions":
            self.write_json(200, {"items": [asdict(session) for session in sessions.values()]})
            return
        self.write_json(404, {"error": "not found"})

    def do_POST(self) -> None:
        path = urlparse(self.path).path
        try:
            body = self.read_json()
            if path == "/api/pjsip/calls":
                self.create_session(body)
                return
            if path.startswith("/api/pjsip/calls/") and path.endswith("/answer"):
                call_id = path.split("/")[-2]
                self.transition(call_id, "answered")
                return
            if path.startswith("/api/pjsip/calls/") and path.endswith("/hangup"):
                call_id = path.split("/")[-2]
                self.transition(call_id, "ended")
                return
            self.write_json(404, {"error": "not found"})
        except ValueError as exc:
            self.write_json(400, {"error": str(exc)})
        except Exception as exc:
            self.write_json(502, {"error": f"{exc.__class__.__name__}: {exc}"})

    def create_session(self, body: dict[str, Any]) -> None:
        call_id = required(body, "callId")
        session = MediaSession(
            callId=call_id,
            sessionId="pjsip_" + str(uuid.uuid4()),
            callerId=required(body, "callerId"),
            calleeId=required(body, "calleeId"),
            conversationId=required(body, "conversationId"),
            mediaType=body.get("mediaType") or "audio",
            turnSessionId=body.get("turnSessionId") or "",
            status="ringing",
            engine=engine.name,
            createdAt=time.time(),
        )
        try:
            engine.create(session)
        except Exception as exc:
            session.error = f"{exc.__class__.__name__}: {exc}"
            sessions[call_id] = session
            raise
        sessions[call_id] = session
        self.write_json(200, response(session))

    def transition(self, call_id: str, status: str) -> None:
        session = sessions.get(call_id)
        if session is None:
            self.write_json(404, {"error": "session not found"})
            return
        if status == "answered":
            engine.answer(session)
            session.answeredAt = time.time()
        if status == "ended":
            engine.hangup(session)
            session.endedAt = time.time()
        session.status = status
        self.write_json(200, response(session))

    def read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0:
            return {}
        raw = self.rfile.read(length).decode("utf-8")
        return json.loads(raw)

    def write_json(self, status: int, body: dict[str, Any]) -> None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, format: str, *args: Any) -> None:
        print("%s - %s" % (self.address_string(), format % args))


def required(body: dict[str, Any], name: str) -> str:
    value = body.get(name)
    if value is None or str(value).strip() == "":
        raise ValueError(f"{name} is required")
    return str(value)


def response(session: MediaSession) -> dict[str, Any]:
    return {
        "sessionId": session.sessionId,
        "callId": session.callId,
        "status": session.status,
        "engine": session.engine,
        "mediaType": session.mediaType,
        "turnSessionId": session.turnSessionId,
        "processPid": session.processPid,
        "error": session.error,
    }


def main() -> None:
    port = int(os.environ.get("PJSIP_GATEWAY_PORT", "7070"))
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"pjsip-gateway listening on 0.0.0.0:{port}, engine={engine.name}")
    server.serve_forever()


if __name__ == "__main__":
    main()
