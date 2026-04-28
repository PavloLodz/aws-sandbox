package pl.ldz.example.tests;

import pl.ldz.example.dto.ItemRequest;

/**
 * Factory methods that produce {@link ItemRequest} instances for test scenarios.
 *
 * <p>Centralising test data here keeps individual test methods clean and makes
 * it easy to evolve payloads in one place.
 */
public final class ItemFactory {

    private ItemFactory() {}

    /** A fully populated, valid request. */
    public static ItemRequest valid(String name) {
        return ItemRequest.builder()
                .name(name)
                .description("Auto-generated test description for: " + name)
                .build();
    }

    /** A valid request with no description (description is optional). */
    public static ItemRequest withoutDescription(String name) {
        return ItemRequest.builder()
                .name(name)
                .build();
    }

    /** A request with a null name – must fail validation (400). */
    public static ItemRequest nullName() {
        return ItemRequest.builder()
                .name(null)
                .description("Some description")
                .build();
    }

    /** A request with a blank name – must fail validation (400). */
    public static ItemRequest blankName() {
        return ItemRequest.builder()
                .name("   ")
                .description("Some description")
                .build();
    }

    /** A request with a name that exceeds 255 characters – must fail validation (400). */
    public static ItemRequest nameTooLong() {
        return ItemRequest.builder()
                .name("A".repeat(256))
                .description("Some description")
                .build();
    }

    /** A request with a description that exceeds 1000 characters – must fail validation (400). */
    public static ItemRequest descriptionTooLong() {
        return ItemRequest.builder()
                .name("Valid name")
                .description("X".repeat(1001))
                .build();
    }
}
