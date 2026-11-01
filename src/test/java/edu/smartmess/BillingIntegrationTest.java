package edu.smartmess;

import static org.assertj.core.api.Assertions.*;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:billing;DB_CLOSE_DELAY=-1;NON_KEYWORDS=DAY",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class BillingIntegrationTest {
  @Autowired MessService service;
  @Autowired People people;
  @Autowired Meals meals;
  @Autowired Bookings bookings;
  @Autowired Attendances attendance;
  @Autowired Ledgers ledger;
  Person student;
  final LocalDateTime lunchtime = LocalDate.of(2030, 1, 2).atTime(13, 0);

  @BeforeEach
  void setup() {
    student = new Person();
    student.name = "Test Student";
    student.email = UUID.randomUUID() + "@test.local";
    student.password = "unused";
    student.wallet = new BigDecimal("200.00");
    student.dailyBudget = new BigDecimal("200.00");
    student = people.save(student);
  }

  void yes() {
    Booking b = new Booking();
    b.studentId = student.id;
    b.day = lunchtime.toLocalDate();
    b.mealId = "LUNCH";
    b.attending = true;
    bookings.save(b);
  }

  @Test
  void yesSelectionAloneNeverCharges() {
    yes();
    assertThat(people.findById(student.id).orElseThrow().wallet).isEqualByComparingTo("200");
    assertThat(attendance.findByStudentIdOrderByScannedAtDesc(student.id)).isEmpty();
    assertThat(ledger.findByStudentIdOrderByCreatedAtDesc(student.id)).isEmpty();
  }

  @Test
  void explicitNoSelectionDeductsNothing() {
    Booking b = new Booking();
    b.studentId = student.id;
    b.day = lunchtime.toLocalDate();
    b.mealId = "LUNCH";
    b.attending = false;
    bookings.save(b);
    assertThat(service.admitAt(student.id, lunchtime).get("granted")).isEqualTo(false);
    assertThat(people.findById(student.id).orElseThrow().wallet).isEqualByComparingTo("200");
  }

  @Test
  void automaticYesChargesOnlyOnAttendanceAndOnlyOnce() {
    service.assignDefault(
        student.id, lunchtime.toLocalDate(), meals.findById("LUNCH").orElseThrow(), lunchtime);
    assertThat(people.findById(student.id).orElseThrow().wallet).isEqualByComparingTo("200");
    assertThat(service.admitAt(student.id, lunchtime).get("granted")).isEqualTo(true);
    assertThat(service.admitAt(student.id, lunchtime).get("granted")).isEqualTo(false);
    assertThat(people.findById(student.id).orElseThrow().wallet).isEqualByComparingTo("135");
  }

  @Test
  void automaticSelectionStartsExactlyAtDeadline() {
    Meal meal = meals.findById("LUNCH").orElseThrow();
    LocalDate day = lunchtime.toLocalDate();
    LocalDateTime deadline = day.atTime(meal.starts).minusHours(3);
    service.assignDefault(student.id, day, meal, deadline.minusNanos(1));
    assertThat(bookings.findByStudentIdAndDayAndMealId(student.id, day, meal.id)).isEmpty();
    service.assignDefault(student.id, day, meal, deadline);
    service.assignDefault(student.id, day, meal, deadline.plusHours(1));
    assertThat(
            bookings
                .findByStudentIdAndDayAndMealId(student.id, day, meal.id)
                .orElseThrow()
                .attending)
        .isTrue();
    assertThat(people.findById(student.id).orElseThrow().wallet).isEqualByComparingTo("200");
  }

  @Test
  void eligibleAdmissionChargesExactlyOnce() {
    yes();
    assertThat(service.admitAt(student.id, lunchtime).get("granted")).isEqualTo(true);
    assertThat(service.admitAt(student.id, lunchtime).get("granted")).isEqualTo(false);
    assertThat(people.findById(student.id).orElseThrow().wallet).isEqualByComparingTo("135");
    assertThat(attendance.findByStudentIdOrderByScannedAtDesc(student.id)).hasSize(1);
    assertThat(ledger.findByStudentIdOrderByCreatedAtDesc(student.id)).hasSize(1);
  }

  @Test
  void insufficientBalanceDenied() {
    yes();
    student.wallet = new BigDecimal("10");
    people.save(student);
    assertThat(service.admitAt(student.id, lunchtime).get("granted")).isEqualTo(false);
    assertThat(people.findById(student.id).orElseThrow().wallet).isEqualByComparingTo("10");
    assertThat(attendance.findByStudentIdOrderByScannedAtDesc(student.id)).isEmpty();
  }

  @Test
  void dailyBudgetDenied() {
    yes();
    student.dailyBudget = new BigDecimal("60");
    people.save(student);
    assertThat(service.admitAt(student.id, lunchtime).get("granted")).isEqualTo(false);
    assertThat(people.findById(student.id).orElseThrow().wallet).isEqualByComparingTo("200");
  }

  @Test
  void previousSpendingCountsTowardsBudget() {
    yes();
    student.dailyBudget = new BigDecimal("80");
    people.save(student);
    Attendance a = new Attendance();
    a.studentId = student.id;
    a.day = lunchtime.toLocalDate();
    a.mealId = "BREAKFAST";
    a.amount = new BigDecimal("35");
    a.scannedAt = lunchtime.minusHours(5);
    attendance.save(a);
    assertThat(service.admitAt(student.id, lunchtime).get("granted")).isEqualTo(false);
  }

  @Test
  void outsideSessionDenied() {
    yes();
    assertThat(service.admitAt(student.id, lunchtime.withHour(3)).get("granted")).isEqualTo(false);
    assertThat(attendance.findByStudentIdOrderByScannedAtDesc(student.id)).isEmpty();
  }

  @Test
  void inactiveStudentDenied() {
    yes();
    student.active = false;
    people.save(student);
    assertThat(service.admitAt(student.id, lunchtime).get("granted")).isEqualTo(false);
  }

  @Test
  void simultaneousScansChargeOnce() throws Exception {
    yes();
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Map<String, Object>>> results = new ArrayList<>();
      for (int i = 0; i < 6; i++)
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return service.admitAt(student.id, lunchtime);
                }));
      start.countDown();
      int granted = 0;
      for (var result : results)
        if (Boolean.TRUE.equals(result.get(20, TimeUnit.SECONDS).get("granted"))) granted++;
      assertThat(granted).isEqualTo(1);
      assertThat(people.findById(student.id).orElseThrow().wallet).isEqualByComparingTo("135");
      assertThat(attendance.findByStudentIdOrderByScannedAtDesc(student.id)).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void databaseRejectsDuplicateAttendance() {
    yes();
    service.admitAt(student.id, lunchtime);
    Attendance duplicate = new Attendance();
    duplicate.studentId = student.id;
    duplicate.day = lunchtime.toLocalDate();
    duplicate.mealId = "LUNCH";
    duplicate.amount = new BigDecimal("65");
    assertThatThrownBy(() -> attendance.saveAndFlush(duplicate))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void deadlineAndDateValidation() {
    assertThatThrownBy(
            () ->
                service.select(student.id, "LUNCH", service.now().toLocalDate().minusDays(1), true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.select(student.id, "LUNCH", service.now().toLocalDate().plusDays(8), true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void selectingFutureMealChangesNoMoney() {
    LocalDate tomorrow = service.now().toLocalDate().plusDays(1);
    service.select(student.id, "LUNCH", tomorrow, true);
    service.select(student.id, "LUNCH", tomorrow, false);
    assertThat(
            bookings
                .findByStudentIdAndDayAndMealId(student.id, tomorrow, "LUNCH")
                .orElseThrow()
                .attending)
        .isFalse();
    assertThat(people.findById(student.id).orElseThrow().wallet).isEqualByComparingTo("200");
  }

  @Test
  void forecastIsBoundedAndExplainable() {
    Forecast f = service.forecast(service.now().toLocalDate().plusDays(2), "LUNCH");
    assertThat(f.expected).isBetween(0, f.booked);
    assertThat(f.recommended).isBetween(f.expected, f.booked);
    assertThat(f.explanation).contains("Prior");
  }

  @Test
  void creditRejectsFractionalPaise() {
    assertThatThrownBy(() -> service.topup(student.id, new BigDecimal("0.001")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(people.findById(student.id).orElseThrow().wallet).isEqualByComparingTo("200");
  }

  @Test
  void pastDeadlineRejectsSelection() {
    Meal m = new Meal();
    m.id = "DEADLINE_TEST";
    m.title = "Deadline test";
    m.price = BigDecimal.ONE;
    m.starts = LocalTime.of(1, 0);
    m.ends = LocalTime.of(2, 0);
    m.deadline = LocalTime.MIDNIGHT;
    meals.save(m);
    try {
      assertThatThrownBy(() -> service.select(student.id, m.id, service.now().toLocalDate(), true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("deadline");
    } finally {
      meals.deleteById(m.id);
    }
  }
}
