package pl.ldz.example.tests;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import pl.ldz.example.dto.ItemRequest;
import pl.ldz.example.dto.ItemResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@code PUT /api/items/{id}}.
 */
@Test(groups = "update", dependsOnGroups = "create")
public class UpdateItemTest extends BaseApiTest {

    private ItemResponse seedItem;

    @BeforeClass
    public void seedItem() {
        ItemRequest request = ItemFactory.valid("UpdateTest-" + System.currentTimeMillis());

        seedItem = apiRequest()
                .body(request)
                .post(ITEMS_PATH)
                .then()
                .statusCode(201)
                .extract()
                .as(ItemResponse.class);

        log.info("Seeded item for update tests id={}", seedItem.getId());
    }

    // ----------------------------------------------------------------- happy path

    @Test(description = "PUT /api/items/{id} – name and description are updated")
    public void updateItem_withNewValues_returns200() {
        ItemRequest updateRequest = ItemRequest.builder()
                .name("Updated-" + System.currentTimeMillis())
                .description("Updated description")
                .build();

        ItemResponse response = apiRequest()
                .body(updateRequest)
                .put(ITEMS_PATH + "/" + seedItem.getId())
                .then()
                .statusCode(200)
                .extract()
                .as(ItemResponse.class);

        assertThat(response.getId()).isEqualTo(seedItem.getId());
        assertThat(response.getName()).isEqualTo(updateRequest.getName());
        assertThat(response.getDescription()).isEqualTo(updateRequest.getDescription());
        // updatedAt should be equal to or after createdAt
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test(description = "PUT /api/items/{id} – clear description by setting null")
    public void updateItem_clearDescription_returns200() {
        ItemRequest updateRequest = ItemFactory.withoutDescription("NoDesc-" + System.currentTimeMillis());

        ItemResponse response = apiRequest()
                .body(updateRequest)
                .put(ITEMS_PATH + "/" + seedItem.getId())
                .then()
                .statusCode(200)
                .extract()
                .as(ItemResponse.class);

        assertThat(response.getId()).isEqualTo(seedItem.getId());
        assertThat(response.getName()).isEqualTo(updateRequest.getName());
    }

    // ----------------------------------------------------------------- not found

    @Test(description = "PUT /api/items/{id} with unknown id – returns 404")
    public void updateItem_unknownId_returns404() {
        apiRequest()
                .body(ItemFactory.valid("ghost"))
                .put(ITEMS_PATH + "/999999999")
                .then()
                .statusCode(404);
    }

    // ----------------------------------------------------------------- validation errors

    @Test(description = "PUT /api/items/{id} with blank name – returns 400")
    public void updateItem_blankName_returns400() {
        apiRequest()
                .body(ItemFactory.blankName())
                .put(ITEMS_PATH + "/" + seedItem.getId())
                .then()
                .statusCode(400);
    }

    @Test(description = "PUT /api/items/{id} with name > 255 chars – returns 400")
    public void updateItem_nameTooLong_returns400() {
        apiRequest()
                .body(ItemFactory.nameTooLong())
                .put(ITEMS_PATH + "/" + seedItem.getId())
                .then()
                .statusCode(400);
    }
}
