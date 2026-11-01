> Current layout: every application class is directly in `src/main/java/edu/smartmess/`, with package `edu.smartmess`. Earlier package-organization discussion below is historical. Spring Boot discovers components, entities and repositories from this root automatically; no custom scanning configuration is required.

# SmartMess codebase and viva guide

## What SmartMess does

Students log in, choose meals, enroll their face with consent and pay from an internal wallet only when they attend. Staff scan a live webcam; OpenCV identifies an enrolled student, then Java checks the meal session, selection, balance, daily budget and duplicate attendance before charging. Admin manages students, meals, credits, waste and reports.

## Run and present

Run `./start.ps1` in Windows PowerShell and open http://localhost:8080/login. Run `./tools/test.ps1` for clean verification. Main entry: `src/main/java/edu/smartmess/SmartMessApplication.java`. Accounts: student@smartmess.local, admin@smartmess.local, staff@smartmess.local; password SmartMess@123. All wallets are simulated.

## Architecture and folders

`controller` handles HTML/JSON, `service` owns meal selection/billing and scheduled catch-up, `model` contains JPA tables, `repository` provides database query contracts, `security` configures BCrypt/session/roles/CSRF, `recognition` processes OpenCV frames and encrypted samples, forecasting stays inside `MessService` beside the historical-rate calculation. Each public class/interface now has its own file. Resource templates render login/register/application shells; static JavaScript renders role screens, captures real camera JPEGs and posts CSRF-protected JSON. CSS handles responsive layout. Maven pom.xml lists Spring Boot 3.4.4, Spring Web, Thymeleaf, Security, Data JPA/Hibernate, H2, Bytedeco OpenCV/JavaCPP/OpenBLAS and test dependencies. Bootstrap CSS and Chart.js are local vendor assets.

## End-to-end rules

Login is processed by Spring Security, then redirects by role. A student can change YES/NO until meal start minus three hours in Asia/Kolkata. A missing booking becomes YES at that instant; explicit NO remains NO. SelectionScheduler runs catch-up every minute; profile/forecast/admission also catch up, so restart does not lose defaults. No booking operation deducts money.

The scanner uses browser getUserMedia, captures JPEG frames preserving the real camera aspect ratio, obtains a timed one-use nonce and sends frames to Java. Haar detection first uses normal grayscale, then retries contrast equalization when zero faces are found. Exactly one face, good brightness, distinct frames and movement are required. Crops are normalized to 160×160; encrypted training samples feed native LBPH. Lower histogram distance means closer match; threshold 65 and consistent votes in 70% of probes are required. Errors report dimensions/count and guidance, never images or facial pixels. Movement is basic replay resistance and may be bypassed by moved photos/video; physical webcam quality is not certified by automated fixture tests.

MessService locks the student, identifies one current meal, catches up defaults, checks prior attendance, YES, funds and budget, then subtracts price, flushes attendance and records a negative ledger entry in a transaction. A unique student/day/meal constraint is the final duplicate guard. NO, insufficient funds, a repeated scan, or no valid session never charges. YES without attendance costs zero.

Forecast uses up to 56 earlier days, a 4-served/5-booked prior, weekday weight 2 versus 1, expected=round(booked*showRate), and a one-standard-deviation binomial buffer capped at YES bookings. It is an explainable statistical estimator; Haar/LBPH is classical computer vision/machine learning, not deep learning. Forecast date uses earlier history only. Waste stores prepared and actual attendance served; estimated savings assume ingredients cost 45% of meal price.

## Database

Person maps to mess_users; Meal stores menus/prices/times; Booking stores intention; Attendance stores verified service/amount; Ledger stores wallet history; FaceSample stores AES-GCM bytes; Waste stores prepared/served; Forecast stores expected/recommended/rate/explanation. Person IDs and meal IDs are logical relationships: no JPA @ManyToOne or enforced foreign keys are declared. Booking/Attendance uniquely constrain student/day/meal, Waste/Forecast day/meal. H2 persists under data; Hibernate update manages schema. JpaRepository translates repository calls to database operations. Biometric key is a local file, requiring production secret management.

## OOP in simple English

Class/object: a recipe and a thing made from it; Person/Meal are classes and `new Person()` creates an object. Encapsulation: rules live behind service operations; browser cannot choose a wallet charge. Entity fields are public, so field hiding is limited; scheduler has a private final dependency. Abstraction: repository contracts hide SQL and service methods hide business steps. Interfaces: People extends JpaRepository<Person,Long>; Spring generates implementation. Polymorphism/overriding: DemoData implements CommandLineRunner and overrides run; repositories and PasswordEncoder use framework implementations through interfaces. Overloading: Pages.register() and register(String,String,String) share a name with different parameters. Custom domain class inheritance and custom abstract classes: NOT FOUND. Enums declared by the project: NOT FOUND; roles are strings.

