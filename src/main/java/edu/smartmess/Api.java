package edu.smartmess;

import jakarta.servlet.http.HttpSession;
import java.math.*;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class Api {
  private final People people;
  private final Meals meals;
  private final Bookings bookings;
  private final Attendances attendance;
  private final Ledgers ledger;
  private final Faces faces;
  private final Wastes wastes;
  private final Forecasts forecasts;
  private final MessService mess;
  private final FaceService face;

  public Api(
      People p,
      Meals m,
      Bookings b,
      Attendances a,
      Ledgers l,
      Faces f,
      Wastes w,
      Forecasts fs,
      MessService s,
      FaceService fc) {
    people = p;
    meals = m;
    bookings = b;
    attendance = a;
    ledger = l;
    faces = f;
    wastes = w;
    forecasts = fs;
    mess = s;
    face = fc;
  }

  public Person me(Principal p) {
    return people.findByEmail(p.getName()).orElseThrow();
  }

  public void student(Person p) {
    if (!p.role.equals("STUDENT")) throw new IllegalArgumentException("Student account required");
  }

  @GetMapping("/me")
  public Map<String, Object> profile(Principal auth) {
    Person p = me(auth);
    LocalDate day = mess.now().toLocalDate();
    mess.refreshSelections(day);
    return Map.of(
        "user",
        p,
        "meals",
        meals.findAll(),
        "bookings",
        bookings.findByStudentId(p.id),
        "attendance",
        attendance.findByStudentIdOrderByScannedAtDesc(p.id),
        "transactions",
        ledger.findByStudentIdOrderByCreatedAtDesc(p.id),
        "faceSamples",
        faces.findByStudentId(p.id).size(),
        "today",
        day,
        "serverTime",
        mess.now());
  }

  public record Choice(String meal, LocalDate day, boolean yes) {}

  @PostMapping("/selection")
  public Booking select(Principal p, @RequestBody Choice c) {
    Person u = me(p);
    student(u);
    return mess.select(u.id, c.meal, c.day, c.yes);
  }

  public record Budget(BigDecimal amount) {}

  @PostMapping("/budget")
  @Transactional
  public void budget(Principal auth, @RequestBody Budget b) {
    Person p = people.locked(me(auth).id).orElseThrow();
    student(p);
    if (b.amount == null
        || b.amount.signum() < 0
        || b.amount.compareTo(new BigDecimal("10000")) > 0)
      throw new IllegalArgumentException("Budget must be ₹0–₹10,000");
    p.dailyBudget = b.amount.setScale(2, RoundingMode.HALF_UP);
    people.save(p);
  }

  @PostMapping("/face/challenge")
  public Map<String, Object> challenge(HttpSession session) {
    String nonce = UUID.randomUUID().toString();
    session.setAttribute("faceNonce", nonce);
    session.setAttribute("faceIssued", System.currentTimeMillis());
    return Map.of(
        "nonce", nonce, "instruction", "Slowly move your head left and right for 6 seconds");
  }

  public record Capture(List<String> images, boolean consent, String nonce) {}

  public void check(HttpSession session, Capture c) {
    Object nonce = session.getAttribute("faceNonce"), issued = session.getAttribute("faceIssued");
    session.removeAttribute("faceNonce");
    session.removeAttribute("faceIssued");
    if (nonce == null || !nonce.equals(c.nonce) || issued == null)
      throw new IllegalArgumentException("Start a new live capture");
    long elapsed = System.currentTimeMillis() - (Long) issued;
    if (elapsed < 3500 || elapsed > 60000)
      throw new IllegalArgumentException("Capture expired or too fast. Retry live capture.");
  }

  @PostMapping("/face/enroll")
  public void enroll(Principal p, HttpSession session, @RequestBody Capture c) {
    check(session, c);
    Person u = me(p);
    student(u);
    face.enroll(u.id, c.images, c.consent);
  }

  @DeleteMapping("/face")
  public void delete(Principal p) {
    face.delete(me(p).id);
  }

  @PostMapping("/scanner/recognize")
  public Map<String, Object> recognize(HttpSession s, @RequestBody Capture c) {
    check(s, c);
    FaceService.Match match = face.recognize(c.images);
    Map<String, Object> result = new HashMap<>(mess.admit(match.studentId()));
    result.put("distance", match.distance());
    result.put("threshold", face.threshold);
    return result;
  }

  @GetMapping("/admin/data")
  public Map<String, Object> admin(@RequestParam(required = false) LocalDate day) {
    if (day == null) day = mess.now().toLocalDate();
    List<Forecast> predictions = new ArrayList<>();
    for (Meal m : meals.findAll()) predictions.add(mess.forecast(day, m.id));
    return Map.of(
        "students",
        people.findAll(),
        "meals",
        meals.findAll(),
        "bookings",
        bookings.findAll(),
        "attendance",
        attendance.findAll(),
        "transactions",
        ledger.findAll(),
        "forecasts",
        predictions,
        "waste",
        wastes.findAll(),
        "day",
        day);
  }

  public record Credit(Long studentId, BigDecimal amount) {}

  public record AdminBooking(Long studentId, String meal, LocalDate day, boolean yes) {}

  @PostMapping("/admin/booking")
  public Booking adminBooking(@RequestBody AdminBooking c) {
    student(people.findById(c.studentId).orElseThrow());
    return mess.select(c.studentId, c.meal, c.day, c.yes);
  }

  @PostMapping("/admin/topup")
  public void topup(@RequestBody Credit c) {
    mess.topup(c.studentId, c.amount);
  }

  public record StudentUpdate(Long id, boolean active) {}

  @PostMapping("/admin/student")
  @Transactional
  public void update(@RequestBody StudentUpdate s) {
    Person p = people.locked(s.id).orElseThrow();
    if (!p.role.equals("STUDENT"))
      throw new IllegalArgumentException("Only students can be managed here");
    p.active = s.active;
    people.save(p);
  }

  @DeleteMapping("/admin/face/{id}")
  public void deleteFace(@PathVariable Long id) {
    face.delete(id);
  }

  @PostMapping("/admin/meal")
  @Transactional
  public void meal(@RequestBody Meal input) {
    Meal m = meals.findById(input.id).orElseThrow();
    if (input.title == null
        || input.title.isBlank()
        || input.menu == null
        || input.menu.length() > 500
        || input.price == null
        || input.price.signum() < 0
        || input.price.compareTo(new BigDecimal("1000")) > 0
        || input.starts == null
        || input.ends == null
        || !input.starts.isBefore(input.ends))
      throw new IllegalArgumentException(
          "Invalid price or schedule. Deadline ≤ start < end on the same day.");
    for (Meal other : meals.findAll())
      if (!other.id.equals(m.id)
          && input.starts.isBefore(other.ends)
          && other.starts.isBefore(input.ends))
        throw new IllegalArgumentException("Meal sessions cannot overlap");
    m.title = input.title;
    m.menu = input.menu;
    m.price = input.price.setScale(2, RoundingMode.HALF_UP);
    m.starts = input.starts;
    m.ends = input.ends;
    m.deadline = input.starts.minusHours(3);
    meals.save(m);
  }

  @PostMapping("/admin/waste")
  @Transactional
  public Waste waste(@RequestBody Waste input) {
    if (input.day == null
        || input.day.isAfter(mess.now().toLocalDate())
        || !meals.existsById(input.mealId)
        || input.prepared < 0
        || input.prepared > 100000)
      throw new IllegalArgumentException("Invalid date, meal or portions");
    int actual = attendance.findByDayAndMealId(input.day, input.mealId).size();
    if (input.prepared < actual)
      throw new IllegalArgumentException(
          "Prepared portions cannot be below recorded attendance (" + actual + ")");
    Waste w = wastes.findByDayAndMealId(input.day, input.mealId).orElseGet(Waste::new);
    w.day = input.day;
    w.mealId = input.mealId;
    w.prepared = input.prepared;
    w.served = actual;
    return wastes.save(w);
  }

  @GetMapping(value = "/admin/report", produces = "text/csv")
  public ResponseEntity<String> report() {
    StringBuilder csv = new StringBuilder("Date,Meal,Student ID,Amount\r\n");
    for (Attendance a : attendance.findAll())
      csv.append(a.day)
          .append(',')
          .append(a.mealId)
          .append(',')
          .append(a.studentId)
          .append(',')
          .append(a.amount)
          .append("\r\n");
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=smartmess-attendance.csv")
        .body(csv.toString());
  }
}
