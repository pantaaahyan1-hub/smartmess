package edu.smartmess;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler({
    IllegalArgumentException.class,
    NoSuchElementException.class,
    org.springframework.dao.DataIntegrityViolationException.class
  })
  public ResponseEntity<Map<String, String>> bad(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message",
                e instanceof org.springframework.dao.DataIntegrityViolationException
                    ? "Record already exists; refresh and try again"
                    : Objects.toString(e.getMessage(), "Invalid request")));
  }
}
