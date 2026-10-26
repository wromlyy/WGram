"""Local opt-in plugins. Reading metadata never executes plugin code."""
import ast
import hashlib
import json
import logging
import os
from pathlib import Path
import re
import sys
import threading
import types

from base_plugin import BasePlugin, HookResult, HookStrategy

MAX_SOURCE = 1024 * 1024
IDENTIFIER = re.compile(r"[A-Za-z][A-Za-z0-9_-]{1,31}\Z")
_engine = None


def metadata(source):
    if len(source) > MAX_SOURCE:
        raise ValueError("Plugin is larger than 1 MB")
    tree = ast.parse(source.decode("utf-8-sig"))
    values = {}
    for node in tree.body:
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id.startswith("__"):
                    try:
                        values[target.id] = ast.literal_eval(node.value)
                    except (ValueError, TypeError):
                        raise ValueError("Plugin metadata must use literal values") from None
    plugin_id = values.get("__id__", "")
    name = values.get("__name__", "")
    if not isinstance(plugin_id, str) or not IDENTIFIER.fullmatch(plugin_id):
        raise ValueError("Invalid plugin ID (2–32 Latin letters, digits, _ or -)")
    if not isinstance(name, str) or not name.strip() or len(name) > 100:
        raise ValueError("Plugin name must contain 1–100 characters")
    if values.get("__requirements__"):
        raise ValueError("Installing Python dependencies is not supported")
    if values.get("__sdk_version__") or values.get("__app_version__") or values.get("__min_version__"):
        raise ValueError("This plugin requires the exteraGram SDK. Use a WGram API 1 plugin")
    if values.get("__wgram_api__", 1) != 1:
        raise ValueError("Unsupported WGram plugin API")
    result = {"id": plugin_id, "name": name, "version": "1.0", "author": "", "description": ""}
    for key, limit in (("version", 64), ("author", 200), ("description", 2000)):
        value = values.get("__" + key + "__", result[key])
        if not isinstance(value, str) or len(value) > limit:
            raise ValueError("Invalid plugin " + key)
        result[key] = value
    result["sha256"] = hashlib.sha256(source).hexdigest()
    return result


def _write_json(path, value):
    temporary = path.with_suffix(".tmp")
    with temporary.open("w", encoding="utf-8") as output:
        json.dump(value, output, ensure_ascii=False)
        output.flush()
        os.fsync(output.fileno())
    os.replace(temporary, path)


