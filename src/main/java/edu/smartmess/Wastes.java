package edu.smartmess;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface Wastes extends JpaRepository<Waste, Long> {
  Optional<Waste> findByDayAndMealId(LocalDate day, String mealId);
}
