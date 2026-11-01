# Smart Mess

A Java 17+ hostel mess application built with Spring Boot, Security, JPA, H2, Thymeleaf and native OpenCV. Students select meals and use an internal prepaid wallet. Staff verify attendance using a real webcam; approved attendance charges once. Admin manages menus, schedules, credits, waste records and forecasts.

## Run on Windows

Open this **SmartMess folder** in VS Code (File → Open Folder), then run in PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\start.ps1
```

Open http://localhost:8080. Keep the terminal open; Ctrl+C stops the server. The script uses a configured JDK 17+ (or this laptop's Corretto 21) and bundled Maven under `tools`. If no JAR exists, it builds/tests first. The first build requires internet. Run commands from SmartMess so the existing `data` directory is used. Do not delete or replace that directory or its encryption key.

After source changes, stop the server and rebuild:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\test.ps1
powershell -ExecutionPolicy Bypass -File .\start.ps1
```

## Demo login

Password for all demo accounts: `SmartMess@123`.

| Role | Email |
|---|---|
| Student | student@smartmess.local |
| Admin | admin@smartmess.local |
| Staff | staff@smartmess.local |

`DemoData.java` creates accounts and labeled synthetic history only when the database has no users. Existing accounts, balances and enrollment are retained on restart. Wallet credits are simulated; no banking service is connected.

## Structure

```text
SmartMess/
├── src/
│   ├── main/
│   │   ├── java/edu/smartmess/
│   │   │   └── SmartMessApplication.java
│   │   └── resources/
│   │       ├── static/
│   │       ├── templates/
│   │       ├── application.properties
│   │       └── haarcascade_frontalface_default.xml
│   └── test/
├── docs/
├── pom.xml
├── README.md
└── start.ps1
```

All application classes share the single package `edu.smartmess`; recognition and encryption remain separate classes. Entity and repository types remain separate because JPA needs accessible types across packages. There are no application subpackages or artificial inheritance hierarchies.

VS Code hides `target`, `data`, `tools`, compiled files and temporary files using `.vscode/settings.json`. These files remain on disk. Settings apply when SmartMess is the opened folder; opening its parent Desktop as the workspace does not load child-folder settings. Use File → Open Folder → SmartMess. To reveal hidden files, temporarily change the relevant `files.exclude` value to `false` in workspace settings.

## Java concepts

Classes/objects model users and meals. Private final dependencies and service methods encapsulate rules. Repository interfaces hide database details. Constructor injection connects components. `DemoData.run` overrides `CommandLineRunner.run`; `Pages.register` demonstrates overloading. Generics, lists, maps, sets, records, loops, exceptions and annotations are used. No custom domain inheritance hierarchy is claimed.

## Demonstration

1. Student: show wallet, budget and four meal cards. Changes close exactly three hours before service (IST); missing choices become YES and explicit NO stays NO. Selection alone never charges.
2. Student: consent to enrollment, start the camera and capture while moving gently left/right in good front lighting. Stop that camera before opening the staff scanner.
3. Staff: scan the enrolled student during a current meal session. Eligible attendance deducts the price once and records attendance/ledger history. Repeat scanning must not deduct again. NO, insufficient balance or exceeded budget denies entry without a charge.
4. Admin: show attendance, CSV reports and preparation forecasts using weighted historical show-up rates. Explain that seeded history is synthetic and estimated savings use a stated ingredient-cost assumption.

Native OpenCV fixture tests exercise real detection/recognition and billing. They do **not** verify physical laptop camera quality or certified liveness; moving photos/video can bypass basic movement checks. Physical enrollment and recognition must be tested with the consenting student.

## Documentation and stale editor tabs

- [Project files explained](docs/PROJECT_STRUCTURE.md)
- [Codebase and viva guide](docs/SMARTMESS_CODEBASE_AND_VIVA_GUIDE.md)
- [Detailed setup and camera troubleshooting](docs/SETUP_DETAILS.md)
- [Verification notes](docs/REFINEMENT_VERIFICATION.md)
- [Third-party notices](docs/THIRD_PARTY_NOTICES.md)

If VS Code says the old `DemoData.java` was not found, close that stale tab with its **×** or Ctrl+W. Press Ctrl+P and open `src/main/java/edu/smartmess/DemoData.java`. If Explorer has not refreshed, use its Refresh button or **Developer: Reload Window** from Ctrl+Shift+P. Do not recreate the old file: there is one initializer in `edu.smartmess`, discovered by Spring's component scan.
