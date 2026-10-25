"""WGram profiles + Telegram bot. Python 3.11+, standard library only."""
import base64
from contextlib import contextmanager
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import sqlite3
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ADMIN = 8226881959
MAX_BANNER = 1_500_000
MAX_BODY = 2_100_000
MAX_BADGES = 3
TOKEN = os.environ.get('BOT_TOKEN', '')
DATA = Path(os.environ.get('WGRAM_DATA_DIR', 'data'))
DB = DATA / 'wgram.sqlite3'
GUARD = threading.RLock()
BOT = {'id': 0, 'username': ''}


@contextmanager
def database():
    DB.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB, timeout=20)
    conn.row_factory = sqlite3.Row
    try:
        conn.executescript('''
            CREATE TABLE IF NOT EXISTS pairs(id TEXT PRIMARY KEY,proof TEXT,uid INTEGER,expires INTEGER,approved INTEGER DEFAULT 0);
            CREATE TABLE IF NOT EXISTS sessions(hash TEXT PRIMARY KEY,uid INTEGER,expires INTEGER);
            CREATE TABLE IF NOT EXISTS profiles(uid INTEGER PRIMARY KEY,payload TEXT,updated INTEGER);
            CREATE TABLE IF NOT EXISTS banners(uid INTEGER PRIMARY KEY,data BLOB);
            CREATE TABLE IF NOT EXISTS badges(uid INTEGER PRIMARY KEY,payload TEXT);
            CREATE TABLE IF NOT EXISTS state(name TEXT PRIMARY KEY,value TEXT);
        ''')
        with conn:
            yield conn
    finally:
        conn.close()


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def api(method, payload):
    request = urllib.request.Request('https://api.telegram.org/bot' + TOKEN + '/' + method,
        data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=40) as response:
        result = json.load(response)
    if not result.get('ok'):
        raise RuntimeError('Telegram API request failed')
    return result['result']


def uid(value):
    if isinstance(value, bool):
        raise ValueError('Invalid user')
    value = int(value)
    if value <= 0 or value >= 2**63:
        raise ValueError('Invalid user')
    return value


def clean_profile(data):
    return {'premium': bool(data.get('premium')), 'emoji': max(0, min(2**63-1, int(data.get('emoji', 0)))),
        'color': max(-1, min(100, int(data.get('color', -1))))}


def clean_badge(data):
    active = bool(data.get('active'))
    result = {'active': active, 'emoji': max(0, min(2**63-1, int(data.get('emoji', 0)))),
        'title': str(data.get('title', ''))[:40].strip(), 'description': str(data.get('description', ''))[:300].strip()}
    if active and (not result['title'] or not result['emoji']):
        raise ValueError('Badge title and emoji are required')
    return result


def read_badges(db, target):
    row = db.execute('SELECT payload FROM badges WHERE uid=?', (target,)).fetchone()
    if not row:
        return []
    saved = json.loads(row[0])
    # Preserve awards made by the old single-badge service.
    values = saved if isinstance(saved, list) else [saved]
    result = []
    for value in values[:MAX_BADGES]:
        badge = clean_badge(value)
        if badge['active']:
            badge['id'] = digest(str(badge['emoji']) + ':' + badge['title'])
            result.append(badge)
    return result


def banner_bytes(image):
    if len(image) > MAX_BANNER or not image.startswith(b'\xff\xd8\xff') or not image.endswith(b'\xff\xd9'):
        raise ValueError('Invalid JPEG banner')
    return image


def handle_bot_message(message):
    sender = message.get('from', {}).get('id', 0)
    if not sender or message.get('chat', {}).get('type') != 'private' or message['chat']['id'] != sender:
        return
    if any(key in message for key in ('forward_origin', 'forward_from', 'forward_from_chat')):
        return
    if not time.time()-600 <= message.get('date', 0) <= time.time()+60:
        return
    text = message.get('text', '')
    reply = None
    if text.startswith('/start wgram_'):
        pairing = text.split('wgram_', 1)[1].strip()
        with GUARD, database() as db:
            row = db.execute('SELECT * FROM pairs WHERE id=?', (pairing,)).fetchone()
            if row and row['uid'] == sender and row['expires'] > time.time():
                db.execute('UPDATE pairs SET approved=1 WHERE id=?', (pairing,))
                reply = 'WGram подключён. Вернись в приложение. Пароли и коды Telegram боту не нужны.'
        if reply:
            api('sendMessage', {'chat_id': sender, 'text': reply})
        return
    if text == '/delete':
        with GUARD, database() as db:
            db.execute('DELETE FROM profiles WHERE uid=?', (sender,))
            db.execute('DELETE FROM banners WHERE uid=?', (sender,))
        reply = 'Оформление и баннер удалены из сервиса WGram. Сообщения боту можно удалить в Telegram.'
    elif message.get('photo'):
        photo = max(message['photo'], key=lambda p: p.get('file_size', 0))
        if photo.get('file_size', MAX_BANNER+1) > MAX_BANNER:
            reply = 'Выбери баннер размером до 1,5 МБ.'
        else:
            info = api('getFile', {'file_id': photo['file_id']})
            path = info['file_path']
            if not isinstance(path, str) or path.startswith('/') or '..' in path.split('/'):
                raise ValueError('Invalid file path')
            with urllib.request.urlopen('https://api.telegram.org/file/bot' + TOKEN + '/' + path, timeout=20) as response:
                image = response.read(MAX_BANNER+1)
            try:
                banner_bytes(image)
            except ValueError:
                reply = 'Не удалось прочитать баннер. Отправь фото до 1,5 МБ.'
            else:
                with GUARD, database() as db:
                    db.execute('INSERT OR REPLACE INTO banners VALUES(?,?)', (sender, image))
                reply = 'Баннер сохранён для твоего профиля WGram. Он доступен подключённым пользователям WGram. Публичной группы нет. /delete — удалить оформление.'
    else:
        reply = 'Сервис профилей WGram. Подключись из настроек мода, затем отправь сюда фото для баннера. /delete — удалить оформление. Новости: @WGramnews. Коды входа не присылай.'
    api('sendMessage', {'chat_id': sender, 'text': reply})


