# Module Echo

[![Build Status](https://github.com/ai-pipestream/module-echo/actions/workflows/build-and-publish.yml/badge.svg)](https://github.com/ai-pipestream/module-echo/actions/workflows/build-and-publish.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.29.2-red.svg)](https://quarkus.io/)

A lightweight, high-performance validation and testing module for the Pipestream data processing pipeline. Module Echo receives documents via gRPC, enriches them with processing metadata, and returns them unchanged - making it ideal for pipeline testing, validation, and debugging.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Installation](#installation)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Docker Support](#docker-support)
- [Development](#development)
- [Testing](#testing)
- [CI/CD](#cicd)
- [Contributing](#contributing)
- [License](#license)

## Overview

Module Echo is a Quarkus-based microservice that serves as a validation and testing component in the Pipestream pipeline ecosystem. It implements the `PipeStepProcessor` gRPC interface to:

- **Echo documents** back with enriched metadata for traceability
- **Validate pipeline connectivity** by providing a known, predictable response
- **Support service discovery** through Consul integration
- **Enable observability** with comprehensive logging and health checks

### Use Cases

- **Pipeline Validation**: Verify that documents flow correctly through the pipeline
- **Integration Testing**: Test pipeline components in isolation
- **Debugging**: Trace document flow with added timestamp and processing metadata
- **Performance Testing**: Benchmark pipeline throughput with minimal processing overhead
- **Health Monitoring**: Validate service availability and connectivity

## Features

- **gRPC-based Communication**: High-performance, type-safe communication using Protocol Buffers
- **Document Metadata Enrichment**: Automatically adds processing provenance (timestamp, processor ID, version)
- **Service Registration**: Auto-registers with Consul for dynamic service discovery
- **Large Message Support**: Handles documents up to 2GB in size
- **Reactive Processing**: Built on Mutiny for non-blocking, asynchronous operations
- **Health Checks**: SmallRye Health integration for Kubernetes/orchestration readiness
- **OpenAPI Documentation**: Auto-generated Swagger UI for REST endpoints
- **Docker Ready**: Multiple container options (JVM, native, micro)
- **Processing Buffer**: Optional document capture for analysis and debugging
- **Comprehensive Testing**: Unit, integration, and buffer access tests

## Architecture

```
┌─────────────────────┐     gRPC Request      ┌──────────────────┐
│   Pipeline         │  ModuleProcessRequest  │                  │
│   Orchestrator     │ ─────────────────────> │   Module Echo    │
│                    │                         │                  │
│                    │  ModuleProcessResponse  │  - Echo Document │
│                    │ <───────────────────── │  - Add Metadata  │
└─────────────────────┘                        │  - Log Activity  │
                                               └──────────────────┘
                                                        │
                                                        ▼
                                               ┌──────────────────┐
                                               │     Consul       │
                                               │ Service Registry │
                                               └──────────────────┘
```

### Processing Flow

1. **Receive Request**: Accept `ModuleProcessRequest` containing a `PipeDoc`
2. **Extract Document**: Parse the incoming document and metadata
3. **Enrich Metadata**: Add processing tags (timestamp, processor name, version, stream ID)
4. **Build Response**: Create `ModuleProcessResponse` with success status and logs
5. **Return Document**: Send back the enriched document via gRPC

## Quick Start

### Prerequisites

- Java 21 or higher
- Gradle 8.x (wrapper included)
- Docker (optional, for containerized deployment)

### Build and Run

```bash
# Clone the repository
git clone https://github.com/ai-pipestream/module-echo.git
cd module-echo

# Build the project
./gradlew build

# Run in development mode
./gradlew quarkusDev
```

The service will start on:
- **HTTP/gRPC**: `http://localhost:39000/echo`
- **Health Check**: `http://localhost:39000/echo/health`
- **Swagger UI**: `http://localhost:39000/echo/swagger-ui`

### Test the Service

```bash
# Check health status
curl http://localhost:39000/echo/health

# View OpenAPI specification
curl http://localhost:39000/echo/openapi
```

## Installation

### Maven Dependency

```xml
<dependency>
  <groupId>ai.pipestream.module</groupId>
  <artifactId>module-echo</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Gradle Dependency

```groovy
implementation 'ai.pipestream.module:module-echo:1.0.0'
```

### Docker

```bash
# Pull from GitHub Container Registry
docker pull ghcr.io/ai-pipestream/module-echo:latest

# Pull from Docker Hub
docker pull pipestreamai/module-echo:latest

# Run the container
docker run -p 39000:39000 ghcr.io/ai-pipestream/module-echo:latest
```

## Configuration

Module Echo is configured via `application.properties`. Key configuration options:

### Server Configuration

```properties
# HTTP/gRPC Server
quarkus.http.port=39000
quarkus.http.host=0.0.0.0
quarkus.http.root-path=/echo
quarkus.grpc.server.use-separate-server=false

# Maximum message size (up to 2GB)
quarkus.grpc.server.max-inbound-message-size=2147483647
```

### Service Registration

```properties
# Enable service registration
module.registration.enabled=true
module.registration.module-name=echo
module.registration.host=localhost
module.registration.port=39000
module.registration.description=Echo module for testing and validation
module.registration.capabilities=echo,testing,validation
module.registration.tags=echo,testing,module
```

### Consul Service Discovery

```properties
# Consul configuration
quarkus.stork.registration-service.service-discovery.type=consul
quarkus.stork.registration-service.service-discovery.consul-host=${CONSUL_HOST:consul}
quarkus.stork.registration-service.service-discovery.consul-port=${CONSUL_PORT:8500}
```

### Processing Buffer (Optional)

```properties
# Enable document buffering for analysis
processing.buffer.enabled=false
processing.buffer.capacity=100
processing.buffer.directory=target/buffer-data
processing.buffer.prefix=echo_buffer
```

### Logging

```properties
quarkus.log.level=INFO
quarkus.log.category."ai.pipestream".level=DEBUG
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `CONSUL_HOST` | Consul server hostname | `consul` |
| `CONSUL_PORT` | Consul server port | `8500` |
| `QUARKUS_HTTP_PORT` | HTTP/gRPC server port | `39000` |

## API Reference

### gRPC Service: PipeStepProcessor

Module Echo implements the `PipeStepProcessor` gRPC service with three main methods:

#### processData

Processes a document by echoing it back with enriched metadata.

**Request**: `ModuleProcessRequest`
```protobuf
message ModuleProcessRequest {
  PipeDoc document = 1;
  ServiceMetadata metadata = 2;
  ProcessConfiguration config = 3;
}
```

**Response**: `ModuleProcessResponse`
```protobuf
message ModuleProcessResponse {
  bool success = 1;
  PipeDoc output_doc = 2;
  repeated string processor_logs = 3;
  ErrorDetails error_details = 4;
}
```

**Added Metadata Tags**:
- `processed_by_echo`: Application name
- `echo_timestamp`: ISO-8601 timestamp of processing
- `echo_module_version`: Module version (1.0.0)
- `echo_stream_id`: Stream ID from request metadata
- `echo_step_name`: Pipeline step name from request metadata

#### getServiceRegistration

Returns comprehensive service registration metadata.

**Request**: `RegistrationRequest`
```protobuf
message RegistrationRequest {
  ModuleProcessRequest test_request = 1; // Optional health check
}
```

**Response**: `ServiceRegistrationMetadata`
```protobuf
message ServiceRegistrationMetadata {
  string module_name = 1;
  string version = 2;
  string display_name = 3;
  string description = 4;
  string owner = 5;
  repeated string tags = 6;
  google.protobuf.Timestamp registration_timestamp = 7;
  string server_info = 8;
  string sdk_version = 9;
  map<string, string> metadata = 10;
  bool health_check_passed = 11;
  string health_check_message = 12;
}
```

#### testProcessData

Test endpoint for validation purposes. Creates a test document if none provided.

**Request**: `ModuleProcessRequest` (optional - will create test data if missing)

**Response**: `ModuleProcessResponse` with `[TEST]` prefixed logs

### HTTP Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/echo` | GET | Root endpoint |
| `/echo/health` | GET | Health check status |
| `/echo/health/live` | GET | Liveness probe |
| `/echo/health/ready` | GET | Readiness probe |
| `/echo/swagger-ui` | GET | OpenAPI/Swagger documentation |
| `/echo/openapi` | GET | OpenAPI specification (JSON) |

## Docker Support

### Available Images

- **JVM Mode**: Standard Java runtime (larger, more compatible)
- **Native Mode**: GraalVM native image (faster startup, lower memory)
- **Native Micro**: Minimal native image (smallest footprint)

### Build Docker Image

```bash
# JVM mode
./gradlew build -Dquarkus.container-image.build=true

# Native mode
./gradlew build -Dquarkus.native.enabled=true \
  -Dquarkus.container-image.build=true

# Push to registry
./gradlew build -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.registry=ghcr.io \
  -Dquarkus.container-image.group=ai-pipestream
```

### Docker Compose

```yaml
version: '3.8'
services:
  module-echo:
    image: ghcr.io/ai-pipestream/module-echo:latest
    ports:
      - "39000:39000"
    environment:
      - CONSUL_HOST=consul
      - CONSUL_PORT=8500
    depends_on:
      - consul

  consul:
    image: consul:1.15
    ports:
      - "8500:8500"
```

## Development

### Project Structure

```
module-echo/
├── src/
│   ├── main/
│   │   ├── java/ai/pipestream/module/echo/
│   │   │   └── EchoServiceImpl.java          # Main service implementation
│   │   ├── resources/
│   │   │   ├── application.properties        # Configuration
│   │   │   └── META-INF/beans.xml           # CDI configuration
│   │   └── docker/                           # Dockerfiles
│   ├── test/                                 # Unit tests
│   └── integrationTest/                      # Integration tests
├── build.gradle                              # Build configuration
├── settings.gradle                           # Gradle settings
└── .github/workflows/                        # CI/CD pipelines
```

### Development Mode

Quarkus provides a powerful development mode with live reload:

```bash
./gradlew quarkusDev
```

Features:
- **Live Reload**: Code changes are automatically reloaded
- **Dev Services**: Automatic container provisioning for dependencies
- **Continuous Testing**: Tests run automatically on code changes
- **Dev UI**: Interactive development dashboard at `/q/dev`

### Code Style

- Java 21 features enabled
- UTF-8 encoding
- Standard Quarkus/CDI patterns
- Reactive programming with Mutiny

## Testing

### Run All Tests

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew integrationTest

# All tests
./gradlew check
```

### Test Categories

#### Unit Tests

Located in `src/test/java/ai/pipestream/module/echo/`:

- `EchoServiceTest` - Basic service functionality
- `EchoServiceNoRegistrationTest` - Service without registration
- `ProcessingBufferAccessTest` - Buffer capture functionality

Key test cases:
- Document processing and metadata enrichment
- Handling requests without documents
- Service registration with/without health checks
- Large document processing (10KB+)
- Custom data preservation
- Test endpoint validation

#### Integration Tests

Located in `src/integrationTest/java/ai/pipestream/module/echo/`:

- `EchoServiceIT` - End-to-end integration testing
- `EchoServiceGrpcIT` - gRPC client integration

Features:
- Real gRPC channel setup
- Black-box testing against packaged application
- Various message size scenarios

### Test Infrastructure

The test suite uses Docker Compose for supporting services:

```yaml
# src/test/resources/compose-test-services.yml
services:
  mysql:
    image: mysql:8.0
  redpanda:
    image: redpandadata/redpanda:latest
  apicurio:
    image: apicurio/apicurio-registry-mem:latest
```

## CI/CD

### GitHub Actions Workflows

#### Build and Publish (`build-and-publish.yml`)

Triggered on push to main and pull requests:

1. Build and test the project
2. Publish snapshot to Maven Central (main branch)
3. Publish snapshot to GitHub Packages
4. Build and push Docker snapshot image

#### Release and Publish (`release-and-publish.yml`)

Manual workflow for releases:

1. Build and test
2. Create release tag (semantic versioning)
3. Publish to Maven Central with GPG signing
4. Publish to GitHub Packages
5. Create GitHub Release with artifacts
6. Build and push production Docker image
7. Trigger Docker Hub publish

#### Docker Hub Publish (`dockerhub-publish.yml`)

Publishes to Docker Hub after successful release:

- Source: `ghcr.io/ai-pipestream/module-echo`
- Target: `pipestreamai/module-echo`

### Version Management

Uses [Axion Release Plugin](https://axion-release-plugin.readthedocs.io/):

```bash
# View current version
./gradlew currentVersion

# Create release
./gradlew release -Prelease.version=1.0.0
```

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes with tests
4. Ensure all tests pass: `./gradlew check`
5. Commit with clear messages: `git commit -m "Add my feature"`
6. Push to your fork: `git push origin feature/my-feature`
7. Create a Pull Request

### Development Guidelines

- Write comprehensive tests for new features
- Follow existing code patterns and style
- Update documentation as needed
- Ensure CI passes before merging

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2024 Rokkon Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Support

- **Issues**: [GitHub Issues](https://github.com/ai-pipestream/module-echo/issues)
- **Repository**: [GitHub](https://github.com/ai-pipestream/module-echo)
- **Owner**: Rokkon Team

---

**Module Echo** - Simple, reliable, and observable document processing for Pipestream pipelines.
