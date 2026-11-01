package edu.smartmess;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class Pages {
  @GetMapping("/login")
  public String login(Authentication authentication) {
    return authentication == null ? "login" : "redirect:" + Security.dashboard(authentication);
  }

  @GetMapping("/register")
  public String register() {
    return "register";
  }

  @GetMapping("/")
  public String home(Authentication authentication) {
    return "redirect:" + Security.dashboard(authentication);
  }

  @GetMapping({"/student/dashboard", "/admin/dashboard", "/staff/scanner"})
  public String app() {
    return "app";
  }

  private final People people;
  private final PasswordEncoder passwords;

  public Pages(People p, PasswordEncoder e) {
    people = p;
    passwords = e;
  }

  @PostMapping("/register")
  public String register(
      @RequestParam String name, @RequestParam String email, @RequestParam String password) {
    email = email.trim().toLowerCase();
    if (name.isBlank()
        || name.length() > 80
        || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || email.length() > 150
        || password.length() < 10
        || password.length() > 72
        || people.findByEmail(email).isPresent()) return "redirect:/register?error";
    Person p = new Person();
    p.name = name.trim();
    p.email = email;
    p.password = passwords.encode(password);
    people.save(p);
    return "redirect:/login?registered";
  }
}
