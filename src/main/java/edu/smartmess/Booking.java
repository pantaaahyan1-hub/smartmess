package edu.smartmess;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"studentId", "day", "mealId"}))
public class Booking {
  @Id @GeneratedValue public Long id;
  public Long studentId;
  public LocalDate day;
  public String mealId;
  public boolean attending;
}
