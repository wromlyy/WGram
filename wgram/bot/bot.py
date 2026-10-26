"""WGram profiles + Telegram bot. Python 3.11+, Pillow for universal emoji previews."""
import base64
from contextlib import contextmanager
import hashlib
import hmac
import json
from io import BytesIO
import os
import re
from pathlib import Path
import secrets
import sqlite3
import threading
import time
import urllib.request
from urllib.parse import urlsplit, parse_qs
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
CATALOG_CHAT = os.environ.get('WGRAM_CATALOG_CHAT_ID', '').strip()
MAX_CUSTOMS_PER_USER = 30
MAX_CUSTOMS = 1000


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
            CREATE TABLE IF NOT EXISTS customs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,owner INTEGER NOT NULL,kind TEXT NOT NULL,
                title TEXT NOT NULL,payload TEXT NOT NULL,banner BLOB,status TEXT NOT NULL,
                created INTEGER NOT NULL,fingerprint TEXT UNIQUE NOT NULL);
            CREATE INDEX IF NOT EXISTS customs_owner ON customs(owner,id);
            CREATE INDEX IF NOT EXISTS customs_status ON customs(status,id);
            CREATE TABLE IF NOT EXISTS catalog_outbox(custom_id INTEGER PRIMARY KEY,delivered INTEGER DEFAULT 0);

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



def api_upload(method, payload, field, filename, content):
    """Upload bytes directly: never expose the token-bearing getFile URL in chat."""
    boundary = 'WGram' + secrets.token_hex(16)
    parts = []
    for key, value in payload.items():
        parts.append(('--' + boundary + '\r\nContent-Disposition: form-data; name="' + key +
            '"\r\n\r\n' + str(value) + '\r\n').encode())
    parts.append(('--' + boundary + '\r\nContent-Disposition: form-data; name="' + field +
        '"; filename="' + filename + '"\r\nContent-Type: image/png\r\n\r\n').encode())
    parts.extend((content, ('\r\n--' + boundary + '--\r\n').encode()))
    request = urllib.request.Request('https://api.telegram.org/bot' + TOKEN + '/' + method,
        data=b''.join(parts), headers={'Content-Type': 'multipart/form-data; boundary=' + boundary})
    with urllib.request.urlopen(request, timeout=40) as response:
        result = json.load(response)
    if not result.get('ok'):
        raise RuntimeError('Telegram upload failed')
    return result['result']


def download_emoji_image(file_id):
    limit = 1_500_000
    info = api('getFile', {'file_id': file_id})
    path = info.get('file_path', '')
    if (not isinstance(path, str) or not re.fullmatch(r'[A-Za-z0-9_./-]+', path)
            or path.startswith('/') or '..' in path.split('/') or info.get('file_size', 0) > limit):
        raise ValueError('Invalid emoji image')
    with urllib.request.urlopen('https://api.telegram.org/file/bot' + TOKEN + '/' + path, timeout=20) as response:
        data = response.read(limit + 1)
    if len(data) > limit:
        raise ValueError('Emoji image too large')
    return data


