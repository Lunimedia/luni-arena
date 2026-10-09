import http.client
import sys
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import cover_server as gateway


class FakeIgdb:
    def __init__(self):
        self.lookups = 0

    def find_image_id(self, name):
        self.lookups += 1
        return gateway.matching_game(name, [
            {'name': 'Super Bomberman 4', 'platforms': [19], 'cover': {'image_id': 'wrong4'}},
            {'name': 'Super Bomberman 5', 'platforms': [19], 'cover': {'image_id': 'right5'}}])

    def image(self, image_id):
        assert image_id == 'right5'
        return b'\xff\xd8\xff\xe0picture', 'image/jpeg'


class CoverServerTest(unittest.TestCase):
    def test_normalizes_rom_names_and_never_chooses_wrong_sequel(self):
        rom = 'Super Bomberman 5 (USA) [BR] - snesforever.com.br.sfc'
        self.assertEqual(gateway.title_for_search(rom), 'Super Bomberman 5')
        self.assertEqual(gateway.title_for_search('Zelda (A Link to the Past) (USA).sfc'),
                         'Zelda A Link to the Past')
        games = [{'name': 'Super Bomberman 4', 'platforms': [19],
                  'cover': {'image_id': 'wrong4'}}]
        self.assertIsNone(gateway.matching_game(rom, games))
        self.assertIsNone(gateway.matching_game('Chrono Trigger', [
            {'name': 'Chrono Trigger', 'platforms': [6],
             'cover': {'image_id': 'pcimage'}}]))

    def test_authenticated_fetch_and_disk_cache(self):
        with tempfile.TemporaryDirectory() as location:
            provider = FakeIgdb()
            covers = gateway.Covers(provider, Path(location))
            server = ThreadingHTTPServer(('127.0.0.1', 0), gateway.handler_for(covers, 'a' * 32))
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                conn = http.client.HTTPConnection('127.0.0.1', server.server_port)
                conn.request('GET', '/v1/health', headers={'X-Luni-Key': 'a' * 32})
                response = conn.getresponse()
                self.assertEqual(response.status, 200)
                self.assertIn(b'IGDB', response.read())
                conn.close()
                for key, expected in [('bad', 401), ('a' * 32, 200), ('a' * 32, 200)]:
                    conn = http.client.HTTPConnection('127.0.0.1', server.server_port)
                    conn.request('GET', '/v1/cover?title=Super+Bomberman+5+%28USA%29',
                                 headers={'X-Luni-Key': key})
                    response = conn.getresponse()
                    self.assertEqual(response.status, expected)
                    if expected == 200:
                        self.assertEqual(response.getheader('X-Luni-Source'), 'IGDB.com')
                        self.assertTrue(response.read().startswith(b'\xff\xd8\xff'))
                    else:
                        response.read()
                    conn.close()
                self.assertEqual(provider.lookups, 1)
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)


if __name__ == '__main__':
    unittest.main()
