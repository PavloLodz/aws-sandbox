package pl.ldz.example.tests;

import org.testng.annotations.Test;
import pl.ldz.example.dto.ItemRequest;
import pl.ldz.example.dto.ItemResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@code POST /api/items}.
 *
 * <p>Happy-path scenarios verify that the API creates items correctly and
 * returns the expected DTO structure.  Negative scenarios verify that
 * validation errors are surfaced as 400 responses.
 */
@Test(groups = "create")
public class CreateItemTest extends BaseApiTest {

    // ----------------------------------------------------------------- happy path

    @Test(description = "Create item with name and description – expect 201 and populated DTO")
    public void createItem_withNameAndDescription_returns201() {
        ItemRequest request = ItemFactory.valid("Widget Alpha");

        ItemResponse response = apiRequest()
                .body(request)
                .post(ITEMS_PATH)
                .then()
                .statusCode(201)
                .extract()
                .as(ItemResponse.class);

        assertThat(response.getId()).isNotNull().isPositive();
        assertThat(response.getName()).isEqualTo(request.getName());
        assertThat(response.getDescription()).isEqualTo(request.getDescription());
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test(description = "Create item without description – description may be null")
    public void createItem_withoutDescription_returns201() {
        ItemRequest request = ItemFactory.withoutDescription("Widget Beta");

        ItemResponse response = apiRequest()
                .body(request)
                .post(ITEMS_PATH)
                .then()
                .statusCode(201)
                .extract()
                .as(ItemResponse.class);

        assertThat(response.getId()).isNotNull().isPositive();
        assertThat(response.getName()).isEqualTo(request.getName());
    }

    // ----------------------------------------------------------------- validation errors

    @Test(description = "Create item with null name – expect 400")
    public void createItem_nullName_returns400() {
        apiRequest()
                .body(ItemFactory.nullName())
                .post(ITEMS_PATH)
                .then()
                .statusCode(400);
    }

    @Test(description = "Create item with blank name – expect 400")
    public void createItem_blankName_returns400() {
        apiRequest()
                .body(ItemFactory.blankName())
                .post(ITEMS_PATH)
                .then()
                .statusCode(400);
    }

    @Test(description = "Create item with name > 255 chars – expect 400")
    public void createItem_nameTooLong_returns400() {
        apiRequest()
                .body(ItemFactory.nameTooLong())
                .post(ITEMS_PATH)
                .then()
                .statusCode(400);
    }

    @Test(description = "Create item with description > 1000 chars – expect 400")
    public void createItem_descriptionTooLong_returns400() {
        apiRequest()
                .body(ItemFactory.descriptionTooLong())
                .post(ITEMS_PATH)
                .then()
                .statusCode(400);
    }
}
