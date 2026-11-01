> Archived detailed setup notes. Use [the main README](../README.md) and [current structure](PROJECT_STRUCTURE.md) for current commands and source paths. Earlier verification counts describe earlier builds.

# Smart Mess: AI-Based Pay-as-You-Eat and Food Waste Management System

A complete local Java university project. Java 17+, Spring Boot 3, Spring Security, Spring Data JPA, persistent H2, real native OpenCV face detection/LBPH recognition, Thymeleaf, Bootstrap and Chart.js. All billing, eligibility, face recognition and prediction logic runs in Java. Browser JavaScript opens the actual webcam with `getUserMedia()` and sends captured JPEG frames to Java.

## Start on this Windows laptop

Open PowerShell and run:

```powershell
cd "C:\path\to\SmartMess"
powershell -ExecutionPolicy Bypass -File .\start.ps1
```

Then open **http://localhost:8080** in Chrome or Edge. Keep the PowerShell window open. Press Ctrl+C to stop. The supplied scripts detect this laptop's existing Corretto 21 JDK and use the bundled Maven 3.9.9. `start.ps1` runs the packaged JAR, building and testing first if the JAR is absent. After changing sources, run `tools/test.ps1` to rebuild before launching. Java 8 on the normal PATH is not sufficient.

For another Windows computer, install a 64-bit JDK 17 or newer, set `JAVA_HOME` to that JDK directory, and copy the complete project including `tools`. The first Maven build requires internet to download dependencies. After dependencies are cached, normal application operation needs no internet: Bootstrap, Chart.js and the OpenCV face cascade are bundled. An optional Google font falls back to system fonts offline.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\tools\apache-maven-3.9.9\bin\mvn.cmd clean verify
powershell -ExecutionPolicy Bypass -File .\start.ps1
```

You can also run the built executable JAR:

```powershell
& "$env:JAVA_HOME\bin\java.exe" '-Djdk.net.unixdomain.tmpdir=C:\smartmess-no-unix-sockets\unavailable' -jar .\target\smart-mess-1.0.0.jar
```

Run from the project directory so the same persistent `data` folder is used. On first launch, allow about 30 seconds after the server starts for synthetic demo history to finish seeding before signing in. The launcher's process-local `jdk.net.unixdomain.tmpdir` option makes this laptop's older Corretto build fall back to TCP for internal Java selector pipes, avoiding a Windows AF_UNIX loopback error; it does not create that directory or change the server's localhost binding. See [OpenJDK's Windows pipe implementation](https://github.com/openjdk/jdk21u/blob/master/src/java.base/windows/classes/sun/nio/ch/PipeImpl.java). Dependencies include Windows x64 OpenCV natives. Other operating systems need the corresponding OpenCV, JavaCPP and OpenBLAS native classifiers in `pom.xml`.

## Demo accounts

All demo passwords: **`SmartMess@123`**

| Role | Email | Purpose |
|---|---|---|
| Student | student@smartmess.local | Meal choices, budget, wallet, history, face enrollment |
| Admin | admin@smartmess.local | Students, credits, schedules, menus, reports, forecasts, waste |
| Staff | staff@smartmess.local | Separate entrance scanner workspace |

There are 12 demo students, 28 days of **synthetic historical** meal choices, attendance and waste, plus historical forecasts. No demo face profiles exist: a real person must enroll. Student opening wallets contain ₹1,500 in simulated credits. Historical ledger rows are explicitly labeled `DEMO HISTORY` and illustrate past activity; they are not a reconciliation of the assigned opening balance. New registrations start at ₹0. Database seeding runs only when the user table is empty.

## Real webcam enrollment and recognition

1. Sign in as a student, open **Face enrollment**, read the consent text and tick the checkbox.
2. Click **Start Camera**. Allow camera permission in Chrome/Edge. A live, mirrored webcam feed must appear.
3. Click **Capture & enroll**. Face forward in good light and **slowly translate your head left and right** within the guide during all six seconds. Do not turn fully sideways. Java must detect exactly one face in each image.
4. Six normalized 160×160 grayscale face samples are encrypted and persisted. Enrollment replaces the old profile atomically. You can delete all samples with **Delete my biometric data**. Admin can also delete a student's samples.
5. Open an InPrivate/Incognito window, sign in as staff, and go to **Entrance scanner**. Close/stop the student camera first: Windows may prevent two windows from using it simultaneously.
6. Start Camera, then **Start automatic scanning**. A scan collects six real webcam frames and Java detects faces, checks movement, trains LBPH on enrolled samples, and requires a consistent match in at least 70% of frames with LBPH distance below 65. Lower distance indicates a closer match; it is **not a probability or calibrated percentage**.
7. If recognized, Java checks the **current IST meal session**, saved YES, active student status, wallet, daily budget and previous attendance. Eligible entry displays **ACCESS GRANTED**, records attendance and charges once. Every denial leaves the wallet untouched. Subsequent scans show **Already served** and charge nothing.
8. Scanning repeats with a five-second pause. **Stop Camera**, navigation and hiding the browser tab release webcam tracks and stop the loop.

The single-use, session-bound capture challenge expires after 60 seconds and rejects captures under 3.5 seconds. Distinct-frame and face-position checks reject simple static frames. This is **basic anti-spoofing**, not certified liveness: a moving photograph or video replay can bypass it. LBPH can produce false matches, especially with similar-looking people, lighting changes or small enrollment sets. This is an educational project, not a production biometric access controller.

### Camera troubleshooting

- Use `http://localhost:8080`, not an insecure LAN IP. Camera APIs require a secure context; browsers treat localhost as trustworthy.
- Windows Settings → Privacy & security → Camera: enable camera access and desktop app access. Check the browser's site permission icon as well.
- Close Teams, Zoom, Camera or any app already using the webcam. Check the laptop's physical shutter and camera function key.
- If frames fail face detection, improve front lighting, keep only one person visible, remove masks and move closer. All six frames must contain one detectable frontal face.
- If movement fails, move your head side to side gently by a few centimeters. If matching fails, delete and re-enroll with lighting similar to the entrance camera.
- Native startup errors: use a **64-bit JDK**, and install the Microsoft Visual C++ 2015–2022 x64 runtime if your Windows installation lacks it.

