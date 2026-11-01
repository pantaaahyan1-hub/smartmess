package edu.smartmess;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface Forecasts extends JpaRepository<Forecast, Long> {
  Optional<Forecast> findByDayAndMealId(LocalDate day, String mealId);
}
