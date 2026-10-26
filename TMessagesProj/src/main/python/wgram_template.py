"""Working starter plugin used by the in-app editor."""
import secrets


def create():
    return '''from base_plugin import BasePlugin, HookResult, HookStrategy

__id__ = "plugin_%s"
__name__ = "Мой плагин"
__description__ = "Добавляет подпись к сообщениям"
__author__ = ""
__version__ = "1.0"
__wgram_api__ = 1


class MyPlugin(BasePlugin):
    def on_plugin_load(self):
        self.add_on_send_message_hook()

    def on_send_message_hook(self, account, params):
        # Измени подпись или добавь свою обработку текста.
        params.message += " — WGram"
        return HookResult(strategy=HookStrategy.MODIFY, params=params)
''' % secrets.token_hex(4)