Constructors initialize injected dependencies. Public allows cross-package use, private restricts implementation members; final prevents reassignment and static belongs to the class. Generics keep List<Meal>/Optional<Person>/Map types checked. Lists hold records; maps hold JSON/votes; FaceService.validate uses a HashSet of frame hashes to count distinct captures. Ordinary loops find meal sessions, add spending, count YES choices and validate captures. Lambdas remain for short Spring configuration and recognition voting operations. Records carry request/value data. @Entity/@Id map tables; @Service/@Component create beans; @Transactional wraps database changes; @GetMapping/@PostMapping map HTTP; @Scheduled triggers catch-up; @Override checks interface implementation. Exceptions reject invalid requests and controller advice maps expected errors to JSON 400. Try-with-resources closes native matrices/pointers; synchronized serializes detector use. BigDecimal avoids binary floating-point money errors.

Viva OOP answer: “The project groups state into entities and rules into services. Spring injects repository interfaces; DemoData overrides CommandLineRunner.run and Pages overloads register. I use abstraction and framework polymorphism without inventing a domain inheritance hierarchy.”

## Five-minute presentation script

“SmartMess manages hostel dining. Students choose YES or NO before a cutoff three hours before service. Missing selections become YES automatically, but money is charged only for verified attendance. Staff use a real browser webcam; Java OpenCV detects and recognizes enrolled faces. The backend checks selection, funds, budget and duplicate attendance in one transaction. A row lock and unique database constraint prevent double payment. Admin manages meals, students, internal credits and waste. Historical show-up rates and weekday weights predict demand with a transparent safety buffer. Spring Boot starts the web server, Security protects role routes and CSRF, JPA stores entities in H2, and AES-GCM protects biometric samples. Seeded history is illustrative. This is a local university application with basic movement checks, not certified biometric liveness or real banking.”

## Two-minute technical script

“The application starts at SmartMessApplication.main. Controllers receive HTTP requests, services enforce rules, repository interfaces query JPA entities, and recognition invokes native OpenCV. Constructor injection makes required dependencies clear. Records represent request data, collections hold attendance and samples, and streams filter/reduce them. Transactions keep wallet and attendance consistent, while BigDecimal stores money precisely. Spring interface implementations provide polymorphism; DemoData.run overrides an interface method, and Pages.register demonstrates overloading. There is no custom inheritance hierarchy.”

## Revision cheat sheet

Entry: SmartMessApplication.main. Login: Security.filter/dashboard. HTML: Pages. JSON: Api. Select/charge: MessService.select/admitAt. Catch-up: SelectionScheduler.refresh and refreshSelections. Vision: FaceService.crop/validate/enroll/recognize. Encryption: BiometricVault. Query lock: People.locked. Forecast buffer: MessService.forecast. Core rule: selection alone never charges; only approved attendance does. Duplicate guard: lock + check + unique constraint + transaction.

## Implemented, demo and limits

Implemented: role login, CSRF, schedules, automatic YES, wallet ledger, budget, native recognition, consent/delete, encryption, waste/forecast/report UI. Demo: 28-day synthetic history, demo credits and assumed savings. Hardware enrollment quality remains unverified until a real student enrolls and scans on the laptop; native fixture tests are explicitly not physical webcam tests. Native binaries target Windows x64; local H2 and local key storage are demonstration choices.

## 11. Fifty likely viva questions and answers

