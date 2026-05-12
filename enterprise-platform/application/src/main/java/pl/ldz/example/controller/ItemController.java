package pl.ldz.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.ldz.example.dto.ItemRequest;
import pl.ldz.example.dto.ItemResponse;
import pl.ldz.example.service.ItemService;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
@Tag(name = "Items", description = "CRUD operations for items")
public class ItemController {

  private final ItemService itemService;

  @Operation(summary = "List all items", description = "Returns all items, or filters by name when `search` is provided.")
  @ApiResponse(responseCode = "200", description = "Successful retrieval",
      content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemResponse.class))))
  @GetMapping
  public List<ItemResponse> getAll(
      @Parameter(description = "Optional full-text search term")
      @RequestParam(required = false) String search) {
    if (search != null && !search.isBlank()) {
      return itemService.search(search);
    }
    return itemService.findAll();
  }

  @Operation(summary = "Get item by ID")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Item found",
          content = @Content(schema = @Schema(implementation = ItemResponse.class))),
      @ApiResponse(responseCode = "404", description = "Item not found",
          content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @GetMapping("/{id}")
  public ItemResponse getById(
      @Parameter(description = "Item ID", required = true)
      @PathVariable Long id) {
    return itemService.findById(id);
  }

  @Operation(summary = "Create a new item")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Item created",
          content = @Content(schema = @Schema(implementation = ItemResponse.class))),
      @ApiResponse(responseCode = "400", description = "Validation error",
          content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ItemResponse create(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "Item data", required = true,
          content = @Content(schema = @Schema(implementation = ItemRequest.class)))
      @Valid @RequestBody ItemRequest request) {
    return itemService.create(request);
  }

  @Operation(summary = "Update an existing item")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Item updated",
          content = @Content(schema = @Schema(implementation = ItemResponse.class))),
      @ApiResponse(responseCode = "400", description = "Validation error",
          content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
      @ApiResponse(responseCode = "404", description = "Item not found",
          content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PutMapping("/{id}")
  public ItemResponse update(
      @Parameter(description = "Item ID", required = true) @PathVariable Long id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "Updated item data", required = true,
          content = @Content(schema = @Schema(implementation = ItemRequest.class)))
      @Valid @RequestBody ItemRequest request) {
    return itemService.update(id, request);
  }

  @Operation(summary = "Delete an item")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Item deleted"),
      @ApiResponse(responseCode = "404", description = "Item not found",
          content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "Item ID", required = true) @PathVariable Long id) {
    itemService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
