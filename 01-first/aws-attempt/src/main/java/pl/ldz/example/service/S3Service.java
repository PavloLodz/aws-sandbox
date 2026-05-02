package pl.ldz.example.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

  private final S3Client s3Client;

  @Value("${aws.s3.bucket}")
  private String bucket;

  /**
   * Uploads a file to S3 and returns the generated S3 key.
   * The key format is: items/{itemId}/{uuid}-{originalFilename}
   */
  public String upload(Long itemId, MultipartFile file) {
    String key = buildKey(itemId, file.getOriginalFilename());
    try {
      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .contentType(file.getContentType())
              .contentLength(file.getSize())
              .build(),
          RequestBody.fromBytes(file.getBytes())
      );
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read uploaded file: " + e.getMessage(), e);
    }
    return key;
  }

  /**
   * Downloads a file from S3 by key.
   */
  public ResponseBytes<GetObjectResponse> download(String key) {
    return s3Client.getObjectAsBytes(
        GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()
    );
  }

  /**
   * Deletes a file from S3 by key.
   */
  public void delete(String key) {
    s3Client.deleteObject(
        DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()
    );
  }

  private String buildKey(Long itemId, String originalFilename) {
    String sanitized = originalFilename == null ? "file" :
        originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    return "items/" + itemId + "/" + UUID.randomUUID() + "-" + sanitized;
  }
}