1. **What is SmartMess?** A hostel meal booking, face-verified attendance, internal-wallet billing and waste-forecast system.
2. **Where is the entry point?** `SmartMessApplication.main`.
3. **Why Spring Boot?** It auto-configures MVC, security, JPA and embedded Tomcat so the Java project runs with little boilerplate.
4. **Why Java 17?** It is the requested modern LTS language baseline.
5. **What is a class?** A blueprint; `MessService` and `Person` are examples.
6. **What is an object?** A runtime instance of a class, such as the injected `MessService` object.
7. **Where is encapsulation?** `MessService.admitAt` owns eligibility and billing rules behind one operation.
8. **Where is inheritance?** No domain class inheritance is used; repository interfaces extend JpaRepository and DemoData implements CommandLineRunner.
9. **Where is polymorphism?** `DemoData implements CommandLineRunner` overrides `run`; repositories implement framework contracts.
10. **Where is method overloading?** `Pages.register()` and `Pages.register(name, email, password)` overload the same name with different parameters.
11. **Where is abstraction?** API methods and repository interfaces hide SQL and transaction details.
12. **What is an interface?** A contract, such as `People extends JpaRepository`.
13. **What is a constructor?** Initialization code; injected repository constructors are examples.
14. **What does `final` do?** Prevents reassignment of an initialized reference.
15. **What does `static` do?** Makes a member belong to the class; `Security.dashboard` is static.
16. **What is dependency injection?** Spring supplies required collaborators to constructors instead of classes creating them.
17. **What is an annotation?** Metadata such as `@Service`, `@Entity` or `@Transactional` read by frameworks.
18. **What is an enum?** A fixed named type; this project does not currently use one and uses strings instead.
19. **What is a generic?** A type parameter such as `List<Meal>` or `JpaRepository<Person,Long>`.
20. **Where are streams used?** Meal filtering, face mapping, attendance reduction and forecast counts.
21. **What happens after login?** Spring authenticates and redirects by role to the correct dashboard.
22. **Why did `/login` return 403?** A stale session-bound CSRF token was rejected before credentials were processed; the fix gives a fresh safe form.
23. **Did you disable CSRF?** No. CSRF remains enabled.
24. **What is authentication?** Proving identity with email/password.
25. **What is authorization?** Checking whether the authenticated role may access a route.
26. **What is BCrypt?** A one-way password hashing algorithm with salt and work factor.
27. **What happens on NO?** A NO booking is stored and admission returns denied with no charge.
28. **What if YES but no attendance?** Nothing is charged; charge occurs only in `admitAt` after a scan.
29. **How are double payments prevented?** Row lock, existence check, unique constraint and transaction.
30. **What if wallet is insufficient?** ACCESS DENIED and no wallet/ledger mutation.
31. **What if daily budget is exceeded?** ACCESS DENIED and no charge.
32. **Why use JPA?** It maps entities to tables and provides CRUD/query behavior with less SQL.
33. **What is a repository?** A data-access contract such as `Attendances`.
34. **What is a primary key?** A row’s unique identity, such as generated `Person.id`.
35. **What is a foreign key here?** Logical IDs such as `Booking.studentId`; the code does not use JPA relationship annotations.
36. **How does the camera reach Java?** Browser getUserMedia captures JPEG frames and posts them as base64 JSON.
37. **What detects a face?** OpenCV Haar `CascadeClassifier`.
38. **What recognizes identity?** OpenCV LBPH `LBPHFaceRecognizer`.
39. **What does the LBPH distance mean?** Lower means a closer histogram match; it must be below the configured threshold.
40. **What is anti-spoofing?** Challenge timing, head movement, distinct-frame checks and one-face/brightness checks; it is basic, not certified liveness.
41. **How are biometrics stored?** AES-GCM encrypted byte samples in `FaceSample`.
42. **What is consent?** Enrollment requires an explicit checkbox and `consent=true`; deletion removes samples and the flag.
43. **What is the forecast algorithm?** Weighted historical show-up rate with a 4/5 prior and one-standard-deviation buffer.
44. **Is it deep learning?** No; it is explainable statistical prediction using Java collections/streams.
45. **Why matching weekdays twice?** Attendance behavior often differs by weekday; the weight prioritizes comparable history.
46. **What is a transaction?** A group of database changes that commits together or rolls back together; `@Transactional` marks billing/updates.
47. **What does `@Lock(PESSIMISTIC_WRITE)` do?** It prevents competing billing transactions from changing the same student concurrently.
48. **How are exceptions returned to the browser?** `Errors` maps expected validation/uniqueness exceptions to HTTP 400 JSON.
49. **What is demo data?** Synthetic history seeded once for presentation; it is explicitly labeled and does not represent real attendance.
50. **What would you improve for production?** External secret/key management, stronger liveness, proper relational foreign keys, migration tooling, audit logging, monitoring and a trained model if justified.






## Readability refactor

Business rules remain in MessService. Session lookup, spending totals, YES counts, face cropping/validation and active training-sample filtering use ordinary loops. Private final dependencies hide internal wiring. The tiny PreparationEstimate wrapper was removed after its sole caller was checked; the same buffer formula now sits inside forecast. DemoData is intentionally retained in service, has descriptive repository names, no unused Forecasts dependency, and derives meal deadlines from starts. Existing database/table names and encryption format/key are unchanged. Native resource cleanup and transactional locking remain because they provide safety rather than decoration.

Real OOP examples: classes and objects (new Person), encapsulation (private final service repositories and transaction boundaries), abstraction (JpaRepository contracts), overriding (DemoData.run implements CommandLineRunner), overloading (Pages.register with zero or three arguments), generic collections (List, Map, Set), records (Capture/Match), and static main. Custom domain class inheritance and custom abstract classes are NOT FOUND; interface inheritance is used by repositories. No artificial hierarchy was added.


## Exact source evidence

Updated after flattening and formatting.

### src/main/java/edu/smartmess/Api.java

`src/main/java/edu/smartmess/Api.java:14`

```java
public class Api {
  private final People people;
  private final Meals meals;
  private final Bookings bookings;
  private final Attendances attendance;
```
`src/main/java/edu/smartmess/Api.java:26`

