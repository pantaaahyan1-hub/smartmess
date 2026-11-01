package edu.smartmess;

import static org.assertj.core.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Real embedded Tomcat, HTML forms, session cookies and redirects; no MockMvc or synthetic CSRF.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:login-http;DB_CLOSE_DELAY=-1;NON_KEYWORDS=DAY",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class LoginHttpIntegrationTest {
  @LocalServerPort int port;

  @DynamicPropertySource
  static void windowsNetworking(DynamicPropertyRegistry registry) {
    if (System.getProperty("os.name").startsWith("Windows"))
      System.setProperty("jdk.net.unixdomain.tmpdir", "C:\\smartmess-no-unix-sockets\\unavailable");
  }

  final class Browser {
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    final HttpClient http =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    HttpResponse<String> get(String path) throws Exception {
      return send(path, null);
    }

    HttpResponse<String> send(String path, Map<String, String> form) throws Exception {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(30));
      if (form == null) request.GET();
      else {
        String body =
            form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        request
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body));
      }
      return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    String session() {
      return cookies.getCookieStore().getCookies().stream()
          .filter(c -> c.getName().equals("JSESSIONID"))
          .findFirst()
          .orElseThrow()
          .getValue();
    }

    HttpResponse<String> login(String account, String token) throws Exception {
      return send(
          "/login",
          Map.of(
              "username",
              account + "@smartmess.local",
              "password",
              "SmartMess@123",
              "_csrf",
              token));
    }
  }

  static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  static String token(String html) {
    Matcher m = Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
    assertThat(m.find()).as("Thymeleaf must render the real session CSRF hidden input").isTrue();
    return m.group(1);
  }

  static String location(HttpResponse<?> response) {
    return response.headers().firstValue("location").orElse("");
  }

  @ParameterizedTest
  @CsvSource({
    "student,STUDENT,/student/dashboard",
    "admin,ADMIN,/admin/dashboard",
    "staff,STAFF,/staff/scanner"
  })
  void realFormLoginSessionRotationDashboardsAndLogout(
      String account, String role, String dashboard) throws Exception {
    Browser b = new Browser();
    var login = b.get("/login");
    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(login.headers().firstValue("cache-control").orElse("")).contains("no-store");
    String before = b.session();
    var submitted = b.login(account, token(login.body()));
    assertThat(submitted.statusCode()).isEqualTo(302);
    assertThat(location(submitted)).endsWith(dashboard);
    assertThat(b.session()).isNotEqualTo(before);
    var page = b.get(dashboard);
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).contains("/js/app.js", "name=\"_csrf\"");
    assertThat(b.get("/api/me").body()).contains("\"role\":\"" + role + "\"");
    assertThat(location(b.get("/"))).endsWith(dashboard);
    assertThat(location(b.get("/login"))).endsWith(dashboard);
    assertThat(b.get("/api/admin/data").statusCode()).isEqualTo(role.equals("ADMIN") ? 200 : 403);
    if (!role.equals("ADMIN")) assertThat(b.get("/admin/dashboard").statusCode()).isEqualTo(403);
    if (!role.equals("STUDENT"))
      assertThat(b.get("/student/dashboard").statusCode()).isEqualTo(403);
    assertThat(b.send("/api/budget", Map.of("amount", "100")).statusCode()).isEqualTo(403);
    var logout = b.send("/logout", Map.of("_csrf", token(page.body())));
    assertThat(logout.statusCode()).isEqualTo(302);
    assertThat(location(b.get(dashboard))).endsWith("/login");
    assertThat(b.get("/api/me").statusCode()).isEqualTo(302);
  }

  @Test
  void staleTokenFromAnExpiredOrDifferentSessionRefreshesFormWithoutAuthenticating()
      throws Exception {
    Browser old = new Browser();
    String stale = token(old.get("/login").body());
    Browser fresh = new Browser(); // An old tab after cookie loss, logout or server restart.
    var rejected = fresh.login("student", stale);
    assertThat(rejected.statusCode()).isEqualTo(303);
    assertThat(location(rejected)).isEqualTo("/login?expired");
    assertThat(fresh.get("/api/me").statusCode()).isEqualTo(302);
    var newForm = fresh.get("/login?expired");
    assertThat(newForm.body()).contains("session expired or changed").doesNotContain("Whitelabel");
    assertThat(location(fresh.login("student", token(newForm.body()))))
        .endsWith("/student/dashboard");
    assertThat(fresh.get("/student/dashboard").statusCode()).isEqualTo(200);
  }

  @Test
  void missingAndInvalidCsrfNeverAuthenticatesOrDisablesOtherCsrfChecks() throws Exception {
    Browser b = new Browser();
    assertThat(
            b.send(
                    "/login",
                    Map.of("username", "student@smartmess.local", "password", "SmartMess@123"))
                .statusCode())
        .isEqualTo(303);
    assertThat(b.get("/api/me").statusCode()).isEqualTo(302);
    assertThat(location(b.send("/register", Map.of("_csrf", "invalid"))))
        .isEqualTo("/register?expired");
    assertThat(b.send("/api/selection", Map.of()).statusCode()).isEqualTo(403);
  }

  @Test
  void invalidPasswordUsesLoginErrorRatherThanForbiddenAndCanRetry() throws Exception {
    Browser b = new Browser();
    String csrf = token(b.get("/login").body());
    var bad =
        b.send(
            "/login",
            Map.of("username", "student@smartmess.local", "password", "incorrect", "_csrf", csrf));
    assertThat(bad.statusCode()).isEqualTo(302);
    assertThat(location(bad)).endsWith("/login?error");
    var retry = b.get("/login?error");
    assertThat(retry.statusCode()).isEqualTo(200);
    assertThat(location(b.login("student", token(retry.body())))).endsWith("/student/dashboard");
  }

  @Test
  void publicPagesAndEveryBrowserAssetLoadWithoutAuthentication() throws Exception {
    Browser b = new Browser();
    for (String path :
        List.of(
            "/login",
            "/register",
            "/css/app.css",
            "/js/app.js",
            "/vendor/bootstrap.min.css",
            "/vendor/chart.umd.js")) assertThat(b.get(path).statusCode()).as(path).isEqualTo(200);
    assertThat(b.get("/student/dashboard").statusCode()).isEqualTo(302);
    assertThat(b.get("/admin/dashboard").statusCode()).isEqualTo(302);
    assertThat(b.get("/staff/scanner").statusCode()).isEqualTo(302);
  }
}
