package pl.ldz.example.tests;

import org.testng.annotations.Test;
import pl.ldz.example.dto.ItemRequest;
import pl.ldz.example.dto.ItemResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end lifecycle test: Create → Read → Update → Delete.
 *
 * <p>All steps are chained as {@code dependsOnMethods} to avoid duplicating
 * teardown and to make the sequence explicit.  A single {@link ItemResponse}
 * field is shared across steps via instance state (safe because TestNG
 * creates one instance per test class by default).
 */
@Test(groups = "lifecycle")
public class CrudLifecycleTest extends BaseApiTest {

    private Long itemId;
    private String originalName;

    @Test(description = "Step 1 – Create item")
    public void step1_create() {
        ItemRequest request = ItemFactory.valid("Lifecycle-" + System.currentTimeMillis());
        originalName = request.getName();

        ItemResponse response = apiRequest()
                .body(request)
                .post(ITEMS_PATH)
                .then()
                .statusCode(201)
                .extract()
                .as(ItemResponse.class);

        assertThat(response.getId()).isNotNull().isPositive();
        assertThat(response.getName()).isEqualTo(originalName);
        itemId = response.getId();
        log.info("[lifecycle] Created item id={}", itemId);
    }

    @Test(dependsOnMethods = "step1_create",
          description = "Step 2 – Read item and verify fields")
    public void step2_read() {
        ItemResponse response = apiRequest()
                .get(ITEMS_PATH + "/" + itemId)
                .then()
                .statusCode(200)
                .extract()
                .as(ItemResponse.class);

        assertThat(response.getId()).isEqualTo(itemId);
        assertThat(response.getName()).isEqualTo(originalName);
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @Test(dependsOnMethods = "step2_read",
          description = "Step 3 – Update name and description")
    public void step3_update() {
        ItemRequest updateRequest = ItemRequest.builder()
                .name(originalName + "-UPDATED")
                .description("Updated in lifecycle test")
                .build();

        ItemResponse response = apiRequest()
                .body(updateRequest)
                .put(ITEMS_PATH + "/" + itemId)
                .then()
                .statusCode(200)
                .extract()
                .as(ItemResponse.class);

        assertThat(response.getName()).isEqualTo(updateRequest.getName());
        assertThat(response.getDescription()).isEqualTo(updateRequest.getDescription());
    }

    @Test(dependsOnMethods = "step3_update",
          description = "Step 4 – Delete item")
    public void step4_delete() {
        apiRequest()
                .delete(ITEMS_PATH + "/" + itemId)
                .then()
                .statusCode(204);
    }

    @Test(dependsOnMethods = "step4_delete",
          description = "Step 5 – Verify item is gone after deletion")
    public void step5_verifyGone() {
        apiRequest()
                .get(ITEMS_PATH + "/" + itemId)
                .then()
                .statusCode(404);
        log.info("[lifecycle] Item id={} confirmed deleted", itemId);
    }
}
