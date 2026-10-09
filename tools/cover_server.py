#!/usr/bin/env python3
"""Private IGDB cover gateway. Bind locally; publish only through an HTTPS proxy.

Requires an IGDB commercial partnership that covers the intended image use.
The Twitch client secret and token never leave this process.
"""
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import threading
import time
import unicodedata
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from difflib import SequenceMatcher
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / 'cover-cache'
MAX_IMAGE = 3 * 1024 * 1024
SNES_PLATFORM = 19


def title_for_search(raw):
    title = re.sub(r'(?i)\.(?:sfc|smc|fig|swc|zip|7z)$', '', raw.strip())
    title = re.sub(r'(?i)\s*[-–]?\s*(?:www\.)?[\w.-]+\.(?:com(?:\.br)?|net|org|br)\b.*$', '', title)
    title = re.sub(r'\[[^]]*\]', ' ', title)
    title = re.sub(r'(?i)\((?:USA|Europe|Japan|World|BR|Brazil|Rev(?:ision)?\s*\d+|Proto(?:type)?|En,?Fr|E|U|J)\)',
                   ' ', title)
    title = title.replace('(', ' ').replace(')', ' ')
    return re.sub(r'\s+', ' ', title).strip(' -–_')[:96]


def normalized(value):
    value = unicodedata.normalize('NFKD', value.casefold())
    value = ''.join(c for c in value if not unicodedata.combining(c))
    return re.sub(r'\s+', ' ', re.sub(r'[^a-z0-9]+', ' ', value.replace('&', ' and '))).strip()


def matching_game(title, games):
    wanted = normalized(title_for_search(title))
    if not wanted:
        return None
    best, best_score = None, 0.0
    for game in games:
        if SNES_PLATFORM not in (game.get('platforms') or []):
            continue
        cover = game.get('cover')
        image_id = cover.get('image_id', '') if isinstance(cover, dict) else ''
        if not re.fullmatch(r'[a-zA-Z0-9_]{3,60}', image_id):
            continue
        found = normalized(game.get('name', ''))
        if not found:
            continue
        # Never silently use another numbered sequel's artwork.
        if re.findall(r'\b\d+\b', wanted) != re.findall(r'\b\d+\b', found):
            continue
        score = SequenceMatcher(None, wanted, found).ratio()
        if wanted == found:
            score = 1.0
        elif found.startswith(wanted + ' ') or wanted.startswith(found + ' '):
            score = max(score, 0.82)
        if score > best_score:
            best, best_score = image_id, score
    return best if best_score >= 0.78 else None


def bounded_read(response, maximum):
    data = response.read(maximum + 1)
    if len(data) > maximum:
        raise ValueError('A resposta excedeu o limite de tamanho')
    return data


def mime_for_image(data):
    if data.startswith(b'\xff\xd8\xff'):
        return 'image/jpeg'
    if data.startswith(b'\x89PNG\r\n\x1a\n'):
        return 'image/png'
    if data[:4] == b'RIFF' and data[8:12] == b'WEBP':
        return 'image/webp'
    raise ValueError('A IGDB não retornou uma imagem compatível')


class Igdb:
    def __init__(self, client_id, secret):
        self.client_id, self.secret = client_id, secret
        self.token, self.expires = '', 0
        self.lock = threading.Lock()
        self.last_query = 0.0

    def access_token(self):
        with self.lock:
            if time.time() < self.expires - 120:
                return self.token
            form = urlencode({'client_id': self.client_id, 'client_secret': self.secret,
                              'grant_type': 'client_credentials'}).encode('ascii')
            req = Request('https://id.twitch.tv/oauth2/token', data=form, method='POST')
            with urlopen(req, timeout=12) as response:
                token = json.loads(bounded_read(response, 65536))
            self.token = token['access_token']
            self.expires = time.time() + int(token['expires_in'])
            return self.token

    def find_image_id(self, title):
        name = title_for_search(title)
        if not name:
            return None
        token = self.access_token()
        # Search input is escaped because APICalypse uses quoted strings.
        safe_name = name.replace('\\', '\\\\').replace('"', '\\"')
        query = ('fields name,platforms,cover.image_id; '
                 f'search "{safe_name}"; '
                 'where platforms = (19) & cover != null; limit 25;')
        with self.lock:
            wait = 0.28 - (time.monotonic() - self.last_query)
            if wait > 0:
                time.sleep(wait)
            self.last_query = time.monotonic()
        req = Request('https://api.igdb.com/v4/games', data=query.encode('utf-8'),
                      headers={'Client-ID': self.client_id, 'Authorization': 'Bearer ' + token,
                               'Accept': 'application/json'}, method='POST')
        with urlopen(req, timeout=12) as response:
            games = json.loads(bounded_read(response, 256 * 1024))
        if not isinstance(games, list):
            raise ValueError('Resposta inesperada da IGDB')
        return matching_game(title, games)

    def image(self, image_id):
        url = f'https://images.igdb.com/igdb/image/upload/t_cover_big/{image_id}.jpg'
        with urlopen(Request(url, headers={'Accept': 'image/jpeg'}), timeout=12) as response:
            data = bounded_read(response, MAX_IMAGE)
        return data, mime_for_image(data)


