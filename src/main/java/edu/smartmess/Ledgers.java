package edu.smartmess;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface Ledgers extends JpaRepository<Ledger, Long> {
  List<Ledger> findByStudentIdOrderByCreatedAtDesc(Long id);
}
