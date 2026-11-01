package edu.smartmess;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface Faces extends JpaRepository<FaceSample, Long> {
  List<FaceSample> findByStudentId(Long id);

  void deleteByStudentId(Long id);
}
