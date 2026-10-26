from base_plugin import BasePlugin, HookResult, HookStrategy
import re
from urllib.parse import urlsplit, urlunsplit, parse_qsl, urlencode

__id__ = "clean_links"
__name__ = "Чистые ссылки"
__description__ = "Убирает utm_*, fbclid и gclid из ссылок в отправляемом тексте."
__author__ = "WGram"
__version__ = "1.0"
__wgram_api__ = 1


class CleanLinksPlugin(BasePlugin):
    def on_plugin_load(self):
        self.add_on_send_message_hook()

    def on_send_message_hook(self, account, params):
        def clean(match):
            original = match.group(0)
            url = urlsplit(original)
            pairs = parse_qsl(url.query, keep_blank_values=True)
            kept = [(key, value) for key, value in pairs
                    if not key.lower().startswith("utm_") and key.lower() not in {"fbclid", "gclid"}]
            if len(pairs) == len(kept):
                return original
            return urlunsplit((url.scheme, url.netloc, url.path, urlencode(kept), url.fragment))
        text = re.sub(r'https?://[^\s<>]+', clean, params.message)
        if text != params.message:
            params.message = text
            return HookResult(strategy=HookStrategy.MODIFY, params=params)
        return HookResult()
