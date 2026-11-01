# Login 403 fix — 26 October 2026

## Root cause and reproduction

The browser was displaying a Whitelabel **403 at POST /login**. GET /login was already public, and fresh form submissions with all three demo accounts succeeded on the original build. Reusing the login form's CSRF token with a different/missing session cookie reproduced the same 403 before authentication. An old tab after session expiry, logout/account switching, or a server restart can submit this stale session-bound token. The application lacked recovery for this case, so Spring's CSRF rejection became a Whitelabel page. This was not a STUDENT authority mismatch or a bad password. The original request's cookies were not retained in application logs, so the precise event that invalidated that particular browser session cannot be reconstructed.

## Changes in the existing application

- Kept CSRF protection, BCrypt passwords, session ID rotation and role checks enabled.
- Invalid/missing CSRF on login or registration **rejects the POST**, then returns **303** to the corresponding `?expired` GET. The new form renders a fresh session token and explains that credentials must be entered again. Credentials are never replayed automatically and the failed request never authenticates.
- Added explicit login processing/failure mappings and deterministic success destinations: STUDENT → `/student/dashboard`, ADMIN → `/admin/dashboard`, STAFF → `/staff/scanner`.
- Enforced dashboard roles on the server, and routed `/` and an authenticated GET `/login` to the user's dashboard. All dashboards retain the existing application UI and feature APIs.
- Permitted locally bundled `/vendor/**` assets publicly. Forbidden API requests retain HTTP 403 and now return a useful JSON message.
- Trimmed and normalized the login email consistently before account lookup.

## Verification

`tools/test.ps1` / Maven `clean verify`: **31 tests passed; 0 failures, 0 errors, 0 skips**. Seven new cases in `LoginHttpIntegrationTest` use **real embedded Tomcat and Java HTTP clients**, rendered form tokens, cookie jars, form POST bodies, redirects, authenticated pages and logout. Existing billing, duplicate/concurrent charge prevention, encryption and native OpenCV tests still pass.

The rebuilt JAR was restarted on `http://localhost:8080` using the existing persistent H2 database. `python tools/auth-http-check.py` separately passed for all three real demo accounts, including session rotation, dashboard/API access, static assets, role denial, CSRF enforcement, logout and stale-token recovery. Its pre-fix run reproduced `Stale-session login: 403`; its post-fix run returned `303 /login?expired` followed by a successful login using the refreshed form.

Browser sign-ins independently confirmed the student dashboard/wallet, admin dashboard/food intelligence and dedicated staff scanner. No unexpected 403, 404, 500 or JavaScript console errors were found in those checks. Intentional cross-role requests and unsafe API writes without CSRF remain forbidden. No biometric enrollment, wallet top-up or meal charge was performed by these login checks.

To repeat:

```powershell
cd "C:\path\to\SmartMess"
# Stop the running app with Ctrl+C before rebuilding: Windows locks the running JAR.
powershell -ExecutionPolicy Bypass -File .\tools\test.ps1
powershell -ExecutionPolicy Bypass -File .\start.ps1
# In another terminal, with the application running:
python .\tools\auth-http-check.py
```
