package pl.ldz.example.tests;

import org.testng.annotations.Test;
import pl.ldz.example.dto.ItemRequest;
import pl.ldz.example.dto.ItemResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@code DELETE /api/items/{id}}.
 *
 * <p>Each test that deletes an item creates its own item first so tests
 * are independent of execution order.
 */
@Test(groups = "delete")
public class DeleteItemTest extends BaseApiTest {

    // ----------------------------------------------------------------- happy path

    @Test(description = "DELETE /api/items/{id} – returns 204 and item is gone")
    public void deleteItem_existingId_returns204() {
        // create item to delete
        ItemRequest request = ItemFactory.valid("ToDelete-" + System.currentTimeMillis());
        ItemResponse created = apiRequest()
                .body(request)
                .post(ITEMS_PATH)
                .then()
                .statusCode(201)
                .extract()
                .as(ItemResponse.class);

        assertThat(created.getId()).isNotNull();

        // delete
        apiRequest()
                .delete(ITEMS_PATH + "/" + created.getId())
                .then()
                .statusCode(204);

        // verify gone
        apiRequest()
                .get(ITEMS_PATH + "/" + created.getId())
                .then()
                .statusCode(404);
    }

    @Test(description = "DELETE same item twice – first 204, second 404")
    public void deleteItem_twice_secondCallReturns404() {
        ItemRequest request = ItemFactory.valid("ToDeleteTwice-" + System.currentTimeMillis());
        ItemResponse created = apiRequest()
                .body(request)
                .post(ITEMS_PATH)
                .then()
                .statusCode(201)
                .extract()
                .as(ItemResponse.class);

        // first delete
        apiRequest()
                .delete(ITEMS_PATH + "/" + created.getId())
                .then()
                .statusCode(204);

        // second delete
        apiRequest()
                .delete(ITEMS_PATH + "/" + created.getId())
                .then()
                .statusCode(404);
    }

    // ----------------------------------------------------------------- not found

    @Test(description = "DELETE /api/items/{id} with unknown id – returns 404")
    public void deleteItem_unknownId_returns404() {
        apiRequest()
                .delete(ITEMS_PATH + "/999999999")
                .then()
                .statusCode(404);
    }
}
