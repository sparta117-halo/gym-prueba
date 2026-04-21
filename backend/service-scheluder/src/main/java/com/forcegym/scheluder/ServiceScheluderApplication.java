package com.forcegym.scheluder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ServiceScheluderApplication {

  public static void main(String[] args) {
    SpringApplication.run(ServiceScheluderApplication.class, args);
  }
}