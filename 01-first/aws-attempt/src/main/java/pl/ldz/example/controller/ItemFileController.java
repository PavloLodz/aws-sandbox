package pl.ldz.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.ldz.example.dto.ItemFileResponse;
import pl.ldz.example.service.ItemFileService;

import java.util.List;

@RestController
@RequestMapping("/api/items/{itemId}/files")
@RequiredArgsConstructor
@Tag(name = "Item Files", description = "Upload and manage files attached to items (stored in S3)")
public class ItemFileController {

  private final ItemFileService itemFileService;

  @Operation(summary = "List files attached to an item")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "File list returned",
          content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemFileResponse.class)))),
      @ApiResponse(responseCode = "404", description = "Item not found",
          content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @GetMapping
  public List<ItemFileResponse> listFiles(
      @Parameter(description = "Item ID") @PathVariable Long itemId) {
    return itemFileService.listFiles(itemId);
  }

  @Operation(summary = "Upload a file and attach it to an item")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "File uploaded",
          content = @Content(schema = @Schema(implementation = ItemFileResponse.class))),
      @ApiResponse(responseCode = "404", description = "Item not found",
          content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ItemFileResponse upload(
      @Parameter(description = "Item ID") @PathVariable Long itemId,
      @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file) {
    return itemFileService.upload(itemId, file);
  }

  @Operation(summary = "Download a file attached to an item")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "File content returned"),
      @ApiResponse(responseCode = "404", description = "Item or file not found",
          content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @GetMapping("/{fileId}")
  public ResponseEntity<byte[]> download(
      @Parameter(description = "Item ID") @PathVariable Long itemId,
      @Parameter(description = "File ID") @PathVariable Long fileId) {
    return itemFileService.download(itemId, fileId);
  }

  @Operation(summary = "Delete a file from S3 and remove its record")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "File deleted"),
      @ApiResponse(responseCode = "404", description = "Item or file not found",
          content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @DeleteMapping("/{fileId}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "Item ID") @PathVariable Long itemId,
      @Parameter(description = "File ID") @PathVariable Long fileId) {
    itemFileService.delete(itemId, fileId);
    return ResponseEntity.noContent().build();
  }
}
