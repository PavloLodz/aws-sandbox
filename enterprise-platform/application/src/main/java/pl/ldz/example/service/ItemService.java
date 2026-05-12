package pl.ldz.example.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ldz.example.dto.ItemRequest;
import pl.ldz.example.dto.ItemResponse;
import pl.ldz.example.model.Item;
import pl.ldz.example.repository.ItemRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ItemService {

  private final ItemRepository itemRepository;

  public List<ItemResponse> findAll() {
    return itemRepository.findAll().stream()
        .map(ItemResponse::from)
        .toList();
  }

  public ItemResponse findById(Long id) {
    return itemRepository.findById(id)
        .map(ItemResponse::from)
        .orElseThrow(() -> new NoSuchElementException("Item not found: " + id));
  }

  public List<ItemResponse> search(String name) {
    return itemRepository.findByNameContainingIgnoreCase(name).stream()
        .map(ItemResponse::from)
        .toList();
  }

  @Transactional
  public ItemResponse create(ItemRequest request) {
    Instant now = Instant.now();
    Item item = Item.builder()
        .name(request.name())
        .description(request.description())
        .createdAt(now)
        .updatedAt(now)
        .build();
    return ItemResponse.from(itemRepository.save(item));
  }

  @Transactional
  public ItemResponse update(Long id, ItemRequest request) {
    Item item = itemRepository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("Item not found: " + id));
    item.setName(request.name());
    item.setDescription(request.description());
    item.setUpdatedAt(Instant.now());
    return ItemResponse.from(itemRepository.save(item));
  }

  @Transactional
  public void delete(Long id) {
    if (!itemRepository.existsById(id)) {
      throw new NoSuchElementException("Item not found: " + id);
    }
    itemRepository.deleteById(id);
  }
}
