package edu.smartmess;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"studentId", "day", "mealId"}))
public class Attendance {
  @Id @GeneratedValue public Long id;
  public Long studentId;
  public LocalDate day;
  public String mealId;
  public BigDecimal amount;
  public LocalDateTime scannedAt;
}
