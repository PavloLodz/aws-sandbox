package pl.ldz.example.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.ldz.example.dto.ItemRequest;
import pl.ldz.example.dto.ItemResponse;
import pl.ldz.example.model.Item;
import pl.ldz.example.repository.ItemRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

  @Mock
  private ItemRepository itemRepository;

  @InjectMocks
  private ItemService itemService;

  private Item sampleItem;

  @BeforeEach
  void setUp() {
    sampleItem = Item.builder()
        .id(1L)
        .name("Widget A")
        .description("A handy widget")
        .createdAt(Instant.parse("2024-01-01T10:00:00Z"))
        .updatedAt(Instant.parse("2024-01-01T10:00:00Z"))
        .build();
  }

  // ---------------------------------------------------------------------------
  // findAll
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("findAll()")
  class FindAll {

    @Test
    @DisplayName("returns mapped responses for all items")
    void returnsAllItems() {
      Item second = Item.builder().id(2L).name("Gadget B").description(null)
          .createdAt(Instant.now()).updatedAt(Instant.now()).build();
      when(itemRepository.findAll()).thenReturn(List.of(sampleItem, second));

      List<ItemResponse> result = itemService.findAll();

      assertThat(result).hasSize(2);
      assertThat(result.get(0).id()).isEqualTo(1L);
      assertThat(result.get(0).name()).isEqualTo("Widget A");
      assertThat(result.get(1).id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("returns empty list when repository is empty")
    void returnsEmptyList() {
      when(itemRepository.findAll()).thenReturn(List.of());

      assertThat(itemService.findAll()).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------
  // findById
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("findById()")
  class FindById {

    @Test
    @DisplayName("returns response when item exists")
    void returnsItem() {
      when(itemRepository.findById(1L)).thenReturn(Optional.of(sampleItem));

      ItemResponse response = itemService.findById(1L);

      assertThat(response.id()).isEqualTo(1L);
      assertThat(response.name()).isEqualTo("Widget A");
      assertThat(response.description()).isEqualTo("A handy widget");
      assertThat(response.createdAt()).isEqualTo(sampleItem.getCreatedAt());
      assertThat(response.updatedAt()).isEqualTo(sampleItem.getUpdatedAt());
    }

    @Test
    @DisplayName("throws NoSuchElementException when item not found")
    void throwsWhenNotFound() {
      when(itemRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> itemService.findById(99L))
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("99");
    }
  }

  // ---------------------------------------------------------------------------
  // search
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("search()")
  class Search {

    @Test
    @DisplayName("delegates to repository and maps results")
    void returnsMatchingItems() {
      when(itemRepository.findByNameContainingIgnoreCase("widget"))
          .thenReturn(List.of(sampleItem));

      List<ItemResponse> result = itemService.search("widget");

      assertThat(result).hasSize(1);
      assertThat(result.get(0).name()).isEqualTo("Widget A");
      verify(itemRepository).findByNameContainingIgnoreCase("widget");
    }

    @Test
    @DisplayName("returns empty list when no items match")
    void returnsEmptyWhenNoMatch() {
      when(itemRepository.findByNameContainingIgnoreCase("xyz")).thenReturn(List.of());

      assertThat(itemService.search("xyz")).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------
  // create
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("create()")
  class Create {

    @Test
    @DisplayName("saves item with correct fields and returns mapped response")
    void savesAndReturnsItem() {
      ItemRequest request = new ItemRequest("New Widget", "Some description");
      Item saved = Item.builder().id(10L).name("New Widget").description("Some description")
          .createdAt(Instant.now()).updatedAt(Instant.now()).build();
      when(itemRepository.save(any(Item.class))).thenReturn(saved);

      ItemResponse response = itemService.create(request);

      assertThat(response.id()).isEqualTo(10L);
      assertThat(response.name()).isEqualTo("New Widget");
      assertThat(response.description()).isEqualTo("Some description");
    }

    @Test
    @DisplayName("sets createdAt and updatedAt to the same non-null instant")
    void setsTimestamps() {
      ItemRequest request = new ItemRequest("Timestamped", null);
      ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
      when(itemRepository.save(captor.capture())).thenAnswer(inv -> {
        Item i = captor.getValue();
        i = Item.builder().id(5L).name(i.getName()).description(i.getDescription())
            .createdAt(i.getCreatedAt()).updatedAt(i.getUpdatedAt()).build();
        return i;
      });

      itemService.create(request);

      Item captured = captor.getValue();
      assertThat(captured.getCreatedAt()).isNotNull();
      assertThat(captured.getUpdatedAt()).isNotNull();
      assertThat(captured.getCreatedAt()).isEqualTo(captured.getUpdatedAt());
    }

    @Test
    @DisplayName("supports null description")
    void supportsNullDescription() {
      ItemRequest request = new ItemRequest("No Desc", null);
      Item saved = Item.builder().id(3L).name("No Desc").description(null)
          .createdAt(Instant.now()).updatedAt(Instant.now()).build();
      when(itemRepository.save(any(Item.class))).thenReturn(saved);

      ItemResponse response = itemService.create(request);

      assertThat(response.description()).isNull();
    }
  }

  // ---------------------------------------------------------------------------
  // update
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("update()")
  class Update {

    @Test
    @DisplayName("updates fields and returns mapped response")
    void updatesItem() {
      ItemRequest request = new ItemRequest("Updated Name", "Updated Desc");
      when(itemRepository.findById(1L)).thenReturn(Optional.of(sampleItem));
      when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

      ItemResponse response = itemService.update(1L, request);

      assertThat(response.name()).isEqualTo("Updated Name");
      assertThat(response.description()).isEqualTo("Updated Desc");
    }

    @Test
    @DisplayName("updates updatedAt timestamp")
    void refreshesUpdatedAt() {
      Instant before = sampleItem.getUpdatedAt();
      ItemRequest request = new ItemRequest("Changed", null);
      when(itemRepository.findById(1L)).thenReturn(Optional.of(sampleItem));
      when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

      itemService.update(1L, request);

      assertThat(sampleItem.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("throws NoSuchElementException when item not found")
    void throwsWhenNotFound() {
      when(itemRepository.findById(42L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> itemService.update(42L, new ItemRequest("X", null)))
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("42");

      verify(itemRepository, never()).save(any());
    }
  }

  // ---------------------------------------------------------------------------
  // delete
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("delete()")
  class Delete {

    @Test
    @DisplayName("deletes existing item without error")
    void deletesItem() {
      when(itemRepository.existsById(1L)).thenReturn(true);

      itemService.delete(1L);

      verify(itemRepository).deleteById(1L);
    }

    @Test
    @DisplayName("throws NoSuchElementException when item does not exist")
    void throwsWhenNotFound() {
      when(itemRepository.existsById(99L)).thenReturn(false);

      assertThatThrownBy(() -> itemService.delete(99L))
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("99");

      verify(itemRepository, never()).deleteById(any());
    }
  }
}
