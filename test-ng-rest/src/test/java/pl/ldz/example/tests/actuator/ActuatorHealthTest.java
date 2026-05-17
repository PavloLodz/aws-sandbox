package pl.ldz.example.tests.actuator;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;
import pl.ldz.example.tests.BaseApiTest;

import static org.testng.Assert.assertEquals;

public class ActuatorHealthTest extends BaseApiTest {

  @BeforeClass
  public void setup() {
    // RestAssured.baseURI = "http://localhost";
    // RestAssured.port = 8080;
  }


  @Test
  public void testHealthEndpointReturnsUp() {
    Response response = apiRequest()
        .given()
        .when()
        .get("/actuator/health")
        .then()
        .extract()
        .response();

    // Verify HTTP status code
    assertEquals(response.getStatusCode(), 200);

    // Verify JSON response field
    assertEquals(response.jsonPath().getString("status"), "UP");
  }

  @Test
  public void testLivenessEndpointReturnsUp() {
    Response response = apiRequest()
        .given()
        .when()
        .get("/actuator/health/liveness")
        .then()
        .extract()
        .response();

    // Verify HTTP status code
    assertEquals(response.getStatusCode(), 200);

    // Verify "status":"UP"
    assertEquals(response.jsonPath().getString("status"), "UP");
  }

  @Test
  public void testReadinessEndpointReturnsUp() {
    Response response = apiRequest()
        .given()
        .when()
        .get("/actuator/health/readiness")
        .then()
        .extract()
        .response();

    // Verify HTTP status code
    assertEquals(response.getStatusCode(), 200);

    // Verify "status":"UP"
    assertEquals(response.jsonPath().getString("status"), "UP");
  }




  @Test @Ignore
  public void testHealthEndpointReturnsUpOld() {
    Response response = RestAssured
        .given()
        .when()
        .get("/actuator/health")
        .then()
        .extract()
        .response();

    // Verify HTTP status code
    assertEquals(response.getStatusCode(), 200);

    // Verify JSON response field
    assertEquals(response.jsonPath().getString("status"), "UP");
  }

}
