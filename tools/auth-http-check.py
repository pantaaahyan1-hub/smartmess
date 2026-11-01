"""Real HTTP regression checks against the running local application; no mocked security."""
import http.cookiejar, json, urllib.request, urllib.parse, urllib.error
from html.parser import HTMLParser
BASE = 'http://localhost:8080'
class Fields(HTMLParser):
    def __init__(self, html):
        super().__init__(); self.values = {}; self.feed(html)
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'input' and a.get('name'): self.values[a['name']] = a.get('value', '')
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args): return None
def client():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), NoRedirect()), jar
def request(c, path, data=None, method=None):
    body = urllib.parse.urlencode(data).encode() if data is not None else None
    req = urllib.request.Request(BASE+path, body, method=method)
    try: r = c.open(req, timeout=30)
    except urllib.error.HTTPError as e: r = e
    return r.code, r.headers, r.read().decode('utf-8')
def run():
    for role in ('STUDENT', 'ADMIN', 'STAFF'):
        c, jar = client()
        status, headers, html = request(c, '/login')
        assert status == 200 and 'no-store' in headers.get('Cache-Control', '')
        token = Fields(html).values['_csrf']
        before = next(x.value for x in jar if x.name == 'JSESSIONID')
        status, headers, html = request(c, '/login', {'username':role.lower()+'@smartmess.local','password':'SmartMess@123','_csrf':token})
        assert status == 302, (role, status, html[:150])
        location = headers['Location']; path = urllib.parse.urlsplit(location).path
        if '--before' not in __import__('sys').argv:
            assert path == {'STUDENT':'/student/dashboard','ADMIN':'/admin/dashboard','STAFF':'/staff/scanner'}[role]
        status, _, dashboard = request(c, path)
        assert status == 200 and 'name="_csrf"' in dashboard, (role, status)
        after = next(x.value for x in jar if x.name == 'JSESSIONID')
        assert before != after, 'Session ID must rotate on login'
        status, _, body = request(c, '/api/me'); assert status == 200
        assert json.loads(body)['user']['role'] == role
        for asset in ('/css/app.css','/js/app.js','/vendor/bootstrap.min.css','/vendor/chart.umd.js'):
            assert request(c, asset)[0] == 200, asset
        admin_status = request(c, '/api/admin/data')[0]
        assert admin_status == (200 if role == 'ADMIN' else 403)
        # The rendered dashboard token, not a token manufactured by a test helper.
        import re
        meta = re.search(r'<meta\s+name="_csrf"\s+content="([^"]+)"', dashboard)
        assert meta, 'Dashboard must render a CSRF token'
        status, _, _ = request(c, '/api/budget', {}, 'POST'); assert status == 403
        status, headers, _ = request(c, '/logout', {'_csrf':meta.group(1)})
        assert status == 302
        assert request(c, '/api/me')[0] == 302
        print(role+': form POST, session rotation, dashboard, profile, assets, RBAC, CSRF and logout PASS; redirect='+path, flush=True)
    first, _ = client(); _, _, html = request(first, '/login')
    stale = Fields(html).values['_csrf']
    fresh, _ = client()
    status, headers, _ = request(fresh, '/login', {'username':'student@smartmess.local','password':'SmartMess@123','_csrf':stale})
    print('Stale-session login:', status, headers.get('Location'), flush=True)
    if '--before' not in __import__('sys').argv:
        assert status == 303 and headers.get('Location') == '/login?expired'
        status, _, html = request(fresh, '/login?expired'); assert status == 200 and 'expired' in html
        token = Fields(html).values['_csrf']
        assert request(fresh, '/login', {'username':'student@smartmess.local','password':'SmartMess@123','_csrf':token})[0] == 302
        print('Expired login safely refreshes CSRF and succeeds on retry: PASS')
if __name__ == '__main__': run()
