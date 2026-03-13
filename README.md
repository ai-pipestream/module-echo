# module-echo

Simple test/validation module

## Yes — But Skip the REST Client, Use Vert.x HTTP Proxy Directly

`quarkus-rest-client-reactive` is the wrong tool here — it wants you to define typed interfaces which means you're implicitly defining the body contract. You want `vertx-http-proxy` which is purpose-built for transparent proxying.

Quarkus gives you direct access to the Vert.x instance, and Vert.x has a first-class reverse proxy API that streams bytes without touching them.

---

## Dependency

```xml
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-http-proxy</artifactId>
</dependency>
```

No additional Quarkus extension needed — it rides on `quarkus-vertx-http` which you already have.

---

## The Proxy Service

```java
@ApplicationScoped
public class OpenSearchProxyService {

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "opensearch.host")
    String openSearchHost;

    @ConfigProperty(name = "opensearch.port")
    int openSearchPort;

    HttpProxy proxy;

    void init(@Observes StartupEvent ev) {
        HttpClient client = vertx.createHttpClient(
            new HttpClientOptions()
                .setDefaultHost(openSearchHost)
                .setDefaultPort(openSearchPort)
                .setSsl(true)                    // managed OpenSearch is TLS
                .setKeepAlive(true)
        );
        proxy = HttpProxy.reverseProxy(client);
    }

    public HttpProxy proxy() {
        return proxy;
    }
}
```

---

## The Route — Where Headers Are Injected

```java
@ApplicationScoped
public class ProxyRoute {

    @Inject
    OpenSearchProxyService proxyService;

    @Inject
    EntitlementService entitlementService;

    @Inject
    OidcJsonWebToken jwt;                        // quarkus-oidc injects this

    @Route(path = "/search/*", regex = true)
    @AuthenticationRequired
    Uni<Void> handle(RoutingContext ctx) {

        String user = jwt.getClaim("email");

        return entitlementService.resolveRoles(user)
            .flatMap(roles -> {
                // inject — do NOT touch ctx.request().body()
                ctx.request()
                    .headers()
                    .set("x-proxy-user", user)
                    .set("opensearch-security-roles", String.join(",", roles));

                proxyService.proxy().handle(ctx.request());
                return Uni.createFrom().voidItem();
            });
    }
}
```

`proxy.handle(ctx.request())` pipes the raw request — headers, body, chunked encoding, everything — directly to OpenSearch. The body is never deserialized.

---

## What Vert.x HTTP Proxy Gives You For Free

- **Streaming** — request and response body are piped as `Buffer` chunks, never materialized
- **Backpressure** — if OpenSearch is slow, the client is slowed, not your heap
- **Chunked transfer** — preserved transparently
- **Connection pooling** — handled by the underlying `HttpClient`
- **Response streaming back** — OpenSearch's response is piped back to the client the same way

---

## What You Still Wire Up Separately

```
quarkus-oidc          → JWT validation (declarative, @AuthenticationRequired)
EntitlementService    → your Collibra/Confluence ACL resolver
quarkus-micrometer    → metrics on the proxy path
```

The JWT validation happens before your route handler ever fires — Quarkus OIDC rejects invalid tokens at the filter layer, so by the time you're in the route, the identity is already verified. The only work your code does is entitlement resolution and header injection.

---

## What This Isn't Doing

No body parsing. No JSON deserialization. No OpenSearch query model. The proxy literally does not know or care what OpenSearch query looks like — and that's the correct design.