```java
  public Api(
      People p,
      Meals m,
      Bookings b,
      Attendances a,
```
`src/main/java/edu/smartmess/Api.java:49`

```java
  public Person me(Principal p) {
    return people.findByEmail(p.getName()).orElseThrow();
  }

  public void student(Person p) {
```
`src/main/java/edu/smartmess/Api.java:53`

```java
  public void student(Person p) {
    if (!p.role.equals("STUDENT")) throw new IllegalArgumentException("Student account required");
  }

  @GetMapping("/me")
```
`src/main/java/edu/smartmess/Api.java:58`

```java
  public Map<String, Object> profile(Principal auth) {
    Person p = me(auth);
    LocalDate day = mess.now().toLocalDate();
    mess.refreshSelections(day);
    return Map.of(
```
`src/main/java/edu/smartmess/Api.java:81`

```java
  public record Choice(String meal, LocalDate day, boolean yes) {}

  @PostMapping("/selection")
  public Booking select(Principal p, @RequestBody Choice c) {
    Person u = me(p);
```
`src/main/java/edu/smartmess/Api.java:84`

```java
  public Booking select(Principal p, @RequestBody Choice c) {
    Person u = me(p);
    student(u);
    return mess.select(u.id, c.meal, c.day, c.yes);
  }
```
`src/main/java/edu/smartmess/Api.java:90`

```java
  public record Budget(BigDecimal amount) {}

  @PostMapping("/budget")
  @Transactional
  public void budget(Principal auth, @RequestBody Budget b) {
```
`src/main/java/edu/smartmess/Api.java:94`

```java
  public void budget(Principal auth, @RequestBody Budget b) {
    Person p = people.locked(me(auth).id).orElseThrow();
    student(p);
    if (b.amount == null
        || b.amount.signum() < 0
```
`src/main/java/edu/smartmess/Api.java:106`

```java
  public Map<String, Object> challenge(HttpSession session) {
    String nonce = UUID.randomUUID().toString();
    session.setAttribute("faceNonce", nonce);
    session.setAttribute("faceIssued", System.currentTimeMillis());
    return Map.of(
```
`src/main/java/edu/smartmess/Api.java:114`

```java
  public record Capture(List<String> images, boolean consent, String nonce) {}

  public void check(HttpSession session, Capture c) {
    Object nonce = session.getAttribute("faceNonce"), issued = session.getAttribute("faceIssued");
    session.removeAttribute("faceNonce");
```
`src/main/java/edu/smartmess/Api.java:116`

```java
  public void check(HttpSession session, Capture c) {
    Object nonce = session.getAttribute("faceNonce"), issued = session.getAttribute("faceIssued");
    session.removeAttribute("faceNonce");
    session.removeAttribute("faceIssued");
    if (nonce == null || !nonce.equals(c.nonce) || issued == null)
```
`src/main/java/edu/smartmess/Api.java:128`

```java
  public void enroll(Principal p, HttpSession session, @RequestBody Capture c) {
    check(session, c);
    Person u = me(p);
    student(u);
    face.enroll(u.id, c.images, c.consent);
```
`src/main/java/edu/smartmess/Api.java:136`

```java
  public void delete(Principal p) {
    face.delete(me(p).id);
  }

  @PostMapping("/scanner/recognize")
```
`src/main/java/edu/smartmess/Api.java:141`

```java
  public Map<String, Object> recognize(HttpSession s, @RequestBody Capture c) {
    check(s, c);
    FaceService.Match match = face.recognize(c.images);
    Map<String, Object> result = new HashMap<>(mess.admit(match.studentId()));
    result.put("distance", match.distance());
```
`src/main/java/edu/smartmess/Api.java:151`

```java
  public Map<String, Object> admin(@RequestParam(required = false) LocalDate day) {
    if (day == null) day = mess.now().toLocalDate();
    List<Forecast> predictions = new ArrayList<>();
    for (Meal m : meals.findAll()) predictions.add(mess.forecast(day, m.id));
    return Map.of(
```
`src/main/java/edu/smartmess/Api.java:174`

```java
  public record Credit(Long studentId, BigDecimal amount) {}

  public record AdminBooking(Long studentId, String meal, LocalDate day, boolean yes) {}

  @PostMapping("/admin/booking")
```
`src/main/java/edu/smartmess/Api.java:176`

```java
  public record AdminBooking(Long studentId, String meal, LocalDate day, boolean yes) {}

  @PostMapping("/admin/booking")
  public Booking adminBooking(@RequestBody AdminBooking c) {
    student(people.findById(c.studentId).orElseThrow());
```
`src/main/java/edu/smartmess/Api.java:179`

```java
  public Booking adminBooking(@RequestBody AdminBooking c) {
    student(people.findById(c.studentId).orElseThrow());
    return mess.select(c.studentId, c.meal, c.day, c.yes);
  }

```
`src/main/java/edu/smartmess/Api.java:185`

