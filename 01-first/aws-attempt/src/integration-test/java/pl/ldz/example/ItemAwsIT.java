package pl.ldz.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests that run against real AWS (RDS + S3).
 *
 * <p>Skipped automatically when {@code RDS_HOSTNAME} env var is not set.
 * See {@link AbstractAwsIT} for required environment variables and how to run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ItemAwsIT extends AbstractAwsIT {

  @Autowired
  MockMvc mockMvc;

  @Test
  void healthEndpointIsAvailable() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void createAndFetchItem() throws Exception {
    mockMvc.perform(post("/api/items")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name": "AWS Test Item", "description": "Created against real AWS"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.name").value("AWS Test Item"));

    mockMvc.perform(get("/api/items"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").exists());
  }
}
