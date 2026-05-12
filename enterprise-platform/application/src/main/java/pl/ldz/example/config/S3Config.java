package pl.ldz.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class S3Config {

  @Value("${aws.s3.region}")
  private String region;

  /**
   * Optional endpoint override — set only in tests pointing at LocalStack.
   * In production (EC2 with IAM role) this property is absent and defaults to null.
   */
  @Value("${aws.s3.endpoint-override:#{null}}")
  private String endpointOverride;

  /**
   * Optional static credentials — used only with LocalStack.
   * In production credentials are resolved from the EC2 IAM role automatically.
   */
  @Value("${aws.s3.access-key:#{null}}")
  private String accessKey;

  @Value("${aws.s3.secret-key:#{null}}")
  private String secretKey;

  @Bean
  public S3Client s3Client() {
    var builder = S3Client.builder()
        .region(Region.of(region));

    if (endpointOverride != null && !endpointOverride.isBlank()) {
      // LocalStack / test mode: use custom endpoint with path-style access
      builder
          .endpointOverride(URI.create(endpointOverride))
          .serviceConfiguration(S3Configuration.builder()
              .pathStyleAccessEnabled(true)
              .build());

      if (accessKey != null && secretKey != null) {
        builder.credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)));
      }
    }
    // Production: credentials resolved automatically from EC2 IAM role

    return builder.build();
  }
}
