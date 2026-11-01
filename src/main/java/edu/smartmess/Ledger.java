package edu.smartmess;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
public class Ledger {
  @Id @GeneratedValue public Long id;
  public Long studentId;
  public BigDecimal amount;
  public String description;
  public LocalDateTime createdAt;
}
