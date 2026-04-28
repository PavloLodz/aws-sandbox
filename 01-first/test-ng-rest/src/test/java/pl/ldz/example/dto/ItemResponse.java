package pl.ldz.example.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO returned by every Items endpoint.
 *
 * <p>Mirrors {@code pl.ldz.example.dto.ItemResponse} from the production service.
 * Unknown JSON fields are silently ignored so the test suite stays compatible
 * when the API adds new fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemResponse {

    private Long id;
    private String name;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
