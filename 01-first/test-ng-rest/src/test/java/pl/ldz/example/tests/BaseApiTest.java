package pl.ldz.example.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeSuite;

import static io.restassured.RestAssured.given;

/**
 * Base class shared by all API test classes.
 *
 * <p>Configures RestAssured once per suite:
 * <ul>
 *   <li>Base URI read from the system property {@code base.url}
 *       (default: {@code http://localhost:8080}).
 *   <li>Jackson ObjectMapper with Java-time support.
 *   <li>Request/response logging for every call (great for CI logs).
 * </ul>
 */
public abstract class BaseApiTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseApiTest.class);

    /** Endpoint path for all item operations. */
    protected static final String ITEMS_PATH = "/api/items";

    // ------------------------------------------------------------------ setup

    @BeforeSuite(alwaysRun = true)
    public void configureRestAssured() {
        String baseUrl = System.getProperty("base.url", "http://localhost:8080");
        log.info("Configuring RestAssured with base URL: {}", baseUrl);

        RestAssured.baseURI = baseUrl;

        // Use Jackson for (de)serialisation with Java 8 date/time support
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        RestAssured.config = RestAssuredConfig.config()
                .objectMapperConfig(
                        ObjectMapperConfig.objectMapperConfig()
                                .jackson2ObjectMapperFactory((cls, charset) -> mapper));

        // Log every request and response to stdout (captured by Surefire / CI)
        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Returns a pre-configured {@link RequestSpecification} that sends and
     * accepts JSON.
     */
    protected RequestSpecification apiRequest() {
        return given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }
}
