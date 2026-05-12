package pl.ldz.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.ldz.example.dto.ItemRequest;
import pl.ldz.example.dto.ItemResponse;
import pl.ldz.example.service.ItemService;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ItemController.class, GlobalExceptionHandler.class})
class ItemControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private ItemService itemService;

  private ItemResponse sampleResponse;

  @BeforeEach
  void setUp() {
    sampleResponse = new ItemResponse(
        1L,
        "Widget A",
        "A handy widget",
        Instant.parse("2024-01-01T10:00:00Z"),
        Instant.parse("2024-01-01T10:00:00Z")
    );
  }

  // ---------------------------------------------------------------------------
  // GET /api/items
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("GET /api/items")
  class GetAll {

    @Test
    @DisplayName("returns 200 with list of all items when no search param")
    void returnsAllItems() throws Exception {
      when(itemService.findAll()).thenReturn(List.of(sampleResponse));

      mockMvc.perform(get("/api/items"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].id").value(1))
          .andExpect(jsonPath("$[0].name").value("Widget A"));
    }

    @Test
    @DisplayName("returns 200 with filtered items when search param provided")
    void returnsFilteredItems() throws Exception {
      when(itemService.search("widget")).thenReturn(List.of(sampleResponse));

      mockMvc.perform(get("/api/items").param("search", "widget"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].name").value("Widget A"));
    }

    @Test
    @DisplayName("calls findAll when search param is blank")
    void callsFindAllForBlankSearch() throws Exception {
      when(itemService.findAll()).thenReturn(List.of(sampleResponse));

      mockMvc.perform(get("/api/items").param("search", "   "))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("returns 200 with empty array when no items exist")
    void returnsEmptyList() throws Exception {
      when(itemService.findAll()).thenReturn(List.of());

      mockMvc.perform(get("/api/items"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }
  }

  // ---------------------------------------------------------------------------
  // GET /api/items/{id}
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("GET /api/items/{id}")
  class GetById {

    @Test
    @DisplayName("returns 200 with item when found")
    void returnsItem() throws Exception {
      when(itemService.findById(1L)).thenReturn(sampleResponse);

      mockMvc.perform(get("/api/items/1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(jsonPath("$.name").value("Widget A"))
          .andExpect(jsonPath("$.description").value("A handy widget"));
    }

    @Test
    @DisplayName("returns 404 when item not found")
    void returns404WhenNotFound() throws Exception {
      when(itemService.findById(99L)).thenThrow(new NoSuchElementException("Item not found: 99"));

      mockMvc.perform(get("/api/items/99"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.detail").value("Item not found: 99"));
    }
  }

  // ---------------------------------------------------------------------------
  // POST /api/items
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("POST /api/items")
  class Create {

    @Test
    @DisplayName("returns 201 with created item on valid request")
    void createsItem() throws Exception {
      ItemRequest request = new ItemRequest("New Widget", "A new one");
      when(itemService.create(any(ItemRequest.class))).thenReturn(sampleResponse);

      mockMvc.perform(post("/api/items")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(jsonPath("$.name").value("Widget A"));
    }

    @Test
    @DisplayName("returns 400 when name is blank")
    void returns400ForBlankName() throws Exception {
      ItemRequest invalid = new ItemRequest("", "desc");

      mockMvc.perform(post("/api/items")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalid)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @DisplayName("returns 400 when name is null")
    void returns400ForNullName() throws Exception {
      String body = "{\"description\": \"no name\"}";

      mockMvc.perform(post("/api/items")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("returns 400 when name exceeds 255 characters")
    void returns400ForNameTooLong() throws Exception {
      ItemRequest invalid = new ItemRequest("x".repeat(256), "desc");

      mockMvc.perform(post("/api/items")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalid)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("accepts null description")
    void acceptsNullDescription() throws Exception {
      ItemRequest request = new ItemRequest("Widget", null);
      when(itemService.create(any(ItemRequest.class))).thenReturn(sampleResponse);

      mockMvc.perform(post("/api/items")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated());
    }
  }

  // ---------------------------------------------------------------------------
  // PUT /api/items/{id}
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("PUT /api/items/{id}")
  class Update {

    @Test
    @DisplayName("returns 200 with updated item on valid request")
    void updatesItem() throws Exception {
      ItemRequest request = new ItemRequest("Updated", "Updated desc");
      ItemResponse updated = new ItemResponse(1L, "Updated", "Updated desc",
          sampleResponse.createdAt(), Instant.now());
      when(itemService.update(eq(1L), any(ItemRequest.class))).thenReturn(updated);

      mockMvc.perform(put("/api/items/1")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    @DisplayName("returns 404 when item not found")
    void returns404WhenNotFound() throws Exception {
      ItemRequest request = new ItemRequest("X", null);
      when(itemService.update(eq(99L), any(ItemRequest.class)))
          .thenThrow(new NoSuchElementException("Item not found: 99"));

      mockMvc.perform(put("/api/items/99")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("returns 400 when name is blank")
    void returns400ForBlankName() throws Exception {
      ItemRequest invalid = new ItemRequest("  ", null);

      mockMvc.perform(put("/api/items/1")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalid)))
          .andExpect(status().isBadRequest());
    }
  }

  // ---------------------------------------------------------------------------
  // DELETE /api/items/{id}
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("DELETE /api/items/{id}")
  class Delete {

    @Test
    @DisplayName("returns 204 on successful delete")
    void deletesItem() throws Exception {
      doNothing().when(itemService).delete(1L);

      mockMvc.perform(delete("/api/items/1"))
          .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("returns 404 when item not found")
    void returns404WhenNotFound() throws Exception {
      doThrow(new NoSuchElementException("Item not found: 99"))
          .when(itemService).delete(99L);

      mockMvc.perform(delete("/api/items/99"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.detail").value("Item not found: 99"));
    }
  }
}
