"""WGram plugin API 1.0. Only the documented API is supported."""
import logging
from dataclasses import dataclass
from enum import Enum


class HookStrategy(Enum):
    DEFAULT = 0
    CANCEL = 1
    MODIFY = 2
    MODIFY_FINAL = 3


@dataclass
class HookResult:
    strategy: HookStrategy = HookStrategy.DEFAULT
    params: object = None


class BasePlugin:
    def __init__(self):
        self._engine = None
        self.id = None
        self._send_priority = None
        self.logger = logging.getLogger("wgram.plugin")

    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    def add_on_send_message_hook(self, priority=0):
        self._send_priority = int(priority)

    def on_send_message_hook(self, account, params):
        return HookResult()

    def get_setting(self, key, default=None):
        return self._engine.settings(self.id).get(key, default)

    def set_setting(self, key, value, reload_settings=False):
        self._engine.set_setting(self.id, key, value)

    def log(self, message):
        self.logger.info("%s", message)
