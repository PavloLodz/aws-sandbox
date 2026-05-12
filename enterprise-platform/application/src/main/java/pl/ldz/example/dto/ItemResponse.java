package pl.ldz.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.ldz.example.model.Item;

import java.time.Instant;

@Schema(description = "Represents a stored item returned by the API")
public record ItemResponse(

    @Schema(description = "Unique identifier", example = "1")
    Long id,

    @Schema(description = "Item name", example = "Widget A")
    String name,

    @Schema(description = "Optional description", example = "A handy widget")
    String description,

    @Schema(description = "Creation timestamp (UTC)")
    Instant createdAt,

    @Schema(description = "Last update timestamp (UTC)")
    Instant updatedAt

) {
  public static ItemResponse from(Item item) {
    return new ItemResponse(
        item.getId(),
        item.getName(),
        item.getDescription(),
        item.getCreatedAt(),
        item.getUpdatedAt()
    );
  }
}
