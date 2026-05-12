package pl.ldz.example.tests;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import pl.ldz.example.dto.ItemRequest;
import pl.ldz.example.dto.ItemResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for:
 * <ul>
 *   <li>{@code GET /api/items}          – list all items</li>
 *   <li>{@code GET /api/items?search=…} – full-text search</li>
 *   <li>{@code GET /api/items/{id}}     – get by ID</li>
 * </ul>
 */
@Test(groups = "read", dependsOnGroups = "create")
public class GetItemTest extends BaseApiTest {

    private ItemResponse seedItem;

    /**
     * Seeds one known item before any read test runs.  Stored in {@link #seedItem}
     * so individual tests can reference a stable ID and name.
     */
    @BeforeClass
    public void seedItem() {
        ItemRequest request = ItemFactory.valid("GetTest-" + System.currentTimeMillis());

        seedItem = apiRequest()
                .body(request)
                .post(ITEMS_PATH)
                .then()
                .statusCode(201)
                .extract()
                .as(ItemResponse.class);

        log.info("Seeded item id={} name={}", seedItem.getId(), seedItem.getName());
    }

    // ----------------------------------------------------------------- GET all

    @Test(description = "GET /api/items – response is a non-empty list")
    public void getAllItems_returnsNonEmptyList() {
        List<ItemResponse> items = List.of(
                apiRequest()
                        .get(ITEMS_PATH)
                        .then()
                        .statusCode(200)
                        .extract()
                        .as(ItemResponse[].class));

        assertThat(items).isNotEmpty();
        assertThat(items).allSatisfy(item -> {
            assertThat(item.getId()).isNotNull().isPositive();
            assertThat(item.getName()).isNotBlank();
        });
    }

    @Test(description = "GET /api/items – seeded item appears in the list")
    public void getAllItems_containsSeedItem() {
        ItemResponse[] items = apiRequest()
                .get(ITEMS_PATH)
                .then()
                .statusCode(200)
                .extract()
                .as(ItemResponse[].class);

        assertThat(items)
                .extracting(ItemResponse::getId)
                .contains(seedItem.getId());
    }

    // ----------------------------------------------------------------- GET by id

    @Test(description = "GET /api/items/{id} with valid id – returns correct DTO")
    public void getById_existingId_returnsItem() {
        ItemResponse response = apiRequest()
                .get(ITEMS_PATH + "/" + seedItem.getId())
                .then()
                .statusCode(200)
                .extract()
                .as(ItemResponse.class);

        assertThat(response.getId()).isEqualTo(seedItem.getId());
        assertThat(response.getName()).isEqualTo(seedItem.getName());
        assertThat(response.getDescription()).isEqualTo(seedItem.getDescription());
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test(description = "GET /api/items/{id} with unknown id – returns 404")
    public void getById_unknownId_returns404() {
        apiRequest()
                .get(ITEMS_PATH + "/999999999")
                .then()
                .statusCode(404);
    }

    // ----------------------------------------------------------------- search

    @Test(description = "GET /api/items?search=<name> – returns matching items")
    public void search_byExactName_returnsMatchingItem() {
        ItemResponse[] items = apiRequest()
                .queryParam("search", seedItem.getName())
                .get(ITEMS_PATH)
                .then()
                .statusCode(200)
                .extract()
                .as(ItemResponse[].class);

        assertThat(items)
                .extracting(ItemResponse::getId)
                .contains(seedItem.getId());
    }

    @Test(description = "GET /api/items?search=<non-existent> – returns empty list")
    public void search_byNonExistentTerm_returnsEmptyList() {
        ItemResponse[] items = apiRequest()
                .queryParam("search", "zzz-no-such-item-zzz-" + System.currentTimeMillis())
                .get(ITEMS_PATH)
                .then()
                .statusCode(200)
                .extract()
                .as(ItemResponse[].class);

        assertThat(items).isEmpty();
    }
}