class Engine:
    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        self.running = {}
        self.master = False
        self.state_file = self.root / "state.json"
        self.marker = self.root / "loading.json"
        self.state = {"plugins": {}, "settings": {}}
        if self.state_file.exists():
            self.state = json.loads(self.state_file.read_text(encoding="utf-8"))
        if self.marker.exists():
            for info in self.state["plugins"].values():
                if info.get("enabled"):
                    info["enabled"] = False
                    info["error"] = "Stopped after an interrupted plugin callback. Enable manually."
            self.save()
            self.marker.unlink()

    def save(self):
        _write_json(self.state_file, self.state)

    def settings(self, plugin_id):
        with self.lock:
            return dict(self.state["settings"].get(plugin_id, {}))

    def set_setting(self, plugin_id, key, value):
        with self.lock:
            json.dumps(value)
            self.state["settings"].setdefault(plugin_id, {})[key] = value
            self.save()

    def _id(self, plugin_id):
        if not isinstance(plugin_id, str) or not IDENTIFIER.fullmatch(plugin_id):
            raise ValueError("Invalid plugin ID")
        return plugin_id

    def inspect(self, path):
        with open(path, "rb") as source:
            return metadata(source.read(MAX_SOURCE + 1))

    def new_source(self):
        from wgram_template import create
        return create()

    def inspect_source(self, source):
        if not isinstance(source, str):
            raise ValueError("Plugin source must be text")
        return metadata(source.encode("utf-8"))

    def source(self, plugin_id):
        with self.lock:
            self._id(plugin_id)
            with (self.root / (plugin_id + ".py")).open("rb") as input_file:
                source = input_file.read(MAX_SOURCE + 1)
            if metadata(source)["sha256"] != self.state["plugins"][plugin_id]["sha256"]:
                raise ValueError("Installed plugin changed. Reimport it.")
            return source.decode("utf-8-sig")

    def install(self, path, expected_hash):
        with open(path, "rb") as input_file:
            source = input_file.read(MAX_SOURCE + 1)
        return self._install(source, expected_hash)

    def install_source(self, source, expected_hash, original_id=None):
        if not isinstance(source, str):
            raise ValueError("Plugin source must be text")
        return self._install(source.encode("utf-8"), expected_hash, original_id)

    def _install(self, source, expected_hash, original_id=None):
        with self.lock:
            info = metadata(source)
            if info["sha256"] != expected_hash:
                raise ValueError("Plugin changed after confirmation")
            plugin_id = info["id"]
            if original_id is not None and plugin_id != self._id(original_id):
                raise ValueError("Keep the plugin ID when editing. Create a new plugin for a different ID.")
            self._unload(plugin_id)
            temporary = self.root / (plugin_id + ".pending")
            temporary.write_bytes(source)
            os.replace(temporary, self.root / (plugin_id + ".py"))
            self.state["plugins"][plugin_id] = dict(info, enabled=False, error="")
            self.save()
            return info

    def _unload(self, plugin_id):
        plugin = self.running.pop(plugin_id, None)
        if plugin:
            _write_json(self.marker, {"id": plugin_id})
            try:
                plugin.on_plugin_unload()
            except BaseException:
                logging.exception("Plugin unload failed: %s", plugin_id)
            finally:
                self.marker.unlink(missing_ok=True)
        sys.modules.pop("wgram_user_" + plugin_id, None)

    def _load(self, plugin_id):
        info = self.state["plugins"][plugin_id]
        _write_json(self.marker, {"id": plugin_id})
        try:
            source = (self.root / (plugin_id + ".py")).read_bytes()
            if metadata(source)["sha256"] != info["sha256"]:
                raise ValueError("Installed plugin changed. Reinstall it.")
            module = types.ModuleType("wgram_user_" + plugin_id)
            module.__file__ = str(self.root / (plugin_id + ".py"))
            sys.modules[module.__name__] = module
            exec(compile(source.decode("utf-8-sig"), module.__file__, "exec"), module.__dict__)
            classes = [v for v in module.__dict__.values() if isinstance(v, type)
                       and v is not BasePlugin and issubclass(v, BasePlugin)
                       and v.__module__ == module.__name__]
            if len(classes) != 1:
                raise ValueError("Define exactly one BasePlugin subclass")
            plugin = classes[0]()
            plugin.id = plugin_id
            plugin._engine = self
            plugin.logger = logging.getLogger("wgram.plugin." + plugin_id)
            self.running[plugin_id] = plugin
            plugin.on_plugin_load()
            info["error"] = ""
        except BaseException as error:
            self.running.pop(plugin_id, None)
            sys.modules.pop("wgram_user_" + plugin_id, None)
            info["enabled"] = False
            info["error"] = type(error).__name__ + ": " + str(error)[:500]
            logging.exception("Plugin load failed: %s", plugin_id)
        finally:
            self.marker.unlink(missing_ok=True)
            self.save()

    def set_master(self, enabled):
        with self.lock:
            self.master = bool(enabled)
            if not self.master:
                for plugin_id in list(self.running):
                    self._unload(plugin_id)
            else:
                for plugin_id, info in self.state["plugins"].items():
                    if info.get("enabled") and plugin_id not in self.running:
                        self._load(plugin_id)

    def enable(self, plugin_id, enabled):
        with self.lock:
            self._id(plugin_id)
            info = self.state["plugins"][plugin_id]
            self._unload(plugin_id)
            info["enabled"] = bool(enabled)
            info["error"] = ""
            self.save()
            if enabled and self.master:
                self._load(plugin_id)

    def remove(self, plugin_id):
        with self.lock:
            self._id(plugin_id)
            self._unload(plugin_id)
            self.state["plugins"].pop(plugin_id, None)
            self.state["settings"].pop(plugin_id, None)
            self.save()
            (self.root / (plugin_id + ".py")).unlink(missing_ok=True)

    def list_plugins(self):
        with self.lock:
            return sorted((dict(info, running=plugin_id in self.running)
                           for plugin_id, info in self.state["plugins"].items()),
                          key=lambda info: (info["name"].casefold(), info["id"]))

    def outgoing(self, account, peer, message):
        with self.lock:
            result = {"message": message, "cancel": False}
            if not self.master:
                return result
            hooks = sorted(((plugin_id, plugin) for plugin_id, plugin in self.running.items()
                            if plugin._send_priority is not None),
                           key=lambda pair: (-pair[1]._send_priority, pair[0]))
            for plugin_id, plugin in hooks:
                params = types.SimpleNamespace(message=result["message"], peer=peer)
                _write_json(self.marker, {"id": plugin_id})
                try:
                    hook = plugin.on_send_message_hook(account, params)
                    if hook is not None and not isinstance(hook, HookResult):
                        raise TypeError("Send hook must return HookResult or None")
                    if hook and hook.strategy == HookStrategy.CANCEL:
                        result["cancel"] = True
                        break
                    if hook and hook.strategy in (HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL):
                        updated = hook.params if hook.params is not None else params
                        if not isinstance(updated.message, str) or not 0 < len(updated.message.encode("utf-16-le")) // 2 <= 4096:
                            raise ValueError("Changed message must contain 1–4096 UTF-16 units")
                        result["message"] = updated.message
                        if hook.strategy == HookStrategy.MODIFY_FINAL:
                            break
                except BaseException as error:
                    info = self.state["plugins"][plugin_id]
                    info["enabled"] = False
                    info["error"] = type(error).__name__ + ": " + str(error)[:500]
                    self._unload(plugin_id)
                    self.save()
                    result["cancel"] = True
                    break
                finally:
                    self.marker.unlink(missing_ok=True)
            return result


def initialize(root, enabled):
    global _engine
    if _engine is None:
        _engine = Engine(root)
    _engine.set_master(enabled)


def call(operation, *args):
    return json.dumps(getattr(_engine, operation)(*args), ensure_ascii=False)