def poll():
    with database() as db:
        row = db.execute("SELECT value FROM state WHERE name='offset'").fetchone()
        offset = int(row[0]) if row else 0
    while True:
        try:
            updates = api('getUpdates', {'offset': offset, 'timeout': 25, 'allowed_updates': ['message']})
            for update in updates:
                try:
                    if 'message' in update:
                        handle_bot_message(update['message'])
                except Exception:
                    # A malformed upload must not permanently block the update queue.
                    print('Bot message could not be processed.', flush=True)
                offset = update['update_id'] + 1
                with GUARD, database() as db:
                    db.execute("INSERT OR REPLACE INTO state VALUES('offset',?)", (str(offset),))
        except Exception:
            # urllib exceptions can contain the bot token in the URL.
            print('Bot request failed; retrying.', flush=True)
            time.sleep(5)


def dispatch(db, method, path, data, auth):
    now = int(time.time())
    db.execute('DELETE FROM pairs WHERE expires<=?', (now,))
    db.execute('DELETE FROM sessions WHERE expires<=?', (now,))
    if method == 'GET' and path in ('/', '/health'):
        return 200, {'service': 'WGram', 'ok': True}
    if method == 'GET' and path == '/v1/config':
        return 200, {'bot_id': BOT['id'], 'bot_username': BOT['username']}
    if method == 'POST' and path == '/v1/auth/start':
        user = uid(data['uid'])
        if (db.execute('SELECT COUNT(*) FROM pairs').fetchone()[0] >= 1000 or
                db.execute('SELECT COUNT(*) FROM pairs WHERE uid=?', (user,)).fetchone()[0] >= 8):
            return 429, {'error': 'TRY_LATER'}
        pairing, proof = secrets.token_urlsafe(24), secrets.token_urlsafe(32)
        db.execute('INSERT INTO pairs VALUES(?,?,?,?,0)', (pairing, digest(proof), user, now+300))
        return 200, {'id': pairing, 'proof': proof, 'bot_id': BOT['id'], 'bot_username': BOT['username']}
    if method == 'POST' and path == '/v1/auth/check':
        pair = db.execute('SELECT * FROM pairs WHERE id=?', (str(data['id']),)).fetchone()
        if not pair or not hmac.compare_digest(pair['proof'], digest(str(data['proof']))):
            return 401, {'error': 'PAIR_EXPIRED'}
        if not pair['approved']:
            return 200, {'pending': True}
        token = secrets.token_urlsafe(32)
        # Bound sessions per account; oldest connections expire first.
        db.execute('DELETE FROM sessions WHERE hash IN (SELECT hash FROM sessions WHERE uid=? ORDER BY expires DESC LIMIT -1 OFFSET 7)', (pair['uid'],))
        db.execute('INSERT INTO sessions VALUES(?,?,?)', (digest(token), pair['uid'], now+30*86400))
        db.execute('DELETE FROM pairs WHERE id=?', (pair['id'],))
        return 200, {'token': token, 'uid': pair['uid']}
    session = db.execute('SELECT uid FROM sessions WHERE hash=?', (digest(auth[7:]),)).fetchone() if auth.startswith('Bearer ') else None
    if not session:
        return 401, {'error': 'CONNECT_BOT_FIRST'}
    user = session['uid']
    if method == 'POST' and path == '/v1/auth/logout':
        db.execute('DELETE FROM sessions WHERE hash=?', (digest(auth[7:]),))
        return 200, {'ok': True}
    if method == 'GET' and path.startswith('/v1/profiles/'):
        parts = path.split('/')
        target = uid(parts[3])
        if len(parts) == 5 and parts[4] == 'banner':
            row = db.execute('SELECT data FROM banners WHERE uid=?', (target,)).fetchone()
            return (200, bytes(row[0])) if row else (404, {'error': 'NO_BANNER'})
        if len(parts) != 4:
            return 404, {'error': 'NOT_FOUND'}
        row = db.execute('SELECT payload FROM profiles WHERE uid=?', (target,)).fetchone()
        profile = json.loads(row[0]) if row else clean_profile({})
        badges = read_badges(db, target)
        profile.update({'uid': target, 'banner': db.execute('SELECT 1 FROM banners WHERE uid=?', (target,)).fetchone() is not None,
            'badges': badges, 'badge': badges[0] if badges else {'active': False}})
        return 200, profile
    if method == 'POST' and path == '/v1/me/profile':
        profile = clean_profile(data)
        if 'banner' in data:
            encoded = data['banner']
            if encoded is None or encoded == '':
                db.execute('DELETE FROM banners WHERE uid=?', (user,))
            else:
                image = banner_bytes(base64.b64decode(encoded, validate=True))
                db.execute('INSERT OR REPLACE INTO banners VALUES(?,?)', (user, image))
        db.execute('INSERT OR REPLACE INTO profiles VALUES(?,?,?)', (user, json.dumps(profile), now))
        return 200, {'ok': True}
    if method == 'POST' and path == '/v1/admin/badge':
        if user != ADMIN:
            return 403, {'error': 'OWNER_ONLY'}
        target = uid(data['uid'])
        badge = clean_badge(data)
        badges = read_badges(db, target)
        if badge['active']:
            badge['id'] = digest(str(badge['emoji']) + ':' + badge['title'])
            index = next((i for i, saved in enumerate(badges) if saved['id'] == badge['id']), None)
            if index is not None:
                badges[index] = badge
            elif len(badges) >= MAX_BADGES:
                return 409, {'error': 'BADGE_LIMIT'}
            else:
                badges.append(badge)
        elif data.get('id'):
            badges = [saved for saved in badges if saved['id'] != data['id']]
        else:
            badges = []  # Old clients used this as clear-all.
        db.execute('INSERT OR REPLACE INTO badges VALUES(?,?)', (target, json.dumps(badges)))
        return 200, {'ok': True}
    if method == 'POST' and path == '/v1/me/delete':
        db.execute('DELETE FROM profiles WHERE uid=?', (user,))
        db.execute('DELETE FROM banners WHERE uid=?', (user,))
        return 200, {'ok': True}
    return 404, {'error': 'NOT_FOUND'}