```java
  public void topup(@RequestBody Credit c) {
    mess.topup(c.studentId, c.amount);
  }

  public record StudentUpdate(Long id, boolean active) {}
```
`src/main/java/edu/smartmess/Api.java:189`

```java
  public record StudentUpdate(Long id, boolean active) {}

  @PostMapping("/admin/student")
  @Transactional
  public void update(@RequestBody StudentUpdate s) {
```
`src/main/java/edu/smartmess/Api.java:193`

```java
  public void update(@RequestBody StudentUpdate s) {
    Person p = people.locked(s.id).orElseThrow();
    if (!p.role.equals("STUDENT"))
      throw new IllegalArgumentException("Only students can be managed here");
    p.active = s.active;
```
`src/main/java/edu/smartmess/Api.java:202`

```java
  public void deleteFace(@PathVariable Long id) {
    face.delete(id);
  }

  @PostMapping("/admin/meal")
```
`src/main/java/edu/smartmess/Api.java:208`

```java
  public void meal(@RequestBody Meal input) {
    Meal m = meals.findById(input.id).orElseThrow();
    if (input.title == null
        || input.title.isBlank()
        || input.menu == null
```
`src/main/java/edu/smartmess/Api.java:238`

```java
  public Waste waste(@RequestBody Waste input) {
    if (input.day == null
        || input.day.isAfter(mess.now().toLocalDate())
        || !meals.existsById(input.mealId)
        || input.prepared < 0
```
`src/main/java/edu/smartmess/Api.java:258`

```java
  public ResponseEntity<String> report() {
    StringBuilder csv = new StringBuilder("Date,Meal,Student ID,Amount\r\n");
    for (Attendance a : attendance.findAll())
      csv.append(a.day)
          .append(',')
```

### src/main/java/edu/smartmess/Attendance.java

`src/main/java/edu/smartmess/Attendance.java:9`

```java
public class Attendance {
  @Id @GeneratedValue public Long id;
  public Long studentId;
  public LocalDate day;
  public String mealId;
```

### src/main/java/edu/smartmess/Attendances.java

`src/main/java/edu/smartmess/Attendances.java:7`

```java
public interface Attendances extends JpaRepository<Attendance, Long> {
  boolean existsByStudentIdAndDayAndMealId(Long id, LocalDate day, String mealId);

  List<Attendance> findByStudentIdAndDay(Long id, LocalDate day);

```

### src/main/java/edu/smartmess/BiometricVault.java

`src/main/java/edu/smartmess/BiometricVault.java:11`

```java
public class BiometricVault {
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public BiometricVault() throws Exception {
```
`src/main/java/edu/smartmess/BiometricVault.java:13`

```java
  private final SecureRandom random = new SecureRandom();

  public BiometricVault() throws Exception {
    Path p = Path.of("data", "biometric.key");
    Files.createDirectories(p.getParent());
```
`src/main/java/edu/smartmess/BiometricVault.java:15`

```java
  public BiometricVault() throws Exception {
    Path p = Path.of("data", "biometric.key");
    Files.createDirectories(p.getParent());
    if (!Files.exists(p)) {
      byte[] b = new byte[32];
```
`src/main/java/edu/smartmess/BiometricVault.java:26`

```java
  public byte[] encrypt(byte[] bytes) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
```
`src/main/java/edu/smartmess/BiometricVault.java:41`

```java
  public byte[] decrypt(byte[] bytes) {
    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
      return c.doFinal(Arrays.copyOfRange(bytes, 12, bytes.length));
```

### src/main/java/edu/smartmess/Booking.java

`src/main/java/edu/smartmess/Booking.java:8`

```java
public class Booking {
  @Id @GeneratedValue public Long id;
  public Long studentId;
  public LocalDate day;
  public String mealId;
```

### src/main/java/edu/smartmess/Bookings.java

`src/main/java/edu/smartmess/Bookings.java:7`

```java
public interface Bookings extends JpaRepository<Booking, Long> {
  Optional<Booking> findByStudentIdAndDayAndMealId(Long studentId, LocalDate day, String mealId);

  List<Booking> findByDayAndMealId(LocalDate day, String mealId);

```

### src/main/java/edu/smartmess/DemoData.java

`src/main/java/edu/smartmess/DemoData.java:11`

```java
public class DemoData implements CommandLineRunner {
  private final People people;
  private final Meals meals;
  private final Bookings bookings;
  private final Attendances attendance;
```
`src/main/java/edu/smartmess/DemoData.java:20`

```java
  public DemoData(
      People people,
      Meals meals,
      Bookings bookings,
      Attendances attendance,
```
`src/main/java/edu/smartmess/DemoData.java:39`

```java
  public void run(String... args) {
    if (people.count() > 0) return;
    create("admin@smartmess.local", "Mess Administrator", "ADMIN");
    create("staff@smartmess.local", "Entrance Staff", "STAFF");
    String[] names = {
```
`src/main/java/edu/smartmess/DemoData.java:105`

