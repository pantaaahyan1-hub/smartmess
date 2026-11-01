package edu.smartmess;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface Attendances extends JpaRepository<Attendance, Long> {
  boolean existsByStudentIdAndDayAndMealId(Long id, LocalDate day, String mealId);

  List<Attendance> findByStudentIdAndDay(Long id, LocalDate day);

  List<Attendance> findByDayAndMealId(LocalDate day, String mealId);

  List<Attendance> findByStudentIdOrderByScannedAtDesc(Long id);
}
