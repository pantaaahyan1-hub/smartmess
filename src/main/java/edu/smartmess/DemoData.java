package edu.smartmess;

import java.math.*;
import java.time.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoData implements CommandLineRunner {
  private final People people;
  private final Meals meals;
  private final Bookings bookings;
  private final Attendances attendance;
  private final Wastes wasteRecords;
  private final MessService mess;
  private final PasswordEncoder encoder;

  public DemoData(
      People people,
      Meals meals,
      Bookings bookings,
      Attendances attendance,
      Wastes wasteRecords,
      MessService mess,
      PasswordEncoder e) {
    this.people = people;
    this.meals = meals;
    this.bookings = bookings;
    this.attendance = attendance;
    this.wasteRecords = wasteRecords;
    this.mess = mess;
    encoder = e;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (people.count() > 0) return;
    create("admin@smartmess.local", "Mess Administrator", "ADMIN");
    create("staff@smartmess.local", "Entrance Staff", "STAFF");
    String[] names = {
      "Aarav Sharma",
      "Ananya Patel",
      "Rohan Mehta",
      "Isha Singh",
      "Kabir Rao",
      "Diya Verma",
      "Arjun Das",
      "Meera Nair",
      "Vihaan Jain",
      "Sana Khan",
      "Dev Shah",
      "Tara Roy"
    };
    for (int i = 0; i < names.length; i++)
      create(
          i == 0 ? "student@smartmess.local" : "student" + (i + 1) + "@smartmess.local",
          names[i],
          "STUDENT");
    addMeal("BREAKFAST", "Breakfast", "Poha, boiled eggs, fruit & chai", 35, "07:00", "09:30");
    addMeal("LUNCH", "Lunch", "Dal tadka, jeera rice, roti & salad", 65, "12:00", "14:30");
    addMeal("SNACKS", "Evening Snacks", "Veg sandwich & masala chai", 25, "16:00", "18:00");
    addMeal("DINNER", "Dinner", "Paneer curry, roti, rice & dessert", 70, "19:00", "22:00");
    LocalDate today = mess.now().toLocalDate();
    for (int d = 28; d >= 1; d--) {
      LocalDate day = today.minusDays(d);
      for (Meal meal : meals.findAll()) {
        int served = 0;
        for (Person user : people.findAll()) {
          if (!user.role.equals("STUDENT")) continue;
          boolean yes = (user.id + d + meal.id.length()) % 5 != 0;
          Booking bk = new Booking();
          bk.studentId = user.id;
          bk.day = day;
          bk.mealId = meal.id;
          bk.attending = yes;
          bookings.save(bk);
          if (yes && (user.id + d) % 7 != 0) {
            Attendance at = new Attendance();
            at.studentId = user.id;
            at.day = day;
            at.mealId = meal.id;
            at.amount = meal.price;
            at.scannedAt = day.atTime(meal.starts.plusMinutes(20));
            attendance.save(at);
            mess.record(user.id, meal.price.negate(), "DEMO HISTORY • " + meal.title, at.scannedAt);
            served++;
          }
        }
        Waste waste = new Waste();
        waste.day = day;
        waste.mealId = meal.id;
        waste.served = served;
        waste.prepared = served + ((d + meal.id.length()) % 4);
        wasteRecords.save(waste);
      }
    }
    // Historical entries are explicitly synthetic and do not alter opening demo balances.
    for (int d = 28; d >= 1; d--)
      for (Meal meal : meals.findAll()) mess.forecast(today.minusDays(d), meal.id);
  }

  private void create(String email, String name, String role) {
    Person u = new Person();
    u.email = email;
    u.name = name;
    u.role = role;
    u.password = encoder.encode("SmartMess@123");
    u.wallet = role.equals("STUDENT") ? new BigDecimal("1500.00") : BigDecimal.ZERO;
    people.save(u);
    if (role.equals("STUDENT"))
      mess.record(
          u.id, u.wallet, "DEMO opening balance • historical ledger is illustrative", mess.now());
  }

  private void addMeal(String id, String title, String menu, int price, String start, String end) {
    Meal meal = new Meal();
    meal.id = id;
    meal.title = title;
    meal.menu = menu;
    meal.price = BigDecimal.valueOf(price);
    meal.starts = LocalTime.parse(start);
    meal.ends = LocalTime.parse(end);
    meal.updateDeadline();
    meals.save(meal);
  }
}