```java
  private void create(String email, String name, String role) {
    Person u = new Person();
    u.email = email;
    u.name = name;
    u.role = role;
```
`src/main/java/edu/smartmess/DemoData.java:118`

```java
  private void addMeal(String id, String title, String menu, int price, String start, String end) {
    Meal meal = new Meal();
    meal.id = id;
    meal.title = title;
    meal.menu = menu;
```

### src/main/java/edu/smartmess/Errors.java

`src/main/java/edu/smartmess/Errors.java:10`

```java
public class Errors {
  @ExceptionHandler({
    IllegalArgumentException.class,
    NoSuchElementException.class,
    org.springframework.dao.DataIntegrityViolationException.class
```
`src/main/java/edu/smartmess/Errors.java:16`

```java
  public ResponseEntity<Map<String, String>> bad(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message",
```

### src/main/java/edu/smartmess/Faces.java

`src/main/java/edu/smartmess/Faces.java:7`

```java
public interface Faces extends JpaRepository<FaceSample, Long> {
  List<FaceSample> findByStudentId(Long id);

  void deleteByStudentId(Long id);
}
```

### src/main/java/edu/smartmess/FaceSample.java

`src/main/java/edu/smartmess/FaceSample.java:7`

```java
public class FaceSample {
  @Id @GeneratedValue public Long id;
  public Long studentId;
  @Lob @com.fasterxml.jackson.annotation.JsonIgnore public byte[] encrypted;
}
```

### src/main/java/edu/smartmess/FaceService.java

`src/main/java/edu/smartmess/FaceService.java:19`

```java
public class FaceService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FaceService.class);
  private final Faces faces;
  private final People people;
  private final BiometricVault vault;
```
`src/main/java/edu/smartmess/FaceService.java:20`

```java
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FaceService.class);
  private final Faces faces;
  private final People people;
  private final BiometricVault vault;
  private final CascadeClassifier detector;
```
`src/main/java/edu/smartmess/FaceService.java:27`

```java
  public FaceService(
      Faces f, People p, BiometricVault v, @Value("${smartmess.face.threshold}") double t)
      throws Exception {
    faces = f;
    people = p;
```
`src/main/java/edu/smartmess/FaceService.java:46`

```java
  public record Crop(byte[] pixels, double center, double size, double brightness) {}

  public synchronized Crop crop(String data) {
    byte[] jpg;
    try {
```
`src/main/java/edu/smartmess/FaceService.java:48`

```java
  public synchronized Crop crop(String data) {
    byte[] jpg;
    try {
      jpg = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
    } catch (Exception e) {
```
`src/main/java/edu/smartmess/FaceService.java:103`

```java
  public List<Crop> validate(List<String> images) {
    if (images == null || images.size() < 6 || images.size() > 12)
      throw new IllegalArgumentException("Capture 6–12 live frames");
    List<Crop> samples = new ArrayList<>();
    Set<Integer> frameHashes = new HashSet<>();
```
`src/main/java/edu/smartmess/FaceService.java:126`

```java
  public synchronized void enroll(Long id, List<String> images, boolean consent) {
    if (!consent) throw new IllegalArgumentException("Explicit biometric consent is required");
    Person p = people.locked(id).orElseThrow();
    if (!p.role.equals("STUDENT"))
      throw new IllegalArgumentException("Only student accounts can enroll");
```
`src/main/java/edu/smartmess/FaceService.java:144`

```java
  public synchronized void delete(Long id) {
    Person p = people.locked(id).orElseThrow();
    faces.deleteByStudentId(id);
    p.biometricConsent = false;
    people.save(p);
```
`src/main/java/edu/smartmess/FaceService.java:151`

```java
  public record Match(Long studentId, double distance) {}

  public synchronized Match recognize(List<String> images) {
    List<Crop> probes = validate(images);
    List<FaceSample> training = new ArrayList<>();
```
`src/main/java/edu/smartmess/FaceService.java:153`

```java
  public synchronized Match recognize(List<String> images) {
    List<Crop> probes = validate(images);
    List<FaceSample> training = new ArrayList<>();
    for (FaceSample sample : faces.findAll()) {
      Person student = people.findById(sample.studentId).orElse(null);
```

### src/main/java/edu/smartmess/Forecast.java

`src/main/java/edu/smartmess/Forecast.java:8`

```java
public class Forecast {
  @Id @GeneratedValue public Long id;
  public LocalDate day;
  public String mealId;
  public int expected, recommended, booked;
```

### src/main/java/edu/smartmess/Forecasts.java

`src/main/java/edu/smartmess/Forecasts.java:7`

```java
public interface Forecasts extends JpaRepository<Forecast, Long> {
  Optional<Forecast> findByDayAndMealId(LocalDate day, String mealId);
}
```

