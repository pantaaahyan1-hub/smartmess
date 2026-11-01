package edu.smartmess;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:security;DB_CLOSE_DELAY=-1;NON_KEYWORDS=DAY",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class SecurityAndFaceTest {
  @Autowired MockMvc mvc;
  @Autowired FaceService face;
  @Autowired BiometricVault vault;
  @Autowired People people;
  @Autowired Faces faces;
  @Autowired MessService mess;
  @Autowired Attendances attendance;
  @Autowired Ledgers ledger;

  @Test
  void loginRenders() throws Exception {
    mvc.perform(get("/login")).andExpect(status().isOk());
  }

  @Test
  void anonymousCannotReadProfile() throws Exception {
    mvc.perform(get("/api/me")).andExpect(status().is3xxRedirection());
  }

  @Test
  @WithMockUser(username = "student@smartmess.local", roles = "STUDENT")
  void studentCannotCreditWallet() throws Exception {
    mvc.perform(
            post("/api/admin/topup")
                .with(csrf())
                .contentType("application/json")
                .content("{\"studentId\":3,\"amount\":100}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "student@smartmess.local", roles = "STUDENT")
  void csrfRequired() throws Exception {
    mvc.perform(post("/api/budget").contentType("application/json").content("{\"amount\":100}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "staff@smartmess.local", roles = "STAFF")
  void staffCannotManageStudents() throws Exception {
    mvc.perform(get("/api/admin/data")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "student@smartmess.local", roles = "STUDENT")
  void challengeRequiredForEnrollment() throws Exception {
    mvc.perform(
            post("/api/face/enroll")
                .with(csrf())
                .contentType("application/json")
                .content("{\"images\":[],\"consent\":true,\"nonce\":\"fake\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void biometricEncryptionRoundTrip() {
    byte[] original = {1, 2, 3, 4};
    byte[] encrypted = vault.encrypt(original);
    assertThat(encrypted).isNotEqualTo(original);
    assertThat(vault.decrypt(encrypted)).isEqualTo(original);
    encrypted[13] ^= 1;
    assertThatThrownBy(() -> vault.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void actualOpenCvRejectsBlankFrame() { // A valid empty image exercises native OpenCV decode and
    // detection without a webcam.
    try (var mat =
            new org.bytedeco.opencv.opencv_core.Mat(
                480,
                640,
                org.bytedeco.opencv.global.opencv_core.CV_8UC3,
                new org.bytedeco.opencv.opencv_core.Scalar(120, 120, 120, 0));
        var bytes = new org.bytedeco.javacpp.BytePointer()) {
      org.bytedeco.opencv.global.opencv_imgcodecs.imencode(".jpg", mat, bytes);
      byte[] jpg = new byte[(int) bytes.limit()];
      bytes.get(jpg);
      assertThatThrownBy(() -> face.crop(java.util.Base64.getEncoder().encodeToString(jpg)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("found 0");
    }
  }

  @Test
  void realOpenCvEnrollmentRecognitionAndDeletion() throws Exception {
    // NASA public-domain astronaut fixture translated/relit to exercise Java detection and LBPH.
    // These generated test frames are NOT a webcam/liveness verification and are never used by the
    // app.
    var samples = new java.util.ArrayList<String>();
    try (var input = getClass().getResourceAsStream("/astronaut.png");
        var pointer = new org.bytedeco.javacpp.BytePointer(input.readAllBytes());
        var raw =
            new org.bytedeco.opencv.opencv_core.Mat(
                1, (int) pointer.limit(), org.bytedeco.opencv.global.opencv_core.CV_8U, pointer);
        var original = org.bytedeco.opencv.global.opencv_imgcodecs.imdecode(raw, 1)) {
      for (int i = 0; i < 6; i++) {
        try (var shifted = new org.bytedeco.opencv.opencv_core.Mat();
            var transform =
                new org.bytedeco.opencv.opencv_core.Mat(
                    2, 3, org.bytedeco.opencv.global.opencv_core.CV_64F);
            var doubles = new org.bytedeco.javacpp.DoublePointer(transform.data());
            var out = new org.bytedeco.javacpp.BytePointer()) {
          doubles.put(new double[] {1, 0, i * 8 - 20, 0, 1, 0});
          org.bytedeco.opencv.global.opencv_imgproc.warpAffine(
              original, shifted, transform, original.size());
          shifted.convertTo(shifted, -1, 1, i * 2);
          org.bytedeco.opencv.global.opencv_imgcodecs.imencode(".jpg", shifted, out);
          byte[] bytes = new byte[(int) out.limit()];
          out.get(bytes);
          samples.add(java.util.Base64.getEncoder().encodeToString(bytes));
        }
      }
    }
    Person p = new Person();
    p.email = "native-test@smartmess.local";
    p.name = "Native fixture";
    p.password = "unused";
    p.wallet = new java.math.BigDecimal("200.00");
    p.dailyBudget = new java.math.BigDecimal("200.00");
    p = people.save(p);
    final Long id = p.id;
    assertThatThrownBy(() -> face.enroll(id, samples, false)).hasMessageContaining("consent");
    face.enroll(id, samples, true);
    assertThat(faces.findByStudentId(id)).hasSize(6);
    assertThat(face.recognize(samples).studentId()).isEqualTo(id);
    // Real native matching feeds the same transactional billing service used by the scanner.
    var lunch = java.time.LocalDate.of(2035, 1, 2).atTime(13, 0);
    assertThat(mess.admitAt(face.recognize(samples).studentId(), lunch).get("granted"))
        .isEqualTo(true);
    assertThat(mess.admitAt(face.recognize(samples).studentId(), lunch).get("granted"))
        .isEqualTo(false);
    assertThat(people.findById(id).orElseThrow().wallet).isEqualByComparingTo("135.00");
    assertThat(attendance.findByStudentIdOrderByScannedAtDesc(id)).hasSize(1);
    assertThat(ledger.findByStudentIdOrderByCreatedAtDesc(id)).hasSize(1);
    face.delete(id);
    assertThat(faces.findByStudentId(id)).isEmpty();
    assertThat(people.findById(id).orElseThrow().biometricConsent).isFalse();
  }
}
