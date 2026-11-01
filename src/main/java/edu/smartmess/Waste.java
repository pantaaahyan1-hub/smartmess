package edu.smartmess;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"day", "mealId"}))
public class Waste {
  @Id @GeneratedValue public Long id;
  public LocalDate day;
  public String mealId;
  public int prepared, served;
}
