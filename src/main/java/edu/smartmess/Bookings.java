package edu.smartmess;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface Bookings extends JpaRepository<Booking, Long> {
  Optional<Booking> findByStudentIdAndDayAndMealId(Long studentId, LocalDate day, String mealId);

  List<Booking> findByDayAndMealId(LocalDate day, String mealId);

  List<Booking> findByStudentId(Long id);
}