class Handler(BaseHTTPRequestHandler):
    server_version = 'WGram/1'

    def log_message(self, *_):
        pass  # No bearer tokens, request bodies, names, or photos in access logs.

    def setup(self):
        super().setup()
        self.connection.settimeout(20)

    def respond(self, status, data):
        binary = isinstance(data, bytes)
        raw = data if binary else json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'image/jpeg' if binary else 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(raw)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        self.route()

    def do_POST(self):
        self.route()

    def route(self):
        try:
            length = int(self.headers.get('Content-Length', 0))
            if length < 0 or length > MAX_BODY:
                return self.respond(413, {'error': 'REQUEST_TOO_LARGE'})
            if self.headers.get('Transfer-Encoding'):
                return self.respond(400, {'error': 'BAD_REQUEST'})
            data = json.loads(self.rfile.read(length)) if length else {}
            if not isinstance(data, dict):
                raise ValueError('Expected JSON object')
            # Commit before sending the response; do not hold a DB lock while writing sockets.
            with GUARD, database() as db:
                status, payload = dispatch(db, self.command, self.path, data, self.headers.get('Authorization', ''))
        except (ValueError, KeyError, TypeError, OverflowError, IndexError):
            status, payload = 400, {'error': 'BAD_REQUEST'}
        except Exception:
            status, payload = 503, {'error': 'SERVICE_UNAVAILABLE'}
        try:
            self.respond(status, payload)
        except (OSError, TimeoutError):
            pass


class Server(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 32

    def __init__(self, *args, **kwargs):
        self.slots = threading.BoundedSemaphore(16)
        super().__init__(*args, **kwargs)

    def process_request(self, request, client_address):
        if not self.slots.acquire(blocking=False):
            try:
                request.sendall(b'HTTP/1.0 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n')
            except OSError:
                pass
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.slots.release()


def main():
    if not TOKEN:
        raise SystemExit('Add BOT_TOKEN to hosting environment variables.')
    BOT.update(api('getMe', {}))
    # Long polling cannot run while a webhook is configured.
    api('deleteWebhook', {'drop_pending_updates': False})
    print('WGram bot ready: @' + BOT['username'], flush=True)
    threading.Thread(target=poll, daemon=True).start()
    Server(('0.0.0.0', int(os.environ.get('PORT', '3000'))), Handler).serve_forever()


if __name__ == '__main__':
    main()
