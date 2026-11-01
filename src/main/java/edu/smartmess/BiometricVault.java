package edu.smartmess;

import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.stereotype.Component;

@Component
public class BiometricVault {
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public BiometricVault() throws Exception {
    Path p = Path.of("data", "biometric.key");
    Files.createDirectories(p.getParent());
    if (!Files.exists(p)) {
      byte[] b = new byte[32];
      random.nextBytes(b);
      Files.write(p, b, StandardOpenOption.CREATE_NEW);
    }
    key = new SecretKeySpec(Files.readAllBytes(p), "AES");
  }

  public byte[] encrypt(byte[] bytes) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] enc = c.doFinal(bytes);
      byte[] all = Arrays.copyOf(iv, iv.length + enc.length);
      System.arraycopy(enc, 0, all, iv.length, enc.length);
      return all;
    } catch (Exception e) {
      throw new IllegalStateException("Cannot encrypt biometric", e);
    }
  }

  public byte[] decrypt(byte[] bytes) {
    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
      return c.doFinal(Arrays.copyOfRange(bytes, 12, bytes.length));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot decrypt biometric", e);
    }
  }
}
