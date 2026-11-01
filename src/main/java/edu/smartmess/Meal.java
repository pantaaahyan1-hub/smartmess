package edu.smartmess;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
public class Meal {
  @Id public String id;
  public String title, menu;
  public BigDecimal price;
  public LocalTime starts, ends, deadline;

  @PostLoad
  @PrePersist
  @PreUpdate
  public void updateDeadline() {
    if (starts != null) deadline = starts.minusHours(3);
  }
}
