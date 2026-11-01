# Verification record — 25 October 2026 (IST)

**Update — 26 October 2026:** the login recovery fix passes **31 tests**, including seven real embedded-server HTTP login cases. The rebuilt application was restarted, and all three demo roles also passed live HTTP and browser login checks against the persistent database. See [LOGIN_FIX.md](LOGIN_FIX.md) for the reproduced root cause, changes and commands.

- `tools/test.ps1` / Maven `clean verify`: **BUILD SUCCESS**, **24 tests, 0 failures, 0 errors, 0 skipped**. Test reports are in `target/surefire-reports`.
- Compiled Java sources for Java 17 using the laptop's Corretto 21.0.3.
- Packaged `target/smart-mess-1.0.0.jar` and launched it with `start.ps1`.
- HTTP GET `http://localhost:8080/login`: **200 OK**.
- Browser verified: student login, student dashboard with real persisted balance/history, face enrollment and consent controls, admin login and overview/chart, forecast/waste analytics, meal editing forms, logout, and dedicated staff login/scanner view.
- Native Windows OpenCV test detected and rejected an image with no face. Another test detected, normalized, encrypted, enrolled and recognized the NASA astronaut fixture through actual LBPH, then deleted the profile. This fixture is test-only and never appears as an enrolled application user.
- Billing tests verified six concurrent admission requests produce exactly one charge and one attendance entry. Database uniqueness was independently tested. The expected unique-constraint error in the test log is a passing rejection test.
- The launcher contains a process-local workaround for the installed JDK's Windows Unix-domain socket selector issue. It successfully starts Tomcat on 127.0.0.1:8080.

**Not physically verified:** a live Windows webcam feed, enrollment of a consenting human and recognition accuracy on that human. The embedded verification browser did not establish a physical camera feed. No real face profile was enrolled or biometric consent accepted during browser checks. Use Chrome or Edge and the README procedure to test the laptop camera. Basic movement checking is not certified liveness detection.
