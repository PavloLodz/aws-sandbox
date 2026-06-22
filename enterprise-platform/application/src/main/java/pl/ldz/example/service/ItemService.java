package pl.ldz.example.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
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
  private final MeterRegistry  meterRegistry; // injected by Spring

  // Non-final — initialised in @PostConstruct after Spring sets the MeterRegistry field
  private Counter itemsCreatedCounter;
  private Counter itemsDeletedCounter;
  private Timer   itemsFetchTimer;

  /**
   * Registers the three custom metrics used by R8.3.
   * Called automatically by Spring after construction (@PostConstruct).
   * Also called directly in unit tests (ItemServiceTest.setUp()) to bypass
   * the Spring lifecycle — this is why the method is package-private rather
   * than private.
   */
  @PostConstruct
  void initMetrics() {
    itemsCreatedCounter = Counter.builder("items.created.total")
        .description("Total number of items created")
        .register(meterRegistry);

    itemsDeletedCounter = Counter.builder("items.deleted.total")
        .description("Total number of items deleted")
        .register(meterRegistry);

    itemsFetchTimer = Timer.builder("items.fetch.duration")
        .description("Time taken by ItemRepository.findAll()")
        .register(meterRegistry);
  }

  public List<ItemResponse> findAll() {
    return itemsFetchTimer.record(() -> // R8.3
        // Timer.record(Supplier) is used instead of Timer.start()/stop() to
        // avoid leaking a Timer.Sample reference if the repository throws.
        // The Timer wraps only the repository call so the measurement reflects
        // actual DB latency, not downstream DTO mapping time.
        itemRepository.findAll().stream()
            .map(ItemResponse::from)
            .toList()
    );
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
    ItemResponse response = ItemResponse.from(itemRepository.save(item));
    itemsCreatedCounter.increment(); // R8.3
    return response;
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
    itemsDeletedCounter.increment(); // R8.3
  }
}
