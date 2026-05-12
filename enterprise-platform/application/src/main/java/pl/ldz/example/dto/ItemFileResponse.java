package pl.ldz.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.ldz.example.model.ItemFile;

import java.time.Instant;

@Schema(description = "Metadata of a file attached to an item")
public record ItemFileResponse(

    @Schema(description = "File record ID", example = "1")
    Long id,

    @Schema(description = "Original filename as uploaded", example = "photo.jpg")
    String originalName,

    @Schema(description = "MIME content type", example = "image/jpeg")
    String contentType,

    @Schema(description = "File size in bytes", example = "204800")
    Long sizeBytes,

    @Schema(description = "Upload timestamp (UTC)")
    Instant uploadedAt

) {
  public static ItemFileResponse from(ItemFile f) {
    return new ItemFileResponse(
        f.getId(),
        f.getOriginalName(),
        f.getContentType(),
        f.getSizeBytes(),
        f.getUploadedAt()
    );
  }
}
