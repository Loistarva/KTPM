package com.ktpm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KtpmApplication {
  public static void main(String[] args) {
    SpringApplication.run(KtpmApplication.class, args);
  }
}
