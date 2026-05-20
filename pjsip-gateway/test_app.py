import os
import unittest
from unittest.mock import patch

import app


class GatewayEngineTest(unittest.TestCase):
    def setUp(self):
        self.session = app.MediaSession(
            callId="call_1",
            sessionId="pjsip_1",
            callerId="alice",
            calleeId="bob",
            conversationId="c1",
            mediaType="audio",
            turnSessionId="turn_1",
            status="ringing",
            engine="test",
            createdAt=1.0,
        )

    def test_render_command_uses_session_fields(self):
        command = app.render_command("dial ${callerId} ${calleeId} ${callId}", self.session)

        self.assertEqual(command, "dial alice bob call_1")

    def test_command_engine_starts_create_template(self):
        with patch.dict(os.environ, {"PJSIP_CREATE_CMD": "echo ${callId}"}, clear=False):
            engine = app.CommandEngine()
            with patch("subprocess.Popen") as popen:
                popen.return_value.pid = 123

                engine.create(self.session)

        self.assertEqual(self.session.processPid, 123)
        self.assertEqual(self.session.command, "echo call_1")

    def test_pjsua_engine_builds_outbound_call(self):
        with patch.dict(os.environ, {
            "PJSIP_SIP_DOMAIN": "sip.local",
            "PJSIP_PROCESS_START_GRACE_MS": "0",
            "PJSIP_USERNAME": "u_gateway",
        }, clear=False):
            engine = app.PjsuaEngine("/usr/bin/pjsua")
            with patch("subprocess.Popen") as popen:
                popen.return_value.pid = 456
                popen.return_value.poll.return_value = None

                engine.create(self.session)

        self.assertEqual(self.session.processPid, 456)
        self.assertIn("sip:u_gateway@sip.local", self.session.command)
        self.assertIn("sip:bob@sip.local", self.session.command)

    def test_select_engine_prefers_command_template(self):
        with patch.dict(os.environ, {"PJSIP_CREATE_CMD": "echo ok"}, clear=False):
            self.assertEqual(app.select_engine().name, "external-pjsip-command")


if __name__ == "__main__":
    unittest.main()
