package edu.smartmess;

import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

@Configuration
public class Security {
  @Bean
  public PasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public UserDetailsService users(People people) {
    return email -> {
      Person p =
          people
              .findByEmail(email.trim().toLowerCase(Locale.ROOT))
              .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
      return User.withUsername(p.email)
          .password(p.password)
          .roles(p.role)
          .disabled(!p.active)
          .build();
    };
  }

  @Bean
  public SecurityFilterChain filter(HttpSecurity h) throws Exception {
    return h.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/login", "/register", "/css/**", "/js/**", "/vendor/**", "/error")
                    .permitAll()
                    .requestMatchers("/student/**")
                    .hasRole("STUDENT")
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/staff/**")
                    .hasAnyRole("ADMIN", "STAFF")
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/scanner/**")
                    .hasAnyRole("ADMIN", "STAFF")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginPage("/login")
                    .loginProcessingUrl("/login")
                    .failureUrl("/login?error")
                    .successHandler(
                        (request, response, authentication) -> {
                          new HttpSessionRequestCache().removeRequest(request, response);
                          response.sendRedirect(
                              request.getContextPath() + dashboard(authentication));
                        })
                    .permitAll())
        .exceptionHandling(
            e ->
                e.accessDeniedHandler(
                    (request, response, exception) -> {
                      // Reject the unsafe POST; issue a fresh form rather than retrying credentials
                      // automatically.
                      String path = request.getServletPath();
                      if (exception instanceof CsrfException
                          && "POST".equals(request.getMethod())
                          && ("/login".equals(path) || "/register".equals(path))) {
                        response.setStatus(303);
                        response.setHeader(
                            "Location", request.getContextPath() + path + "?expired");
                      } else if (path.startsWith("/api/")) {
                        response.setStatus(403);
                        response.setContentType("application/json");
                        response
                            .getWriter()
                            .write(
                                "{\"message\":\"Access denied or security token expired. Refresh"
                                    + " the page and try again.\"}");
                      } else {
                        response.sendError(403, "You do not have permission to access this page.");
                      }
                    }))
        .logout(l -> l.logoutSuccessUrl("/login"))
        .build();
  }

  public static String dashboard(Authentication authentication) {
    if (authentication.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) return "/admin/dashboard";
    if (authentication.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"))) return "/staff/scanner";
    return "/student/dashboard";
  }
}
