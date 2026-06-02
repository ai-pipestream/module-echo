package ai.pipestream.echo;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.hamcrest.Matchers.equalTo;

/**
 * Black-box integration tests against the packaged {@code quarkus-run.jar}
 * (successor to {@code EchoServiceIT} / {@code EchoServiceGrpcIT}).
 *
 * <p>Echo is a demand-pull worker module with no inbound business gRPC services,
 * so the production artifact does not mount a gRPC listener (only {@code grpc-client}).
 * Use {@link ai.pipestream.echo.health.EchoGrpcHealthTest} for real gRPC health calls
 * against the {@code @QuarkusTest} server.
 */
@QuarkusIntegrationTest
@TestProfile(EchoIntegrationTestProfile.class)
class EchoModuleIntegrationIT {

    @TestHTTPResource
    URL testUrl;

    @BeforeEach
    void baseUri() {
        RestAssured.baseURI = testUrl.toString();
    }

    @Test
    void packagedJar_readinessIsUp() {
        RestAssured.given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void packagedJar_livenessIsUp() {
        RestAssured.given()
                .when()
                .get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