### src/main/java/edu/smartmess/Ledger.java

`src/main/java/edu/smartmess/Ledger.java:8`

```java
public class Ledger {
  @Id @GeneratedValue public Long id;
  public Long studentId;
  public BigDecimal amount;
  public String description;
```

### src/main/java/edu/smartmess/Ledgers.java

`src/main/java/edu/smartmess/Ledgers.java:7`

```java
public interface Ledgers extends JpaRepository<Ledger, Long> {
  List<Ledger> findByStudentIdOrderByCreatedAtDesc(Long id);
}
```

### src/main/java/edu/smartmess/Meal.java

`src/main/java/edu/smartmess/Meal.java:8`

```java
public class Meal {
  @Id public String id;
  public String title, menu;
  public BigDecimal price;
  public LocalTime starts, ends, deadline;
```
`src/main/java/edu/smartmess/Meal.java:17`

```java
  public void updateDeadline() {
    if (starts != null) deadline = starts.minusHours(3);
  }
}
```

### src/main/java/edu/smartmess/Meals.java

`src/main/java/edu/smartmess/Meals.java:7`

```java
public interface Meals extends JpaRepository<Meal, String> {}
```

### src/main/java/edu/smartmess/MessService.java

`src/main/java/edu/smartmess/MessService.java:10`

```java
public class MessService {
  private final People people;
  private final Meals meals;
  private final Bookings bookings;
  private final Attendances attendance;
```
`src/main/java/edu/smartmess/MessService.java:18`

```java
  public MessService(
      People people,
      Meals meals,
      Bookings bookings,
      Attendances attendance,
```
`src/main/java/edu/smartmess/MessService.java:33`

```java
  public LocalDateTime now() {
    return LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
  }

  @Transactional
```
`src/main/java/edu/smartmess/MessService.java:38`

```java
  public Booking select(Long id, String mealId, LocalDate day, boolean yes) {
    people.locked(id).orElseThrow();
    Meal m = meals.findById(mealId).orElseThrow();
    if (day.isBefore(now().toLocalDate()) || day.isAfter(now().toLocalDate().plusDays(7)))
      throw new IllegalArgumentException("Choose today or the next seven days");
```
`src/main/java/edu/smartmess/MessService.java:54`

```java
  public Map<String, Object> admit(Long id) {
    return admitAt(id, now());
  }

  @Transactional
```
`src/main/java/edu/smartmess/MessService.java:59`

```java
  public Map<String, Object> admitAt(Long id, LocalDateTime time) {
    Person p = people.locked(id).orElseThrow();
    if (!p.active || !p.role.equals("STUDENT")) return denied("Student account is inactive");
    List<Meal> current = new ArrayList<>();
    for (Meal meal : meals.findAll()) {
```
`src/main/java/edu/smartmess/MessService.java:108`

```java
  private Map<String, Object> denied(String why) {
    return Map.of("granted", false, "message", "ACCESS DENIED", "reason", why);
  }

  // Lazy catch-up makes automatic selections safe after downtime or restart.
```
`src/main/java/edu/smartmess/MessService.java:113`

```java
  public void assignDefault(Long id, LocalDate day, Meal meal, LocalDateTime time) {
    if (time.isBefore(day.atTime(meal.starts).minusHours(3))) return;
    if (bookings.findByStudentIdAndDayAndMealId(id, day, meal.id).isPresent()) return;
    Booking booking = new Booking();
    booking.studentId = id;
```
`src/main/java/edu/smartmess/MessService.java:125`

```java
  public void refreshSelections(LocalDate day) {
    List<Meal> scheduledMeals = meals.findAll();
    for (Person person : people.findAll()) {
      if (!person.active || !"STUDENT".equals(person.role)) continue;
      people.locked(person.id).orElseThrow();
```
`src/main/java/edu/smartmess/MessService.java:136`

```java
  public void record(Long id, BigDecimal amount, String description, LocalDateTime time) {
    Ledger l = new Ledger();
    l.studentId = id;
    l.amount = amount;
    l.description = description;
```
`src/main/java/edu/smartmess/MessService.java:146`

```java
  public void topup(Long id, BigDecimal amount) {
    if (amount == null
        || amount.signum() <= 0
        || amount.compareTo(new BigDecimal("10000")) > 0
        || amount.stripTrailingZeros().scale() > 2)
```
`src/main/java/edu/smartmess/MessService.java:159`

```java
  public Forecast forecast(LocalDate day, String meal) {
    if (!day.isBefore(now().toLocalDate())) refreshSelections(day);
    // Bayesian smoothing: a four-attendance/five-booking prior limits sparse-history noise.
    double yes = 5, served = 4;
    int days = 0;
```
`src/main/java/edu/smartmess/MessService.java:194`

