package pl.ldz.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for creating or updating an item")
public record ItemRequest(

    @NotBlank @Size(max = 255)
    @Schema(description = "Item name", example = "Widget A", maxLength = 255, requiredMode = Schema.RequiredMode.REQUIRED)
    String name,

    @Size(max = 1000)
    @Schema(description = "Optional description", example = "A handy widget", maxLength = 1000)
    String description

) {}
