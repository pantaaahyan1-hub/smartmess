package edu.smartmess;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessService {
  private final People people;
  private final Meals meals;
  private final Bookings bookings;
  private final Attendances attendance;
  private final Ledgers ledger;
  private final Forecasts forecasts;

  public MessService(
      People people,
      Meals meals,
      Bookings bookings,
      Attendances attendance,
      Ledgers ledger,
      Forecasts forecasts) {
    this.people = people;
    this.meals = meals;
    this.bookings = bookings;
    this.attendance = attendance;
    this.ledger = ledger;
    this.forecasts = forecasts;
  }

  public LocalDateTime now() {
    return LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
  }

  @Transactional
  public Booking select(Long id, String mealId, LocalDate day, boolean yes) {
    people.locked(id).orElseThrow();
    Meal m = meals.findById(mealId).orElseThrow();
    if (day.isBefore(now().toLocalDate()) || day.isAfter(now().toLocalDate().plusDays(7)))
      throw new IllegalArgumentException("Choose today or the next seven days");
    if (!now().isBefore(day.atTime(m.starts).minusHours(3)))
      throw new IllegalArgumentException("Selection deadline has passed");
    Booking b = bookings.findByStudentIdAndDayAndMealId(id, day, mealId).orElseGet(Booking::new);
    b.studentId = id;
    b.day = day;
    b.mealId = mealId;
    b.attending = yes;
    return bookings.save(b);
  }

  @Transactional
  public Map<String, Object> admit(Long id) {
    return admitAt(id, now());
  }

  @Transactional
  public Map<String, Object> admitAt(Long id, LocalDateTime time) {
    Person p = people.locked(id).orElseThrow();
    if (!p.active || !p.role.equals("STUDENT")) return denied("Student account is inactive");
    List<Meal> current = new ArrayList<>();
    for (Meal meal : meals.findAll()) {
      if (!time.toLocalTime().isBefore(meal.starts) && time.toLocalTime().isBefore(meal.ends)) {
        current.add(meal);
      }
    }
    if (current.size() != 1) return denied("No active meal session. Ask admin to check schedules.");
    Meal m = current.get(0);
    LocalDate day = time.toLocalDate();
    assignDefault(id, day, m, time);
    if (attendance.existsByStudentIdAndDayAndMealId(id, day, m.id))
      return denied("Already served — no additional charge");
    if (!bookings.findByStudentIdAndDayAndMealId(id, day, m.id).map(b -> b.attending).orElse(false))
      return denied("Meal selection is NO or missing — no charge");
    if (p.wallet.compareTo(m.price) < 0) return denied("Insufficient wallet balance — no charge");
    BigDecimal spent = BigDecimal.ZERO;
    for (Attendance previous : attendance.findByStudentIdAndDay(id, day)) {
      spent = spent.add(previous.amount);
    }
    if (spent.add(m.price).compareTo(p.dailyBudget) > 0)
      return denied("Daily budget exceeded — no charge");
    p.wallet = p.wallet.subtract(m.price);
    people.save(p);
    Attendance a = new Attendance();
    a.studentId = id;
    a.day = day;
    a.mealId = m.id;
    a.amount = m.price;
    a.scannedAt = time;
    attendance.saveAndFlush(a);
    record(id, m.price.negate(), m.title + " attendance • " + day, time);
    return Map.of(
        "granted",
        true,
        "message",
        "ACCESS GRANTED",
        "name",
        p.name,
        "meal",
        m.title,
        "charged",
        m.price,
        "wallet",
        p.wallet);
  }

  private Map<String, Object> denied(String why) {
    return Map.of("granted", false, "message", "ACCESS DENIED", "reason", why);
  }

  // Lazy catch-up makes automatic selections safe after downtime or restart.
  public void assignDefault(Long id, LocalDate day, Meal meal, LocalDateTime time) {
    if (time.isBefore(day.atTime(meal.starts).minusHours(3))) return;
    if (bookings.findByStudentIdAndDayAndMealId(id, day, meal.id).isPresent()) return;
    Booking booking = new Booking();
    booking.studentId = id;
    booking.day = day;
    booking.mealId = meal.id;
    booking.attending = true;
    bookings.save(booking);
  }

  @Transactional
  public void refreshSelections(LocalDate day) {
    List<Meal> scheduledMeals = meals.findAll();
    for (Person person : people.findAll()) {
      if (!person.active || !"STUDENT".equals(person.role)) continue;
      people.locked(person.id).orElseThrow();
      for (Meal meal : scheduledMeals) {
        assignDefault(person.id, day, meal, now());
      }
    }
  }

  public void record(Long id, BigDecimal amount, String description, LocalDateTime time) {
    Ledger l = new Ledger();
    l.studentId = id;
    l.amount = amount;
    l.description = description;
    l.createdAt = time;
    ledger.save(l);
  }

  @Transactional
  public void topup(Long id, BigDecimal amount) {
    if (amount == null
        || amount.signum() <= 0
        || amount.compareTo(new BigDecimal("10000")) > 0
        || amount.stripTrailingZeros().scale() > 2)
      throw new IllegalArgumentException(
          "Top-up must be ₹0.01–₹10,000 with at most two decimal places");
    Person p = people.locked(id).orElseThrow();
    p.wallet = p.wallet.add(amount.setScale(2, RoundingMode.HALF_UP));
    record(id, amount, "Admin credit • simulated internal wallet", now());
  }

  @Transactional
  public Forecast forecast(LocalDate day, String meal) {
    if (!day.isBefore(now().toLocalDate())) refreshSelections(day);
    // Bayesian smoothing: a four-attendance/five-booking prior limits sparse-history noise.
    double yes = 5, served = 4;
    int days = 0;
    for (int i = 1; i <= 56; i++) {
      LocalDate d = day.minusDays(i);
      double weight = d.getDayOfWeek() == day.getDayOfWeek() ? 2 : 1;
      List<Booking> bs = bookings.findByDayAndMealId(d, meal);
      if (bs.isEmpty()) continue;
      int n = countYes(bs);
      List<Attendance> as = attendance.findByDayAndMealId(d, meal);
      yes += weight * n;
      served += weight * as.size();
      days++;
    }
    double rate = Math.min(1, served / yes);
    int booked = countYes(bookings.findByDayAndMealId(day, meal));
    int expected = (int) Math.round(booked * rate);
    Forecast f = forecasts.findByDayAndMealId(day, meal).orElseGet(Forecast::new);
    f.day = day;
    f.mealId = meal;
    f.booked = booked;
    f.expected = expected;
    double buffer = Math.sqrt(booked * rate * (1 - rate));
    f.recommended = Math.min(booked, (int) Math.ceil(expected + buffer));
    f.showRate = rate;
    f.explanation =
        "YES bookings × smoothed historical show-up rate; matching weekdays count twice. Prior: 4"
            + " attendances / 5 bookings. History: "
            + days
            + " days. Safety buffer: one binomial standard deviation, capped at YES bookings.";
    return forecasts.save(f);
  }

  private int countYes(List<Booking> choices) {
    int count = 0;
    for (Booking choice : choices) {
      if (choice.attending) count++;
    }
    return count;
  }
}