## Meal rules

Default schedules (all **Asia/Kolkata / IST**, independent of Windows timezone):

| Meal | Price | Choose before | Service |
|---|---:|---|---|
| Breakfast | ₹35 | 04:00 | 07:00–09:30 |
| Lunch | ₹65 | 09:00 | 12:00–14:30 |
| Evening Snacks | ₹25 | 13:00 | 16:00–18:00 |
| Dinner | ₹70 | 16:00 | 19:00–22:00 |

Student selections are editable until exactly three hours before meal start, for today or the next seven days. Missing choices become YES at the cutoff; explicit NO and YES are preserved. Scheduled, dashboard and admission catch-up cover restarts. **Selection alone never charges; only approved attendance charges.** Service windows include start and exclude end. Overnight sessions are unsupported. Admin validation prevents overlaps. Budget updates do not alter past charges.

Wallet operations lock the student row with `PESSIMISTIC_WRITE`. Selection and budget changes use that same lock. Eligibility, attendance creation, wallet deduction and ledger insertion run in one transaction. A database unique constraint on `(student_id, day, meal_id)` is the second line of duplicate protection. No client-supplied student ID is accepted by the scanner: the Java face matcher supplies the identity. There is no manual-ID billing or fake recognition fallback.

## Food waste prediction explained

For each target day and meal, Java reads the preceding 56 calendar days. Days without booking records are excluded. Matching weekdays receive weight 2; other days weight 1. A Bayesian-style prior contributes four attendances and five YES bookings:

```text
showRate = min(1, (4 + weighted past attendance) / (5 + weighted past YES bookings))
expected = round(target YES bookings × showRate)
buffer = sqrt(target YES bookings × showRate × (1 - showRate))
recommended = min(target YES bookings, ceil(expected + buffer))
```

This explainable statistical algorithm learns historical no-show behavior, recent consumption and weekday patterns; it is not a generative AI API or hard-coded dashboard number. Zero YES bookings produce zero preparation recommendation because unbooked students cannot be served. Each forecast stores the inputs, expectation, recommendation and explanation in H2. Historical re-evaluation uses only earlier dates, not the target day's actual attendance. The selected date's past-session MAE compares predicted vs recorded attendance in portions. This is a simple baseline, and small or changing datasets limit accuracy.

Admin records **prepared portions**; served is derived from verified attendance at save time. Record output **after the meal session** and save again if later attendance changes. Wasted = prepared − served; waste % = wasted ÷ prepared × 100, or zero if nothing was prepared. One attendance equals one portion; plate-weight waste is not measured. Ingredient savings are a transparent estimate versus preparing one portion for every registered student, assuming ingredient cost is **45% of current menu price**. They are not audited financial savings.

## Professor demonstration (8–10 minutes)