def emoji_preview_png(sticker):
    from PIL import Image
    source = sticker if not sticker.get('is_animated') and not sticker.get('is_video') else sticker.get('thumbnail', {})
    if not source.get('file_id'):
        raise ValueError('No emoji preview available')
    with Image.open(BytesIO(download_emoji_image(source['file_id'])), formats=('WEBP', 'JPEG', 'PNG')) as image:
        if not (0 < image.width <= 512 and 0 < image.height <= 512):
            raise ValueError('Invalid emoji dimensions')
        image = image.convert('RGBA')
        if sticker.get('needs_repainting'):
            tinted = Image.new('RGBA', image.size, (40, 40, 40, 255))
            tinted.putalpha(image.getchannel('A'))
            image = tinted
        scale = 256 / max(image.size)
        image = image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))), Image.Resampling.LANCZOS)
        canvas = Image.new('RGB', (320, 320), (245, 245, 245))
        canvas.paste(image, ((320-image.width)//2, (320-image.height)//2), image)
        output = BytesIO()
        canvas.save(output, 'PNG')
        return output.getvalue()


def send_emoji_preview(chat_id, emoji):
    """Recipient Premium is never required. Check native delivery before falling back."""
    emoji = str(uid(emoji))
    try:
        stickers = api('getCustomEmojiStickers', {'custom_emoji_ids': [emoji]})
        sticker = next(s for s in stickers if isinstance(s, dict) and str(s.get('custom_emoji_id')) == emoji)
    except Exception:
        api('sendMessage', {'chat_id': chat_id, 'text': 'Эмодзи сейчас недоступен. Попробуй открыть работу позже.'})
        return False
    alt = sticker.get('emoji')
    if isinstance(alt, str) and alt:
        try:
            sent = api('sendMessage', {'chat_id': chat_id, 'text': alt,
                'entities': [{'type': 'custom_emoji', 'offset': 0, 'length': len(alt.encode('utf-16-le'))//2,
                    'custom_emoji_id': emoji}]})
            if any(e.get('type') == 'custom_emoji' and str(e.get('custom_emoji_id')) == emoji
                   for e in sent.get('entities', []) if isinstance(e, dict)):
                return True
        except Exception:
            pass  # Bot permission may be absent even though recipients can view emojis.
    try:
        image = emoji_preview_png(sticker)
        api_upload('sendPhoto', {'chat_id': chat_id, 'caption': 'Предпросмотр эмодзи · Premium для просмотра не нужен'},
            'photo', 'emoji.png', image)
        return True
    except Exception:
        api('sendMessage', {'chat_id': chat_id,
            'text': 'Не удалось загрузить предпросмотр. Анимацию можно открыть в каталоге WGram без Premium.'})
        return False


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


def custom_json(row):
    return {'id': row['id'], 'owner': row['owner'], 'kind': row['kind'], 'title': row['title'],
        'payload': json.loads(row['payload']), 'banner': row['banner'] is not None,
        'status': row['status'], 'created': row['created']}


def create_custom(db, owner, kind, title, payload, banner=None):
    if not isinstance(title, str):
        raise ValueError('Title must be text')
    title = title.strip()
    if not title or len(title) > 60 or any(ord(c) < 32 for c in title):
        raise ValueError('Title must contain 1–60 characters')
    if kind not in ('profile', 'banner', 'emoji', 'emoji_pack', 'sticker_pack'):
        raise ValueError('Unknown custom type')
    if kind in ('profile', 'banner'):
        payload = clean_profile(payload)
        if banner is not None:
            banner = banner_bytes(banner)
        if kind == 'banner' and banner is None:
            raise ValueError('Banner required')
        if kind == 'profile' and not (banner or payload['premium'] or payload['emoji'] or payload['color'] >= 0):
            raise ValueError('Choose profile appearance before sharing')
    elif kind == 'emoji':
        payload = {'emoji': uid(payload['emoji'])}
        banner = None
    else:
        name = str(payload['short_name'])
        if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,63}', name):
            raise ValueError('Invalid sticker set name')
        payload = {'short_name': name}
        banner = None
    encoded = json.dumps(payload, sort_keys=True, ensure_ascii=False)
    fingerprint = digest(str(owner) + ':' + kind + ':' + title + ':' + encoded + ':' + hashlib.sha256(banner or b'').hexdigest())
    existing = db.execute('SELECT * FROM customs WHERE fingerprint=?', (fingerprint,)).fetchone()
    if existing:
        return custom_json(existing)
    if db.execute('SELECT COUNT(*) FROM customs WHERE owner=?', (owner,)).fetchone()[0] >= MAX_CUSTOMS_PER_USER:
        return None
    if db.execute('SELECT COUNT(*) FROM customs').fetchone()[0] >= MAX_CUSTOMS:
        return None
    cursor = db.execute('INSERT INTO customs(owner,kind,title,payload,banner,status,created,fingerprint) VALUES(?,?,?,?,?,?,?,?)',
        (owner, kind, title, encoded, banner, 'pending', int(time.time()), fingerprint))
    return custom_json(db.execute('SELECT * FROM customs WHERE id=?', (cursor.lastrowid,)).fetchone())


def snapshot_custom(db, owner, title, kind='profile'):
    row = db.execute('SELECT payload FROM profiles WHERE uid=?', (owner,)).fetchone()
    profile = json.loads(row[0]) if row else clean_profile({})
    image = db.execute('SELECT data FROM banners WHERE uid=?', (owner,)).fetchone()
    return create_custom(db, owner, kind, title, profile, bytes(image[0]) if image else None)


def moderate_custom(db, actor, custom_id, approve):
    if actor != ADMIN:
        return False
    row = db.execute('SELECT * FROM customs WHERE id=?', (custom_id,)).fetchone()
    if not row:
        return False
    status = 'published' if approve else 'rejected'
    if row['status'] != status:
        db.execute('UPDATE customs SET status=? WHERE id=?', (status, custom_id))
        if approve and CATALOG_CHAT:
            db.execute('INSERT OR IGNORE INTO catalog_outbox(custom_id) VALUES(?)', (custom_id,))
    return True


def catalog_link(custom_id):
    name = BOT.get('username', '')
    return 'https://t.me/' + name + '?start=custom_' + str(custom_id) if re.fullmatch(r'[A-Za-z0-9_]{5,32}', name) else ''


def deliver_catalog():
    if not CATALOG_CHAT:
        return
    with GUARD, database() as db:
        rows = db.execute("SELECT c.* FROM catalog_outbox o JOIN customs c ON c.id=o.custom_id WHERE o.delivered=0 AND c.status='published' ORDER BY c.id LIMIT 5").fetchall()
    for row in rows:
        item = custom_json(row)
        link = catalog_link(item['id'])
        api('sendMessage', {'chat_id': CATALOG_CHAT, 'text': item['title'] + '\nАвтор: ' + str(item['owner']) + '\n' + link,
            'reply_markup': {'inline_keyboard': [[{'text': 'Открыть кастомку', 'url': link}]]}})
        with GUARD, database() as db:
            db.execute('UPDATE catalog_outbox SET delivered=1 WHERE custom_id=?', (item['id'],))


def handle_custom_command(sender, text, preview=False):
    command, _, argument = text.partition(' ')
    if command == '/view' or (command == '/start' and argument.startswith('custom_')):
        try:
            custom_id = uid(argument[7:] if command == '/start' else argument)
        except (ValueError, TypeError):
            return 'Кастомка не найдена.'
        with GUARD, database() as db:
            row = db.execute("SELECT * FROM customs WHERE id=? AND (status='published' OR owner=? OR ?=?)", (custom_id, sender, sender, ADMIN)).fetchone()
            if not row:
                return 'Кастомка не найдена.'
            item = custom_json(row)
        content = item['title'] + '\nАвтор: ' + str(item['owner']) + '\nСтатус: ' + item['status']
        if item['kind'] in ('emoji_pack', 'sticker_pack'):
            content += '\nhttps://t.me/' + ('addemoji/' if item['kind'] == 'emoji_pack' else 'addstickers/') + item['payload']['short_name']
        else:
            content += '\nОткрой каталог кастомок в настройках WGram, чтобы применить.'
        if preview and item['payload'].get('emoji'):
            send_emoji_preview(sender, item['payload']['emoji'])
        return content
    if text in ('/customs', '/start customs', '/help'):
        return ('Общий каталог WGram. Всё отправленное на публикацию видно другим после одобрения.\n'
            '/submit Название — поделиться сохранённым профилем\n'
            'Фото с подписью /custom Название — поделиться баннером\n'
            'Кастомный эмодзи отдельным сообщением — добавить эмодзи\n'
            '/emoji ID Название — добавить эмодзи\n'
            'Ссылка t.me/addemoji/... или t.me/addstickers/... — добавить набор\n'
            '/catalog — общий каталог\n/view ID — открыть работу и эмодзи без Premium\n/mine — мои работы\n/remove ID — удалить свою работу')
    if command in ('/catalog', '/mine', '/pending'):
        with GUARD, database() as db:
            if command == '/pending':
                if sender != ADMIN:
                    return 'Проверка работ доступна владельцу WGram.'
                rows = db.execute("SELECT * FROM customs WHERE status='pending' ORDER BY id LIMIT 20").fetchall()
            elif command == '/mine':
                rows = db.execute('SELECT * FROM customs WHERE owner=? ORDER BY id DESC LIMIT 30', (sender,)).fetchall()
            else:
                rows = db.execute("SELECT * FROM customs WHERE status='published' ORDER BY id DESC LIMIT 20").fetchall()
        return '\n\n'.join(str(row['id']) + '. ' + row['title'] + ' [' + row['status'] + ']\n' + catalog_link(row['id']) for row in rows) or 'Пока нет работ. /customs — как добавить.'
    if command in ('/approve', '/reject', '/remove'):
        try:
            custom_id = uid(argument)
        except (ValueError, TypeError):
            return 'Укажи числовой ID работы.'
        with GUARD, database() as db:
            if command == '/remove':
                removed = db.execute('DELETE FROM customs WHERE id=? AND (owner=? OR ?=?)', (custom_id, sender, sender, ADMIN)).rowcount
                db.execute('DELETE FROM catalog_outbox WHERE custom_id=? AND NOT EXISTS(SELECT 1 FROM customs WHERE id=?)', (custom_id, custom_id))
                return 'Работа удалена из каталога.' if removed else 'Работа не найдена или принадлежит другому автору.'
            if sender != ADMIN:
                return 'Одобрение работ доступно владельцу WGram.'
            success = moderate_custom(db, sender, custom_id, command == '/approve')
        return ('Работа одобрена.' if command == '/approve' else 'Работа отклонена.') if success else 'Работа не найдена.'
    if command in ('/submit', '/emoji'):
        try:
            with GUARD, database() as db:
                if command == '/submit':
                    item = snapshot_custom(db, sender, argument)
                else:
                    emoji, _, title = argument.partition(' ')
                    item = create_custom(db, sender, 'emoji', title, {'emoji': uid(emoji)})
        except (ValueError, KeyError, TypeError):
            return 'Не удалось добавить. Для /submit сначала опубликуй профиль из WGram. Для /emoji укажи ID и название (до 60 символов).'
        return 'Лимит работ достигнут. Удали ненужные через /mine.' if item is None else 'Работа #' + str(item['id']) + ' отправлена на проверку. /mine — мои работы.'
    match = re.fullmatch(r'(?:https?://)?t\.me/(addemoji|addstickers)/([A-Za-z][A-Za-z0-9_]{0,63})', text.strip())
    if match:
        kind = 'emoji_pack' if match[1] == 'addemoji' else 'sticker_pack'
        try:
            info = api('getStickerSet', {'name': match[2]})
            if (info.get('sticker_type') == 'custom_emoji') != (kind == 'emoji_pack'):
                return 'Тип ссылки не совпадает с набором.'
            if not info.get('title'):
                return 'Набор не найден.'
            with GUARD, database() as db:
                item = create_custom(db, sender, kind, str(info['title'])[:60], {'short_name': match[2]})
        except Exception:
            return 'Не удалось проверить набор. Пришли прямую ссылку t.me/addemoji/... или t.me/addstickers/...'
        return 'Лимит работ достигнут.' if item is None else 'Набор #' + str(item['id']) + ' отправлен на проверку.'
    return None


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
    custom_reply = handle_custom_command(sender, text, preview=True)
    if custom_reply is not None:
        api('sendMessage', {'chat_id': sender, 'text': custom_reply})
        return
    emoji_entities = [entity for entity in message.get('entities', [])
        if isinstance(entity, dict) and entity.get('type') == 'custom_emoji']
    if emoji_entities and not text.startswith('/'):
        try:
            emoji = uid(emoji_entities[0]['custom_emoji_id'])
            with GUARD, database() as db:
                item = create_custom(db, sender, 'emoji', 'Эмодзи ' + str(emoji), {'emoji': emoji})
            reply = 'Лимит работ достигнут.' if item is None else 'Эмодзи #' + str(item['id']) + ' отправлен на проверку. /mine — мои работы.'
        except (ValueError, KeyError, TypeError):
            reply = 'Не удалось прочитать кастомный эмодзи. Попробуй прислать его отдельным сообщением.'
    elif text == '/delete':
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
                caption = str(message.get('caption', ''))
                if caption.startswith('/custom '):
                    try:
                        with GUARD, database() as db:
                            item = create_custom(db, sender, 'banner', caption[8:], {}, image)
                        reply = 'Лимит работ достигнут.' if item is None else 'Баннер #' + str(item['id']) + ' отправлен на проверку. /mine — мои работы.'
                    except ValueError:
                        reply = 'Укажи название баннера до 60 символов после /custom.'
                else:
                    with GUARD, database() as db:
                        db.execute('INSERT OR REPLACE INTO banners VALUES(?,?)', (sender, image))
                    reply = 'Баннер сохранён для профиля WGram. Чтобы поделиться им со всеми, отправь фото с подписью /custom Название. /delete — удалить оформление.'
    else:
        reply = 'Профили и каталог WGram. /customs — добавить свою работу, /catalog — общий каталог. Подключись из настроек мода, чтобы применять оформление. Фото без команды сохраняется в твой профиль. /delete — удалить оформление. Коды входа не присылай.'
    api('sendMessage', {'chat_id': sender, 'text': reply})


def poll():
    with database() as db:
        row = db.execute("SELECT value FROM state WHERE name='offset'").fetchone()
        offset = int(row[0]) if row else 0
    while True:
        try:
            updates = api('getUpdates', {'offset': offset, 'timeout': 25, 'allowed_updates': ['message']})
            try:
                deliver_catalog()
            except Exception:
                print('Catalog post could not be delivered; will retry.', flush=True)
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
    query = parse_qs(urlsplit(path).query)
    path = urlsplit(path).path
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
    if path == '/v1/customs' and method == 'GET':
        mine = query.get('mine', ['0'])[0] == '1'
        before = uid(query['before'][0]) if 'before' in query else 2**63-1
        kind = query.get('kind', [''])[0]
        condition, params = ('owner=?', [user]) if mine else ("status='published'", [])
        condition += ' AND id<?'
        params.append(before)
        if kind:
            condition += ' AND kind=?'
            params.append(kind)
        rows = db.execute('SELECT * FROM customs WHERE ' + condition + ' ORDER BY id DESC LIMIT 21', params).fetchall()
        return 200, {'items': [custom_json(row) for row in rows[:20]], 'next': rows[19]['id'] if len(rows) > 20 else 0}
    if path == '/v1/customs' and method == 'POST':
        kind = data.get('kind', 'profile')
        if kind not in ('profile', 'banner', 'emoji'):
            return 400, {'error': 'SUBMIT_PACK_TO_BOT'}
        try:
            item = (create_custom(db, user, kind, data['title'], {'emoji': uid(data['emoji'])}) if kind == 'emoji'
                else snapshot_custom(db, user, data['title'], kind))
        except (ValueError, KeyError, TypeError):
            return 400, {'error': 'INVALID_CUSTOM'}
        return (200, {'item': item}) if item else (429, {'error': 'CUSTOM_LIMIT'})
    if path.startswith('/v1/customs/'):
        parts = path.split('/')
        custom_id = uid(parts[3])
        row = db.execute('SELECT * FROM customs WHERE id=?', (custom_id,)).fetchone()
        if row is None or row['status'] != 'published' and row['owner'] != user and user != ADMIN:
            return 404, {'error': 'NOT_FOUND'}
        if len(parts) == 4 and method == 'GET':
            return 200, {'item': custom_json(row)}
        if len(parts) == 5 and parts[4] == 'banner' and method == 'GET':
            return (200, bytes(row['banner'])) if row['banner'] is not None else (404, {'error': 'NO_BANNER'})
        if len(parts) == 5 and parts[4] == 'delete' and method == 'POST':
            if row['owner'] != user and user != ADMIN:
                return 403, {'error': 'OWNER_ONLY'}
            db.execute('DELETE FROM customs WHERE id=?', (custom_id,))
            db.execute('DELETE FROM catalog_outbox WHERE custom_id=?', (custom_id,))
            return 200, {'ok': True}
        return 404, {'error': 'NOT_FOUND'}
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