```java
  private int countYes(List<Booking> choices) {
    int count = 0;
    for (Booking choice : choices) {
      if (choice.attending) count++;
    }
```

### src/main/java/edu/smartmess/Pages.java

`src/main/java/edu/smartmess/Pages.java:13`

```java
public class Pages {
  @GetMapping("/login")
  public String login(Authentication authentication) {
    return authentication == null ? "login" : "redirect:" + Security.dashboard(authentication);
  }
```
`src/main/java/edu/smartmess/Pages.java:15`

```java
  public String login(Authentication authentication) {
    return authentication == null ? "login" : "redirect:" + Security.dashboard(authentication);
  }

  @GetMapping("/register")
```
`src/main/java/edu/smartmess/Pages.java:20`

```java
  public String register() {
    return "register";
  }

  @GetMapping("/")
```
`src/main/java/edu/smartmess/Pages.java:25`

```java
  public String home(Authentication authentication) {
    return "redirect:" + Security.dashboard(authentication);
  }

  @GetMapping({"/student/dashboard", "/admin/dashboard", "/staff/scanner"})
```
`src/main/java/edu/smartmess/Pages.java:30`

```java
  public String app() {
    return "app";
  }

  private final People people;
```
`src/main/java/edu/smartmess/Pages.java:37`

```java
  public Pages(People p, PasswordEncoder e) {
    people = p;
    passwords = e;
  }

```
`src/main/java/edu/smartmess/Pages.java:43`

```java
  public String register(
      @RequestParam String name, @RequestParam String email, @RequestParam String password) {
    email = email.trim().toLowerCase();
    if (name.isBlank()
        || name.length() > 80
```

### src/main/java/edu/smartmess/People.java

`src/main/java/edu/smartmess/People.java:9`

```java
public interface People extends JpaRepository<Person, Long> {
  Optional<Person> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Person p where p.id=:id")
```

### src/main/java/edu/smartmess/Person.java

`src/main/java/edu/smartmess/Person.java:9`

```java
public class Person {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

```
`src/main/java/edu/smartmess/Person.java:21`

```java
  public BigDecimal wallet = new BigDecimal("0.00"), dailyBudget = new BigDecimal("250.00");
  public boolean biometricConsent = false;
}
```

### src/main/java/edu/smartmess/Security.java

`src/main/java/edu/smartmess/Security.java:15`

```java
public class Security {
  @Bean
  public PasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }
```
`src/main/java/edu/smartmess/Security.java:17`

```java
  public PasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
```
`src/main/java/edu/smartmess/Security.java:22`

```java
  public UserDetailsService users(People people) {
    return email -> {
      Person p =
          people
              .findByEmail(email.trim().toLowerCase(Locale.ROOT))
```
`src/main/java/edu/smartmess/Security.java:37`

```java
  public SecurityFilterChain filter(HttpSecurity h) throws Exception {
    return h.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/login", "/register", "/css/**", "/js/**", "/vendor/**", "/error")
```
`src/main/java/edu/smartmess/Security.java:96`

```java
  public static String dashboard(Authentication authentication) {
    if (authentication.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) return "/admin/dashboard";
    if (authentication.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"))) return "/staff/scanner";
```

### src/main/java/edu/smartmess/SelectionScheduler.java

`src/main/java/edu/smartmess/SelectionScheduler.java:8`

```java
public class SelectionScheduler {
  private final MessService mess;

  public SelectionScheduler(MessService mess) {
    this.mess = mess;
```
`src/main/java/edu/smartmess/SelectionScheduler.java:11`

```java
  public SelectionScheduler(MessService mess) {
    this.mess = mess;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 1000)
```
`src/main/java/edu/smartmess/SelectionScheduler.java:16`

```java
  public void refresh() {
    mess.refreshSelections(mess.now().toLocalDate());
  }
}
```

### src/main/java/edu/smartmess/SmartMessApplication.java

`src/main/java/edu/smartmess/SmartMessApplication.java:8`

```java
public class SmartMessApplication {
  public static void main(String[] args) {
    SpringApplication.run(SmartMessApplication.class, args);
  }
}
```
`src/main/java/edu/smartmess/SmartMessApplication.java:9`

```java
  public static void main(String[] args) {
    SpringApplication.run(SmartMessApplication.class, args);
  }
}
```

### src/main/java/edu/smartmess/Waste.java

`src/main/java/edu/smartmess/Waste.java:8`

```java
public class Waste {
  @Id @GeneratedValue public Long id;
  public LocalDate day;
  public String mealId;
  public int prepared, served;
```

### src/main/java/edu/smartmess/Wastes.java

`src/main/java/edu/smartmess/Wastes.java:7`

```java
public interface Wastes extends JpaRepository<Waste, Long> {
  Optional<Waste> findByDayAndMealId(LocalDate day, String mealId);
}
```