1. Show admin Overview: synthetic history, attendance trend and meal revenue. Explain the demo labels.
2. Show Food intelligence: change forecast date to yesterday; explain show-up smoothing, preparation buffer and MAE. Record prepared portions for a completed session and show waste calculations.
3. Use a real student, preferably a fresh registration. Admin credits ₹500; student sets a ₹200 daily budget and consents to real webcam enrollment.
4. Use a current meal session for a live scan. For a planned demonstration, configure a non-overlapping meal more than three hours ahead and make selections before its automatic cutoff. If demonstrating automatic YES, leave the choice unset and inspect it after the cutoff. Do not override an explicit NO or bypass deadline checks.
5. Wait for the configured service start. In a staff private window, start scanning with the real enrolled person. Show ACCESS GRANTED and a single debit, then rescan and show no additional charge.
6. Demonstrate NO, insufficient balance and daily budget denial with another enrolled student or another meal. No charge occurs in any denied scenario. No face data is supplied for demo users; each person must enroll themselves with consent.
7. Show updated student wallet/attendance and admin report CSV. Delete the test student's face data and show that recognition no longer succeeds for them.
8. Restore default schedules after the demonstration. Do not bypass deadlines or add a fake recognition result to make the demo work.

## Tests and project structure

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\test.ps1
```

`clean verify` compiles, runs Spring integration/security/native OpenCV tests, and packages the runnable JAR. **Verified result: 24 tests passed, zero failures, errors or skips.** Tests cover YES without attendance, NO/missing selection, successful and duplicate billing, six concurrent scans, database uniqueness, insufficient funds, daily budget including prior spending, incorrect sessions, inactive users, selection date boundaries/deadlines, fractional-paise rejection, forecast bounds, role authorization, CSRF, capture challenges, biometric encryption/tamper detection, native OpenCV rejecting a blank frame, and actual native enrollment/matching/deletion using a public-domain face fixture. Tests use separate in-memory H2 databases and do not alter your persistent application database. Fixture tests are not physical webcam tests.

| File | Responsibility |
|---|---|
| `Models.java`, `Repositories.java` | Students, credentials, wallets, meals, bookings, attendance, ledger, encrypted face samples, forecasts and waste |
| `MessService.java` | Transactional eligibility/billing and explainable forecasting |
| `FaceService.java`, `BiometricVault.java` | Native OpenCV detection/LBPH, confidence voting and AES-GCM storage |
| `Security.java`, `Web.java` | BCrypt, role guards, CSRF, pages and REST APIs |
| `DemoData.java` | Clearly labeled synthetic history and accounts |
| `templates`, `static` | Responsive student/admin/scanner interfaces and locally bundled chart/CSS libraries |

REST endpoints include `/api/me`, `/api/selection`, `/api/budget`, `/api/face/challenge`, `/api/face/enroll`, `DELETE /api/face`, `/api/scanner/recognize`, `/api/admin/data`, `/api/admin/topup`, `/api/admin/student`, `/api/admin/meal`, `/api/admin/waste` and `/api/admin/report`. Mutation requests require the session's CSRF token. Admin/staff routes enforce roles on the server.

## Storage and security boundaries

- H2 persists to `data/smartmess.mv.db`. Passwords are BCrypt hashes. Biometrics are AES-256-GCM ciphertext with random 96-bit IVs. A random local key is generated in `data/biometric.key`; the key must be retained to decrypt samples.
- The local encryption key is stored separately from the DB, but on the same computer. Restrict the `data` folder to your Windows account using NTFS permissions. Do not publish it or upload it to Git. Encryption does not protect against an attacker controlling the logged-in Windows account.
- Biometric deletion removes database samples; it is not forensic secure erasure from H2 free pages, backups or disk snapshots. Remove backups separately where appropriate.
- The server binds only to `127.0.0.1`. Demo credentials and the default local DB password are for a university demo. The wallet is entirely simulated.
- To reset demo data, stop the app, rename `data` to a backup folder, and restart. This deletes the active state by starting a fresh database, including all real enrollment records; retain backups only with consent. Never delete the key while retaining enrolled database samples.

## Third-party sources

OpenCV's Haar frontal-face cascade is bundled from [OpenCV 4.10.0](https://github.com/opencv/opencv/blob/4.10.0/data/haarcascades/haarcascade_frontalface_default.xml), retaining its embedded license notice. [Bytedeco JavaCPP/OpenCV](https://bytedeco.org/download/) supplies Windows native bindings and LBPH. Bootstrap 5.3.3 and Chart.js 4.4.8 are bundled locally with their source license headers. Their licenses and the Apache/MIT/BSD notices of transitive Maven dependencies remain applicable.

Physical webcam operation and recognition accuracy require a real laptop camera and the consenting student. Automated tests cannot establish these; follow the enrollment/scanner procedure above. Do not describe automated native OpenCV checks as physical webcam verification.
