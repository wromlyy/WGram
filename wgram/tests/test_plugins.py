import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "TMessagesProj/src/main/python"))
from wgram_plugins import Engine, metadata, MAX_SOURCE

SOURCE = b'''from base_plugin import BasePlugin, HookResult, HookStrategy
__id__ = "example"
__name__ = "Example"
class Example(BasePlugin):
    def on_plugin_load(self):
        self.add_on_send_message_hook()
        self.set_setting("loads", self.get_setting("loads", 0) + 1)
    def on_send_message_hook(self, account, params):
        params.message += " #" + str(account) + ":" + str(params.peer)
        return HookResult(HookStrategy.MODIFY, params)
'''


class PluginsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.engine = Engine(self.root / "plugins")
        self.source = self.root / "input.py"
        self.source.write_bytes(SOURCE)

    def tearDown(self):
        self.engine.set_master(False)
        self.temp.cleanup()

    def install(self, source=SOURCE):
        self.source.write_bytes(source)
        self.engine.install(self.source, hashlib.sha256(source).hexdigest())

    def enable(self):
        self.install()
        self.engine.set_master(True)
        self.engine.enable("example", True)

    def test_editor_template_runs_and_has_unique_ids(self):
        first, second = self.engine.new_source(), self.engine.new_source()
        info = self.engine.inspect_source(first)
        self.assertNotEqual(info["id"], self.engine.inspect_source(second)["id"])
        self.engine.install_source(first, info["sha256"])
        self.assertEqual(self.engine.source(info["id"]), first)
        self.assertFalse(self.engine.running)
        self.engine.set_master(True)
        self.engine.enable(info["id"], True)
        self.assertEqual(self.engine.outgoing(0, 123, "Hello")["message"], "Hello — WGram")

    def test_editor_validation_does_not_execute_and_reports_syntax_errors(self):
        source = SOURCE.decode() + '\nraise RuntimeError("Do not execute in editor")\n'
        info = self.engine.inspect_source(source)
        self.engine.install_source(source, info["sha256"])
        self.assertEqual(self.engine.source("example"), source)
        self.assertFalse(self.engine.running)
        with self.assertRaises(SyntaxError):
            self.engine.inspect_source(source + "class Broken(\n")
        with self.assertRaises(ValueError):
            self.engine.inspect_source(" " * (MAX_SOURCE + 1))

    def test_editor_save_rejects_changed_source_or_identity(self):
        self.enable()
        source = SOURCE.decode().replace('"Example"', '"Edited"')
        info = self.engine.inspect_source(source)
        with self.assertRaises(ValueError):
            self.engine.install_source(source + "\n", info["sha256"], "example")
        with self.assertRaises(ValueError):
            self.engine.install_source(source, info["sha256"], "other")
        self.assertIn("example", self.engine.running)
        self.engine.install_source(source, info["sha256"], "example")
        self.assertFalse(self.engine.running)
        self.assertFalse(self.engine.list_plugins()[0]["enabled"])
        self.assertEqual(self.engine.source("example"), source)
        self.assertEqual(self.engine.settings("example")["loads"], 1)

    def test_editor_source_rejects_escape_and_tampering(self):
        self.install()
        with self.assertRaises(ValueError):
            self.engine.source("../escape")
        (self.root / "plugins/example.py").write_bytes(SOURCE + b"\n# changed")
        with self.assertRaises(ValueError):
            self.engine.source("example")

    def test_metadata_and_install_never_execute(self):
        malicious = SOURCE + b'\nraise RuntimeError("must not run")\n'
        self.install(malicious)
        self.assertFalse(self.engine.list_plugins()[0]["enabled"])
        self.assertFalse(self.engine.running)
        self.assertFalse(self.engine.master)

    def test_invalid_id_cannot_escape_plugin_directory(self):
        for plugin_id in ["../escape", "/escape", "a", "x" * 33, "a/b", "аbc"]:
            with self.subTest(plugin_id=plugin_id), self.assertRaises(ValueError):
                metadata(SOURCE.replace(b'"example"', repr(plugin_id).encode()))
        with self.assertRaises(ValueError):
            self.engine.remove("../escape")

    def test_unsupported_sdk_and_dynamic_metadata_rejected(self):
        for suffix in [b'\n__sdk_version__ = ">=1.4.4.3"', b'\n__requirements__ = ["requests"]',
                       b'\n__wgram_api__ = 2', b'\n__name__ = str(123)']:
            with self.subTest(suffix=suffix), self.assertRaises(ValueError):
                metadata(SOURCE + suffix)

    def test_size_and_confirmation_hash(self):
        with self.assertRaises(ValueError):
            metadata(b" " * (MAX_SOURCE + 1))
        with self.assertRaises(ValueError):
            self.engine.install(self.source, "wrong")
        self.assertFalse(self.engine.list_plugins())

    def test_explicit_enable_and_outgoing_account_peer(self):
        self.install()
        self.engine.set_master(True)
        self.assertEqual(self.engine.outgoing(2, 9223372036854770000, "Hi")["message"], "Hi")
        self.engine.enable("example", True)
        self.assertEqual(self.engine.outgoing(2, 9223372036854770000, "Hi"),
                         {"message": "Hi #2:9223372036854770000", "cancel": False})
        self.engine.enable("example", False)
        self.assertEqual(self.engine.outgoing(2, 1, "Hi")["message"], "Hi")

    def test_settings_persist_and_master_suspends(self):
        self.enable()
        self.assertEqual(self.engine.settings("example")["loads"], 1)
        self.engine.set_master(False)
        self.assertFalse(self.engine.running)
        self.assertTrue(self.engine.list_plugins()[0]["enabled"])
        self.engine = Engine(self.root / "plugins")
        self.assertEqual(self.engine.settings("example")["loads"], 1)
        self.engine.set_master(True)
        self.assertEqual(self.engine.settings("example")["loads"], 2)

    def test_update_needs_reenable_remove_cleans_settings(self):
        self.enable()
        self.install(SOURCE.replace(b'"Example"', b'"Updated"'))
        self.assertFalse(self.engine.running)
        self.assertFalse(self.engine.list_plugins()[0]["enabled"])
        self.engine.remove("example")
        self.assertFalse(self.engine.list_plugins())
        self.assertFalse(self.engine.settings("example"))
        self.assertFalse((self.root / "plugins/example.py").exists())

    def test_crashing_load_disabled(self):
        self.install(SOURCE + b'\nraise SystemExit("bad")\n')
        self.engine.set_master(True)
        self.engine.enable("example", True)
        info = self.engine.list_plugins()[0]
        self.assertFalse(info["enabled"])
        self.assertIn("SystemExit", info["error"])
        self.assertFalse(self.engine.running)

    def test_crashing_send_cancels_and_rolls_back(self):
        self.install(SOURCE.replace(b'params.message +=', b'raise RuntimeError("oops"); params.message +='))
        self.engine.set_master(True)
        self.engine.enable("example", True)
        result = self.engine.outgoing(0, 1, "private")
        self.assertTrue(result["cancel"])
        self.assertEqual(result["message"], "private")
        self.assertFalse(self.engine.list_plugins()[0]["enabled"])

    def test_modified_file_not_executed(self):
        self.install()
        (self.root / "plugins/example.py").write_bytes(SOURCE + b'\nraise RuntimeError("tampered")')
        self.engine.set_master(True)
        self.engine.enable("example", True)
        info = self.engine.list_plugins()[0]
        self.assertFalse(info["enabled"])
        self.assertIn("changed", info["error"])

    def test_interrupted_callback_recovery(self):
        self.enable()
        (self.root / "plugins/loading.json").write_text('{"id":"example"}')
        self.engine = Engine(self.root / "plugins")
        self.engine.set_master(True)
        self.assertFalse(self.engine.running)
        self.assertFalse(self.engine.list_plugins()[0]["enabled"])
        self.assertIn("interrupted", self.engine.list_plugins()[0]["error"])

    def test_priority_and_modify_final(self):
        self.enable()
        other = SOURCE.replace(b'"example"', b'"other"').replace(b'self.add_on_send_message_hook()', b'self.add_on_send_message_hook(priority=100)').replace(b'HookStrategy.MODIFY', b'HookStrategy.MODIFY_FINAL')
        self.install(other)
        self.engine.enable("other", True)
        self.assertEqual(self.engine.outgoing(1, 2, "Hi")["message"], "Hi #1:2")

    def test_utf16_limit(self):
        self.install(SOURCE.replace(b'params.message += " #" + str(account) + ":" + str(params.peer)', b'params.message = "\\U0001f600" * 2049'))
        self.engine.set_master(True)
        self.engine.enable("example", True)
        self.assertTrue(self.engine.outgoing(0, 1, "Hi")["cancel"])

    def test_shipped_clean_links_plugin(self):
        self.install((ROOT / "wgram/plugins/clean_links.py").read_bytes())
        self.engine.set_master(True)
        self.engine.enable("clean_links", True)
        self.assertEqual(self.engine.outgoing(0, 1, "https://example.org/a?x=1&utm_source=tg&fbclid=id#anchor")["message"], "https://example.org/a?x=1#anchor")
        self.assertEqual(self.engine.outgoing(0, 1, "https://example.org/a?q=hello%20there")["message"], "https://example.org/a?q=hello%20there")


if __name__ == "__main__":
    unittest.main()
