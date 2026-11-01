package edu.smartmess;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

import java.nio.file.*;
import java.util.*;
import java.util.Arrays;
import org.bytedeco.javacpp.*;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FaceService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FaceService.class);
  private final Faces faces;
  private final People people;
  private final BiometricVault vault;
  private final CascadeClassifier detector;
  public final double threshold;

  public FaceService(
      Faces f, People p, BiometricVault v, @Value("${smartmess.face.threshold}") double t)
      throws Exception {
    faces = f;
    people = p;
    vault = v;
    threshold = t;
    Path cascade = Path.of("src/main/resources/haarcascade_frontalface_default.xml");
    if (!Files.exists(cascade)) {
      cascade = Path.of("data/haarcascade.xml");
      try (var in = getClass().getResourceAsStream("/haarcascade_frontalface_default.xml")) {
        if (in == null) throw new IllegalStateException("Face cascade missing");
        Files.copy(in, cascade, StandardCopyOption.REPLACE_EXISTING);
      }
    }
    detector = new CascadeClassifier(cascade.toAbsolutePath().toString());
    if (detector.empty()) throw new IllegalStateException("Cannot load face cascade");
  }

  public record Crop(byte[] pixels, double center, double size, double brightness) {}

  public synchronized Crop crop(String data) {
    byte[] jpg;
    try {
      jpg = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid camera image");
    }
    if (jpg.length > 700000) throw new IllegalArgumentException("Camera frame too large");
    try (BytePointer ptr = new BytePointer(jpg);
        Mat raw = new Mat(1, jpg.length, CV_8U, ptr);
        Mat color = imdecode(raw, IMREAD_COLOR);
        Mat gray = new Mat();
        RectVector boxes = new RectVector();
        Mat resized = new Mat()) {
      if (color.empty() || color.cols() > 1920 || color.rows() > 1080)
        throw new IllegalArgumentException("Invalid camera frame dimensions");
      cvtColor(color, gray, COLOR_BGR2GRAY);
      detector.detectMultiScale(gray, boxes, 1.1, 5, 0, new Size(80, 80), new Size());
      if (boxes.size() == 0) {
        // Retry low-contrast or more distant webcam faces without relaxing one-face validation.
        equalizeHist(gray, gray);
        detector.detectMultiScale(gray, boxes, 1.05, 6, 0, new Size(60, 60), new Size());
      }
      if (boxes.size() != 1) {
        log.debug(
            "Face detection rejected frame: width={}, height={}, faces={}",
            color.cols(),
            color.rows(),
            boxes.size());
        throw new IllegalArgumentException(
            "Show exactly one clear, front-facing face (found "
                + boxes.size()
                + "). Face the camera, move closer and use light in front of you. Captured frame: "
                + color.cols()
                + " × "
                + color.rows());
      }
      Rect r = boxes.get(0);
      try (Mat roi = new Mat(gray, r)) {
        resize(roi, resized, new Size(160, 160));
        double bright = mean(resized).get(0);
        if (bright < 35 || bright > 225)
          throw new IllegalArgumentException("Improve lighting; face is too dark or overexposed");
        equalizeHist(resized, resized);
        byte[] pixels = new byte[25600];
        resized.data().get(pixels);
        return new Crop(
            pixels,
            (r.x() + r.width() / 2.0) / gray.cols(),
            r.width() / (double) gray.cols(),
            bright);
      }
    }
  }

  public List<Crop> validate(List<String> images) {
    if (images == null || images.size() < 6 || images.size() > 12)
      throw new IllegalArgumentException("Capture 6–12 live frames");
    List<Crop> samples = new ArrayList<>();
    Set<Integer> frameHashes = new HashSet<>();
    double min = Double.MAX_VALUE;
    double max = -Double.MAX_VALUE;
    for (String image : images) {
      Crop sample = crop(image);
      samples.add(sample);
      min = Math.min(min, sample.center());
      max = Math.max(max, sample.center());
      frameHashes.add(Arrays.hashCode(sample.pixels()));
    }
    if (max - min < 0.035)
      throw new IllegalArgumentException(
          "Movement check failed. Slowly move your head left and right while capturing.");
    if (frameHashes.size() < 5)
      throw new IllegalArgumentException("Repeated static frames rejected");
    return samples;
  }

  @Transactional
  public synchronized void enroll(Long id, List<String> images, boolean consent) {
    if (!consent) throw new IllegalArgumentException("Explicit biometric consent is required");
    Person p = people.locked(id).orElseThrow();
    if (!p.role.equals("STUDENT"))
      throw new IllegalArgumentException("Only student accounts can enroll");
    List<Crop> samples = validate(images);
    faces.deleteByStudentId(id);
    for (Crop c : samples) {
      FaceSample s = new FaceSample();
      s.studentId = id;
      s.encrypted = vault.encrypt(c.pixels);
      faces.save(s);
    }
    p.biometricConsent = true;
    people.save(p);
  }

  @Transactional
  public synchronized void delete(Long id) {
    Person p = people.locked(id).orElseThrow();
    faces.deleteByStudentId(id);
    p.biometricConsent = false;
    people.save(p);
  }

  public record Match(Long studentId, double distance) {}

  public synchronized Match recognize(List<String> images) {
    List<Crop> probes = validate(images);
    List<FaceSample> training = new ArrayList<>();
    for (FaceSample sample : faces.findAll()) {
      Person student = people.findById(sample.studentId).orElse(null);
      if (student != null && student.active && student.biometricConsent) training.add(sample);
    }
    if (training.isEmpty())
      throw new IllegalArgumentException("No enrolled faces. Register a student's face first.");
    try (LBPHFaceRecognizer model = LBPHFaceRecognizer.create(1, 8, 8, 8, threshold);
        MatVector mats = new MatVector(training.size());
        Mat labels = new Mat(training.size(), 1, CV_32SC1);
        IntPointer ints = new IntPointer(labels.data());
        IntPointer label = new IntPointer(1);
        DoublePointer distance = new DoublePointer(1)) {
      List<Mat> allocated = new ArrayList<>();
      List<BytePointer> pointers = new ArrayList<>();
      try {
        for (int i = 0; i < training.size(); i++) {
          BytePointer bp = new BytePointer(vault.decrypt(training.get(i).encrypted));
          pointers.add(bp);
          Mat mat = new Mat(160, 160, CV_8UC1, bp);
          allocated.add(mat);
          mats.put(i, mat);
          ints.put(i, Math.toIntExact(training.get(i).studentId));
        }
        model.train(mats, labels);
        Map<Integer, List<Double>> votes = new HashMap<>();
        for (Crop c : probes) {
          try (BytePointer bp = new BytePointer(c.pixels);
              Mat probe = new Mat(160, 160, CV_8UC1, bp)) {
            model.predict(probe, label, distance);
            if (label.get(0) >= 0 && distance.get(0) < threshold)
              votes.computeIfAbsent(label.get(0), k -> new ArrayList<>()).add(distance.get(0));
          }
        }
        var winner =
            votes.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Face not recognized with sufficient confidence"));
        if (winner.getValue().size() < Math.ceil(probes.size() * 0.7))
          throw new IllegalArgumentException(
              "Recognition inconsistent. Improve lighting and retry.");
        return new Match(
            winner.getKey().longValue(),
            winner.getValue().stream().mapToDouble(d -> d).average().orElseThrow());
      } finally {
        allocated.forEach(Mat::close);
        pointers.forEach(BytePointer::close);
      }
    }
  }
}
