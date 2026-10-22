"""Provider protocol fixtures only. These tests do not establish live Kakao access."""
import io
import json
import threading
import unittest
import urllib.request
import urllib.error
from http.server import ThreadingHTTPServer
from places_gateway import Config, Gateway, GatewayError, GatewayServer


class Response(io.BytesIO):
    def __init__(self, body):
        super().__init__(json.dumps(body).encode())


class GatewayTests(unittest.TestCase):
    def test_expired_location_query_cache_is_removed_without_another_search(self):
        g=Gateway(self.config(),interval=0);g.cache["expired query/location"]=(0,[])
        g.prune_expired();self.assertEqual(g.cache,{})
    def config(self, key=""):
        return Config("fixture-access-token-not-real", key)

    def test_korean_nearby_uses_kakao_and_keeps_the_key_in_the_upstream_header(self):
        requests = []
        def opener(request, **_):
            requests.append(request)
            return Response({"documents": [{"id": "321", "place_name": "검사용 카페 판교점", "x": "127.1112", "y": "37.3948", "road_address_name": "검사용 주소", "category_name": "음식점 > 카페", "phone": "000-0000", "place_url": "http://place.map.kakao.com/321"}]})
        gateway = Gateway(self.config("fixture-kakao-key-not-real"), opener, interval=0)
        rows = gateway.search(dict(q="카페", bounded="1", lat="37.3948", lon="127.1112", radius="20000", viewbox="126.8,37.6,127.4,37.1"))
        self.assertEqual(rows[0]["name"], "검사용 카페 판교점")
        self.assertEqual(rows[0]["source"], "Kakao Local")
        self.assertEqual(rows[0]["extratags"]["website"], "https://place.map.kakao.com/321")
        self.assertEqual(requests[0].get_header("Authorization"), "KakaoAK fixture-kakao-key-not-real")
        self.assertNotIn("fixture-kakao-key-not-real", requests[0].full_url)
        self.assertNotIn("fixture-kakao-key-not-real", json.dumps(rows))

    def test_six_result_pages_cross_the_kakao_fifteen_result_boundary(self):
        from urllib.parse import urlsplit, parse_qs
        pages=[]
        def opener(request, **_):
            page=int(parse_qs(urlsplit(request.full_url).query)["page"][0]);pages.append(page)
            return Response({"documents":[{"id":str(i),"place_name":f"shop {i}","x":"127.1112","y":"37.3948"} for i in range((page-1)*15+1,page*15+1)],"meta":{"is_end":page==3}})
        g=Gateway(self.config("fixture-key"),opener,interval=0)
        raw=dict(q="shop",bounded="1",lat="37.3948",lon="127.1112",radius="20000",viewbox="126.8,37.6,127.4,37.1")
        excluded=[]
        for expected in (range(1,7),range(7,13),range(13,19)):
            rows=g.search({**raw,"exclude_ids":",".join(excluded)})
            self.assertEqual([r["place_id"] for r in rows],[f"kakao:{i}" for i in expected]);excluded.extend(r["place_id"] for r in rows)
        self.assertEqual(pages,[1,1,1,2])

    def test_world_queries_do_not_get_restricted_to_korea_even_with_kakao_configured(self):
        requests=[]
        def opener(request, **_):
            requests.append(request);return Response([{"place_id": 1, "name": "Paris cafe", "lat": "48.85", "lon": "2.35"}])
        g=Gateway(self.config("fixture-key"),opener,interval=0)
        self.assertEqual(g.search({"q":"Paris cafe"})[0]["source"],"OSM / Nominatim")
        self.assertTrue(requests[0].full_url.startswith("https://nominatim.openstreetmap.org/"))
        self.assertIsNone(requests[0].get_header("Authorization"))

    def test_empty_kakao_nearby_query_can_use_world_search_in_neighbouring_regions(self):
        hosts=[]
        def opener(request, **_):
            hosts.append(request.host)
            if request.host=="dapi.kakao.com":return Response({"documents":[],"meta":{"is_end":True}})
            self.assertIsNone(request.get_header("Authorization"))
            return Response([{"place_id":123,"name":"Fukuoka cafe","lat":"33.59","lon":"130.4"}])
        g=Gateway(self.config("fixture-key"),opener,interval=0)
        rows=g.search(dict(q="cafe",bounded="1",lat="33.59",lon="130.4",radius="20000",viewbox="130.1,33.8,130.7,33.3"))
        self.assertEqual(rows[0]["source"],"OSM / Nominatim");self.assertEqual(hosts,["dapi.kakao.com","nominatim.openstreetmap.org"])

    def test_duplicate_queries_are_cached_and_excluded_ids_are_forwarded(self):
        requests=[]
        def opener(request, **_):
            requests.append(request);return Response([])
        g=Gateway(self.config(),opener,interval=0)
        for _ in range(2):g.search({"q":"shop","exclude_ids":"123","exclude_place_ids":"123"})
        self.assertEqual(len(requests),1)
        self.assertIn("exclude_place_ids=123",requests[0].full_url)

    def test_invalid_input_cannot_trigger_any_upstream_call(self):
        def forbidden(*_,**__):raise AssertionError("unexpected provider request")
        g=Gateway(self.config(),forbidden,interval=0)
        for q in ({"q":"x"},{"q":"shop","bounded":"1","lat":"NaN","lon":"127","viewbox":"1,1,2,0"},{"q":"shop","exclude_place_ids":"123%0dKey"}):
            with self.assertRaises(GatewayError):g.search(q)

    def test_provider_errors_do_not_disclose_headers_or_internal_error_text(self):
        def opener(*_,**__):raise OSError("fixture-kakao-key-not-real internal details")
        g=Gateway(self.config(),opener,interval=0)
        with self.assertRaises(GatewayError) as error:g.search({"q":"shop"})
        self.assertEqual(error.exception.status,502)
        self.assertNotIn("internal details",error.exception.message)

    def test_missing_gateway_authentication_is_rejected_before_provider_access(self):
        g=Gateway(self.config(),lambda *a,**kw:Response([]),interval=0)
        server=GatewayServer(("127.0.0.1",0),g)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        try:
            url=f"http://127.0.0.1:{server.server_port}/search?q=shop"
            with self.assertRaises(urllib.error.HTTPError) as error:urllib.request.urlopen(url)
            self.assertEqual(error.exception.code,401)
            request=urllib.request.Request(url,headers={"Authorization":"Bearer fixture-access-token-not-real","Origin":"https://untrusted.example"})
            with self.assertRaises(urllib.error.HTTPError) as error:urllib.request.urlopen(request)
            self.assertEqual(error.exception.code,403)
            request=urllib.request.Request(url,headers={"Authorization":"Bearer fixture-access-token-not-real","Origin":"http://127.0.0.1:4173"})
            with urllib.request.urlopen(request) as response:
                self.assertEqual(response.status,200);self.assertEqual(response.headers["Access-Control-Allow-Origin"],"http://127.0.0.1:4173");self.assertEqual(json.load(response),[])
        finally:server.shutdown();server.server_close();thread.join(timeout=2)


if __name__=="__main__":unittest.main()
