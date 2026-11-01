package edu.smartmess;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "mess_users")
public class Person {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(unique = true, nullable = false)
  public String email;

  public String name;
  @com.fasterxml.jackson.annotation.JsonIgnore public String password;
  public String role = "STUDENT";
  public boolean active = true;
  public BigDecimal wallet = new BigDecimal("0.00"), dailyBudget = new BigDecimal("250.00");
  public boolean biometricConsent = false;
}
