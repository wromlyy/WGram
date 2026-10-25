import base64
from io import BytesIO
import json
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
import urllib.error
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import bot

JPEG = b'\xff\xd8\xfftest\xff\xd9'


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.old_db = bot.DB
        bot.DB = Path(self.temp.name) / 'test.sqlite'
        bot.BOT.update(id=999, username='WGramTestBot')
        self.server = bot.Server(('127.0.0.1', 0), bot.Handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.url = 'http://127.0.0.1:' + str(self.server.server_port)
        self.mock_api = patch.object(bot, 'api', return_value={'file_path': 'photos/banner.jpg'}).start()

    def tearDown(self):
        patch.stopall()
        self.server.shutdown()
        self.server.server_close()
        bot.DB = self.old_db
        self.temp.cleanup()

    def request(self, path, data=None, token=None, raw=None):
        body = raw if raw is not None else json.dumps(data).encode() if data is not None else None
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request(self.url+path, data=body, headers=headers)
        try:
            response = urllib.request.urlopen(req, timeout=3)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            payload = response.read()
            return response.status, payload if response.headers['Content-Type'] == 'image/jpeg' else json.loads(payload)

    def message(self, sender, text='', **extra):
        return dict({'from': {'id': sender}, 'chat': {'id': sender, 'type': 'private'},
            'date': int(time.time()), 'text': text}, **extra)

    def connect(self, user):
        status, pair = self.request('/v1/auth/start', {'uid': user})
        self.assertEqual(status, 200)
        bot.handle_bot_message(self.message(user, '/start wgram_'+pair['id']))
        status, result = self.request('/v1/auth/check', pair)
        self.assertEqual(status, 200)
        self.assertEqual(result['uid'], user)
        return result['token']

    def test_pairing_requires_sender_and_secret_and_is_one_use(self):
        status, pair = self.request('/v1/auth/start', {'uid': 100})
        self.assertEqual(status, 200)
        bot.handle_bot_message(self.message(101, '/start wgram_'+pair['id']))
        self.assertEqual(self.request('/v1/auth/check', pair)[1], {'pending': True})
        bot.handle_bot_message(self.message(100, '/start wgram_'+pair['id'], forward_origin={}))
        self.assertEqual(self.request('/v1/auth/check', pair)[1], {'pending': True})
        bot.handle_bot_message(self.message(100, '/start wgram_'+pair['id']))
        self.assertEqual(self.request('/v1/auth/check', dict(pair, proof='forged'))[0], 401)
        self.assertIn('token', self.request('/v1/auth/check', pair)[1])
        self.assertEqual(self.request('/v1/auth/check', pair)[0], 401)

    def test_expired_pair_and_expired_or_revoked_session(self):
        _, pair = self.request('/v1/auth/start', {'uid': 100})
        with bot.database() as db:
            db.execute('UPDATE pairs SET expires=0')
        self.assertEqual(self.request('/v1/auth/check', pair)[0], 401)
        token = self.connect(100)
        with bot.database() as db:
            self.assertNotEqual(db.execute('SELECT hash FROM sessions').fetchone()[0], token)
            db.execute('UPDATE sessions SET expires=0')
        self.assertEqual(self.request('/v1/profiles/100', token=token)[0], 401)
        token = self.connect(100)
        self.assertEqual(self.request('/v1/auth/logout', {}, token)[0], 200)
        self.assertEqual(self.request('/v1/profiles/100', token=token)[0], 401)

    def test_profile_isolation_badge_permissions_and_delete(self):
        token = self.connect(100)
        other = self.connect(101)
        self.assertEqual(self.request('/v1/me/profile', {'uid': 101, 'emoji': 42, 'banner': base64.b64encode(JPEG).decode()}, token)[0], 200)
        self.assertEqual(self.request('/v1/profiles/100', token=other)[1]['emoji'], 42)
        self.assertEqual(self.request('/v1/profiles/101', token=other)[1]['emoji'], 0)
        badge = {'uid': 100, 'active': True, 'emoji': 99, 'title': 'Помощь', 'description': 'За помощь проекту'}
        self.assertEqual(self.request('/v1/admin/badge', badge, token)[0], 403)
        admin = self.connect(bot.ADMIN)
        self.assertEqual(self.request('/v1/admin/badge', badge, admin)[0], 200)
        self.assertEqual(self.request('/v1/profiles/100', token=other)[1]['badge']['title'], 'Помощь')
        self.assertEqual(self.request('/v1/me/delete', {}, token)[0], 200)
        result = self.request('/v1/profiles/100', token=other)[1]
        self.assertFalse(result['banner'])
        self.assertEqual(result['emoji'], 0)
        self.assertTrue(result['badge']['active'])  # Users cannot erase an admin award by updating their own profile.
        self.assertEqual(self.request('/v1/profiles/100/banner', token=other)[0], 404)

    def test_photo_in_bot_and_api_upload_are_bounded(self):
        token = self.connect(100)
        with patch.object(bot.urllib.request, 'urlopen', return_value=BytesIO(JPEG)):
            bot.handle_bot_message(self.message(100, photo=[{'file_id': 'abc', 'file_size': len(JPEG)}]))
        self.assertEqual(self.request('/v1/profiles/100/banner', token=token), (200, JPEG))
        status, _ = self.request('/v1/me/profile', {'emoji': 123, 'banner': 'not base64'}, token)
        self.assertEqual(status, 400)
        self.assertEqual(self.request('/v1/profiles/100', token=token)[1]['emoji'], 0)
        huge = base64.b64encode(b'\xff\xd8\xff'+b'x'*bot.MAX_BANNER+b'\xff\xd9').decode()
        self.assertEqual(self.request('/v1/me/profile', {'banner': huge}, token)[0], 400)
        self.assertEqual(self.request('/v1/profiles/100/banner', token=token)[1], JPEG)
        bot.handle_bot_message(self.message(100, '/delete'))
        self.assertFalse(self.request('/v1/profiles/100', token=token)[1]['banner'])

    def test_restart_keeps_profile_and_authenticated_session(self):
        token = self.connect(100)
        self.request('/v1/me/profile', {'emoji': 456}, token)
        self.server.shutdown()
        self.server.server_close()
        self.server = bot.Server(('127.0.0.1', 0), bot.Handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.url = 'http://127.0.0.1:' + str(self.server.server_port)
        self.assertEqual(self.request('/health')[1], {'service': 'WGram', 'ok': True})
        self.assertEqual(self.request('/v1/profiles/100', token=token)[1]['emoji'], 456)

    def test_malformed_and_unauthenticated_requests(self):
        self.assertEqual(self.request('/v1/profiles/100')[0], 401)
        self.assertEqual(self.request('/v1/auth/start', raw=b'[]')[0], 400)
        self.assertEqual(self.request('/v1/auth/start', raw=b'{')[0], 400)
        for invalid in [0, -1, True, 2**63]:
            self.assertEqual(self.request('/v1/auth/start', {'uid': invalid})[0], 400)
        for _ in range(8):
            self.assertEqual(self.request('/v1/auth/start', {'uid': 100})[0], 200)
        self.assertEqual(self.request('/v1/auth/start', {'uid': 100})[0], 429)


if __name__ == '__main__':
    unittest.main()
