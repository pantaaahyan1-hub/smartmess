package edu.smartmess;

import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface People extends JpaRepository<Person, Long> {
  Optional<Person> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Person p where p.id=:id")
  Optional<Person> locked(@Param("id") Long id);
}
