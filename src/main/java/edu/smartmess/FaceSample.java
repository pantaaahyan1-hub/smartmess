package edu.smartmess;

import jakarta.persistence.*;
import java.time.*;

@Entity
public class FaceSample {
  @Id @GeneratedValue public Long id;
  public Long studentId;
  @Lob @com.fasterxml.jackson.annotation.JsonIgnore public byte[] encrypted;
}