class Covers:
    def __init__(self, igdb, cache=CACHE):
        self.igdb, self.cache = igdb, cache
        self.lock = threading.Lock()
        self.key_locks = {}
        cache.mkdir(parents=True, exist_ok=True)

    def get(self, title):
        name = title_for_search(title)
        if not name:
            return None
        key = hashlib.sha256(normalized(name).encode()).hexdigest()
        path = self.cache / (key + '.img')
        with self.lock:
            key_lock = self.key_locks.setdefault(key, threading.Lock())
        with key_lock:
            if path.is_file():
                data = path.read_bytes()
                return data, mime_for_image(data)
            image_id = self.igdb.find_image_id(title)
            if not image_id:
                return None
            data, mime = self.igdb.image(image_id)
            temporary = path.with_suffix('.tmp')
            try:
                temporary.write_bytes(data)
                os.replace(temporary, path)
            finally:
                temporary.unlink(missing_ok=True)
            return data, mime


def handler_for(covers, access_key):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if not hmac.compare_digest(self.headers.get('X-Luni-Key', ''), access_key):
                self.send_error(401)
                return
            url = urlsplit(self.path)
            if url.path == '/v1/health' and not url.query:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Cache-Control', 'no-store')
                self.end_headers()
                self.wfile.write(b'{"catalog":"IGDB","status":"ready"}')
                return
            titles = parse_qs(url.query).get('title', [])
            if url.path != '/v1/cover' or len(titles) != 1 or len(titles[0]) > 160:
                self.send_error(400)
                return
            try:
                result = covers.get(titles[0])
            except Exception as e:
                # Log only the exception type: request titles and secrets stay private.
                print('Falha no catálogo:', type(e).__name__, flush=True)
                self.send_error(503, 'Catálogo temporariamente indisponível')
                return
            if result is None:
                self.send_error(404, 'Capa não encontrada')
                return
            data, mime = result
            self.send_response(200)
            self.send_header('Content-Type', mime)
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'private, max-age=86400')
            self.send_header('X-Luni-Source', 'IGDB.com')
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, _format, *_args):
            pass  # Default logs would include the user's ROM filename.
    return Handler


def main():
    if os.environ.get('IGDB_COMMERCIAL_APPROVED') != '1':
        raise SystemExit('Confirme primeiro a parceria comercial e as condições das imagens da IGDB.')
    client_id = os.environ.get('IGDB_CLIENT_ID', '')
    client_secret = os.environ.get('IGDB_CLIENT_SECRET', '')
    access_key = os.environ.get('LUNI_COVER_ACCESS_TOKEN', '')
    if not client_id or not client_secret or len(access_key) < 24:
        raise SystemExit('Defina IGDB_CLIENT_ID, IGDB_CLIENT_SECRET e LUNI_COVER_ACCESS_TOKEN (24+ caracteres).')
    bind, port = '127.0.0.1', 37841
    server = ThreadingHTTPServer((bind, port), handler_for(Covers(Igdb(client_id, client_secret)), access_key))
    print(f'Catálogo IGDB: http://{bind}:{port} (acesso local para proxy HTTPS)', flush=True)
    server.serve_forever()


if __name__ == '__main__':
    main()
