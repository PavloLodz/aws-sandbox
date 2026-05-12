package pl.ldz.example.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO used for POST /api/items and PUT /api/items/{id}.
 *
 * <p>Mirrors {@code pl.ldz.example.dto.ItemRequest} from the production service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ItemRequest {

    /** Item name – required, max 255 characters. */
    private String name;

    /** Optional description – max 1000 characters. */
    private String description;
}
