package ai.pipestream.echo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;

/**
 * Verifies SmallRye readiness over HTTP on the unified server port.
 */
@QuarkusTest
class EchoHttpHealthTest {

    @ConfigProperty(name = "quarkus.http.test-port")
    int httpPort;

    @BeforeEach
    void baseUri() {
        RestAssured.port = httpPort;
    }

    @Test
    void readiness_isUp() {
        RestAssured.given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
