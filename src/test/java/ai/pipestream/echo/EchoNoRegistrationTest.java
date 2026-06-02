package ai.pipestream.echo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;

/**
 * Echo must boot and serve health when registration and the worker loop are off
 * (typical CI / local unit-test mode).
 */
@QuarkusTest
@TestProfile(EchoNoRegistrationTestProfile.class)
class EchoNoRegistrationTest {

    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    @ConfigProperty(name = "quarkus.http.test-port")
    int httpPort;

    @BeforeEach
    void baseUri() {
        RestAssured.port = httpPort;
    }

    @Test
    void applicationStarts_andReadinessIsUp() {
        RestAssured.given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void applicationName_isEcho() {
        org.assertj.core.api.Assertions.assertThat(applicationName).isEqualTo("echo");
    }
}
