package com.samcomo.dbz.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Slf4j
@Configuration
public class FCMConfig {

  @Value("${fcm.key.path}")
  private String SERVICE_ACCOUNT_JSON;

  @PostConstruct
  public void init() {
    try {
      ClassPathResource resource = new ClassPathResource(SERVICE_ACCOUNT_JSON);
      InputStream serviceAccount = resource.getInputStream();

      FirebaseOptions options = FirebaseOptions.builder()
          .setCredentials(GoogleCredentials.fromStream(serviceAccount))
          .build();

      if (FirebaseApp.getApps().isEmpty()) {
        FirebaseApp.initializeApp(options);
        log.info("파이어베이스 연결 성공");
      } else {
        log.info("FirebaseApp is already initialized.");
      }
    } catch (IOException e) {
      log.error("파이어베이스 연결 실패", e);
    }
  }
}
