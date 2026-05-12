package pl.ldz.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class AwsAttemptApplication {

  public static void main(String[] args) {
    log.info("Starting AwsAttemptApplication application just for log");

    SpringApplication.run(AwsAttemptApplication.class, args);

    log.info("AwsAttemptApplication application started successfully");
  }
}
