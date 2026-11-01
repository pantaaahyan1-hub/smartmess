# SmartMess project structure

Open the SmartMess folder itself in VS Code to load its workspace-specific Explorer exclusions. The visible root is `src/`, `docs/`, `pom.xml`, `README.md`, `start.ps1` and `.gitignore`. `target/`, `data/`, `tools/` and `.vscode/` remain on disk but are hidden. Tests run with `powershell -ExecutionPolicy Bypass -File .\tools\test.ps1`. Documentation paths are relative to `docs/`; Java source paths are relative to the project root.

Java source is under `src/main/java/edu/smartmess`.

| File or folder | Purpose |
|---|---|
| `SmartMessApplication.java` | Starts Spring Boot and enables the booking scheduler. |
| `Pages.java` | Login, registration and dashboard HTML routes. |
| `Api.java` | JSON endpoints for meals, wallets, enrollment, scanner and admin reports. |
| `Errors.java` | Converts expected input/database errors to JSON responses. |
| `MessService.java` | Booking deadlines, automatic YES, eligibility, transactional billing and forecasts. |
| `SelectionScheduler.java` | Calls automatic-selection catch-up every minute. It stays separate so Spring applies the service transaction. |
| `DemoData.java` | Creates demo users, four meals and labeled synthetic history only if no accounts exist. It is in the root edu.smartmess package, not removed. |
| `FaceService.java` | Native OpenCV face detection, normalization, movement checks, enrollment and LBPH matching. |
| `BiometricVault.java` | AES-GCM sample encryption using the existing local key. |
| `Security.java` | BCrypt, role rules, CSRF, login redirects and logout. |
| `Person.java` | Account, wallet, daily budget and consent state. |
| `Meal.java` | Menu, price, service times and derived three-hour cutoff. |
| `Booking.java` | Student YES/NO choice for one date and meal. |
| `Attendance.java` | Verified meal service and charged amount; unique per student/date/meal. |
| `Ledger.java` | Wallet credits and deductions. |
| `FaceSample.java` | Encrypted facial sample bytes. |
| `Waste.java` | Prepared and served portion counts. |
| `Forecast.java` | Expected demand, recommended preparation and explanation. |
| `People.java` | Account queries and pessimistic row lock for wallet safety. |
| `Meals.java` | Meal persistence. |
| `Bookings.java` | Choice queries. |
| `Attendances.java` | Attendance and duplicate checks. |
| `Ledgers.java` | Wallet history queries. |
| `Faces.java` | Encrypted sample queries/deletion. |
| `Wastes.java` | Waste records by date and meal. |
| `Forecasts.java` | Saved forecasts by date and meal. |
| `src/main/resources/templates` | Thymeleaf login, register and application shell. |
| `static/js/app.js` | Dashboard navigation, forms, charts and real webcam capture. |
| `static/css` | Main and narrow-screen styles. |
| `application.properties` | H2, cookie and recognition settings. |
| `src/test/java` | Billing, native recognition/security and real HTTP login regression tests. |
| `pom.xml` | Maven dependencies and Java 17 target. |
| `start.ps1`, `tools/test.ps1` | Windows launch and verification commands. |
| `data` | Existing database and biometric key. Do not delete or replace these to refactor source. |

Read the entry point, `Pages`, `Api`, then `MessService`. Read recognition last because native image processing requires more resource-management code. Repository interfaces and entity classes are intentionally simple; each exists because it represents a separate database responsibility. No custom inheritance hierarchy is needed.
