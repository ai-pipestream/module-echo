# module-echo

Reference Pipestream **module** using the demand-pull `ModuleWorkerLoop` pattern: identity `PipeStream` processing, engine bidi `ModuleWorkService`, Consul registration, and unified HTTP/gRPC on one port.

Use this repo as a template for other modules (Gradle, protos, CI, Docker, tests).

## Architecture

- **`EchoProcessor`** — `ModuleProcessor<PipeStream>` identity step.
- **`EchoWorkerConfig`** — wires `ModuleWorkerLoop` and lifecycle (startup delay for Stork/Consul).
- **No `PipeStepProcessor` gRPC** — work is pulled from the engine; registration metadata is inline (`pipestream.registration.module.*`).

## Local run

```bash
./gradlew quarkusDev
```

Default HTTP/gRPC port: **19100** (`application.properties`).

## Tests

| Task | What it exercises |
|------|-------------------|
| `./gradlew test` | Unit tests, in-process bidi smoke (`EchoBidiWiringSmokeTest`), **real gRPC** health client against `@QuarkusTest` server (`EchoGrpcHealthTest`), HTTP readiness |
| `./gradlew quarkusIntTest` | **Integration tests** against the packaged JAR (`EchoModuleIntegrationIT`) |
| | Real gRPC client tests run under `@QuarkusTest` (`EchoGrpcHealthTest`) because prod JARs without inbound `@GrpcService` beans do not mount a gRPC listener. |

Tests disable registration and the worker loop (`%test.*` / `EchoIntegrationTestProfile`) so CI does not need Consul or a running engine.

## CI / Docker

- **`.github/workflows/build-and-publish.yml`** — build, test, `quarkusIntTest`, publish image `module-echo` via `Dockerfile.jvm`.
- **`src/main/docker/Dockerfile.jvm`** — JVM container image (Quarkus `run-java.sh`).

## Related platform docs

- [`pipestream-module-runtime`](https://github.com/ai-pipestream/pipestream-platform) — `ModuleWorkerLoop`, engine client.
- [`module-testing-sidecar`](https://github.com/ai-pipestream/module-testing-sidecar) — E2E graphs that include echo nodes.
