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


## IAM login

The Vert.x proxy will forward the request bytes fine, but managed AWS OpenSearch requires every request to be **SigV4 signed** with the proxy's IAM role credentials. The proxy has to handle that signing — OpenSearch will reject anything unsigned or incorrectly signed.

---

## What Has To Happen On Every Request

```
Inbound request (has Okta JWT)
  │
  ├── Strip Authorization header (Okta JWT must NOT go to OpenSearch)
  │
  ├── Inject DLS headers (x-proxy-user, opensearch-security-roles)
  │
  ├── Sign request with proxy's IAM role (SigV4)
  │     └── Authorization: AWS4-HMAC-SHA256 Credential=...
  │
  └── Forward signed request to OpenSearch
```

The SigV4 signature covers the headers and URL — but **not the body** for streaming requests, you can use `UNSIGNED-PAYLOAD`. This preserves the transparent body forwarding.

---

## The Signing Piece

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>auth</artifactId>
</dependency>
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>regions</artifactId>
</dependency>
```

```java
@ApplicationScoped
public class SigV4Signer {

    @ConfigProperty(name = "aws.region")
    String region;

    private final Aws4Signer signer = Aws4Signer.create();

    public Map<String, String> signHeaders(
            String method,
            URI uri,
            Map<String, String> existingHeaders) {

        AwsCredentialsProvider credentialsProvider = 
            DefaultCredentialsProvider.create(); // picks up IAM role automatically

        SdkHttpFullRequest request = SdkHttpFullRequest.builder()
            .method(SdkHttpMethod.fromValue(method))
            .uri(uri)
            .headers(existingHeaders.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> List.of(e.getValue()))))
            .putHeader("x-amz-content-sha256", "UNSIGNED-PAYLOAD") // ← don't touch body
            .build();

        Aws4SignerParams params = Aws4SignerParams.builder()
            .awsCredentials(credentialsProvider.resolveCredentials())
            .signingName("es")                   // "es" for OpenSearch
            .signingRegion(Region.of(region))
            .build();

        SdkHttpFullRequest signed = signer.sign(request, params);

        // return only the auth headers we need to inject
        return Map.of(
            "Authorization", signed.firstMatchingHeader("Authorization").orElseThrow(),
            "x-amz-date", signed.firstMatchingHeader("x-amz-date").orElseThrow(),
            "x-amz-security-token", signed.firstMatchingHeader("x-amz-security-token").orElse("")
        );
    }
}
```

---

## Updated Route

```java
return entitlementService.resolveRoles(user)
    .flatMap(roles -> {
        URI uri = URI.create("https://" + openSearchHost + ctx.request().uri());

        // build headers for signing (without the Okta JWT)
        Map<String, String> headers = Map.of(
            "host", openSearchHost,
            "x-proxy-user", user,
            "opensearch-security-roles", String.join(",", roles),
            "x-amz-content-sha256", "UNSIGNED-PAYLOAD"
        );

        Map<String, String> sigV4Headers = signer.signHeaders(
            ctx.request().method().name(), uri, headers
        );

        MultiMap reqHeaders = ctx.request().headers();
        reqHeaders.remove("Authorization");      // strip Okta JWT
        sigV4Headers.forEach(reqHeaders::set);   // inject SigV4 + DLS headers

        proxyService.proxy().handle(ctx.request());
        return Uni.createFrom().voidItem();
    });
```

---

## On EC2/ECS/EKS — Zero Config

`DefaultCredentialsProvider` automatically picks up the instance/task/pod IAM role from the metadata service. You don't manage credentials at all — AWS rotates them. This is exactly what zero-trust wants for workload identity: cryptographic, short-lived, automatically rotated.

```
EC2/ECS instance has IAM role: acl-proxy-role
  └── OpenSearch domain policy accepts: arn:aws:iam::ACCOUNT:role/acl-proxy-role
        └── No other role or identity can reach OpenSearch
```

The proxy's IAM role is its identity. No shared secrets, no API keys, no config files with credentials.

# Security

## Two Separate Problems — Both Need Solving

**Problem 1:** Lock OpenSearch to only accept requests from the proxy's IAM role.
**Problem 2:** Ensure injected DLS headers are only trusted when they come from that role.

If you only solve #1, a compromised proxy can inject anything. If you only solve #2 without #1, anyone who gets SigV4 credentials can reach OpenSearch. You need both.

---

## Step 1 — OpenSearch Domain Resource Policy (AWS Level)

This is the outermost lock. OpenSearch flat-out rejects requests that aren't from the specified IAM role — before the security plugin even sees them.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::YOUR_ACCOUNT:role/acl-proxy-role"
      },
      "Action": "es:*",
      "Resource": "arn:aws:es:YOUR_REGION:YOUR_ACCOUNT:domain/YOUR_DOMAIN/*"
    },
    {
      "Effect": "Deny",
      "Principal": "*",
      "Action": "es:*",
      "Resource": "arn:aws:es:YOUR_REGION:YOUR_ACCOUNT:domain/YOUR_DOMAIN/*",
      "Condition": {
        "StringNotEquals": {
          "aws:PrincipalArn": "arn:aws:iam::YOUR_ACCOUNT:role/acl-proxy-role"
        }
      }
    }
  ]
}
```

The explicit `Deny` for everything else is important — `Deny` always wins over `Allow` in IAM evaluation. Even if someone misconfigures another policy that grants access, this Deny blocks it.

---

## Step 2 — VPC + Security Groups (Network Level)

Don't rely on IAM alone. Defense in depth.

```
OpenSearch Security Group:
  Inbound: TCP 443 from acl-proxy-security-group ONLY
  Inbound: DENY everything else

Proxy Security Group:
  Outbound: TCP 443 to opensearch-security-group ONLY

No public endpoint on the OpenSearch domain.
```

Now even if someone has valid IAM credentials, they can't reach OpenSearch unless they're running inside the proxy's security group. A stolen IAM role from outside the VPC is useless.

---

## Step 3 — OpenSearch Security Plugin (Header Trust)

This is where you bind the DLS header trust to the IAM identity. Only requests arriving with `acl-proxy-role` credentials are permitted to inject `opensearch-security-roles` headers.

```yaml
# opensearch_security/config/config.yml

http_authenticator:
  - type: proxy
    challenge: false
    config:
      user_header: "x-proxy-user"
      roles_header: "opensearch-security-roles"
  
  - type: aws_iam       # SigV4 validated here
    challenge: false
```

```yaml
# opensearch_security/config/roles_mapping.yml

# Only the proxy IAM role gets proxy authentication rights
proxy_auth_role:
  reserved: true
  backend_roles:
    - "arn:aws:iam::YOUR_ACCOUNT:role/acl-proxy-role"

# All other IAM roles get nothing
```

The critical behavior here: OpenSearch will only honor `opensearch-security-roles` headers if the SigV4 signature maps to a role that has `proxy_auth_role`. Any other IAM identity sending those headers — including a compromised service with different credentials — has the headers silently ignored.

---

## Step 4 — What Happens If The Proxy Is Compromised

Walk through the scenarios:

**Attacker steals the proxy's IAM credentials and runs from outside VPC:**
```
SigV4 signed correctly ✓
Wrong source IP — VPC security group blocks it ✗
→ Request never reaches OpenSearch
```

**Attacker gains code execution inside the proxy process:**
```
Can inject arbitrary headers ✓
Can sign with the IAM role ✓
→ This is your real threat model — see Step 5
```

**Attacker is on the same VPC but different IAM role:**
```
Right network ✓
Wrong IAM role — domain policy Deny fires ✗
Headers ignored even if injected ✗
→ Blocked at two layers
```

---

## Step 5 — Limiting Blast Radius If The Proxy Itself Is Compromised

This is the hard one. If an attacker owns the proxy process, they have the IAM role and the network path. Your mitigations here are:

### Scope the IAM role tightly
```json
{
  "Effect": "Allow",
  "Action": [
    "es:ESHttpGet",
    "es:ESHttpPost"      
  ],
  "Resource": "arn:aws:es:...:domain/YOUR_DOMAIN/YOUR_INDEX_PREFIX*"
}
```
No `es:ESHttpPut`, no `es:ESHttpDelete`, no `es:ESHttpPatch`. A compromised proxy can't modify or delete documents — read-only against specific indices only.

### CloudTrail + anomaly detection
Every SigV4 call to OpenSearch is logged in CloudTrail. Set alarms on:
- Requests per minute exceeding baseline
- Requests outside business hours
- Unique user identities per minute spike
- Any request that doesn't include `x-proxy-user` header

### Short-lived credentials via IMDSv2 only
```
# On the EC2/ECS task running the proxy
IMDSv2 required — disable IMDSv1
Credential TTL: 1 hour (AWS default, can't shorten further)
Hop limit: 1 (blocks SSRF attacks from stealing the metadata endpoint)
```

```
aws ec2 modify-instance-metadata-options \
  --instance-id i-xxxx \
  --http-tokens required \           # IMDSv2 only
  --http-put-response-hop-limit 1    # blocks SSRF credential theft
```

---

## The Full Picture

```
Internet
  │  Okta JWT
  ▼
[ALB / API Gateway]          ← TLS termination, DDoS protection
  │
  ▼
[Proxy — Security Group A]   ← only thing in Security Group A
  │  SigV4 signed
  │  x-proxy-user + opensearch-security-roles injected
  ▼
[OpenSearch — Security Group B]
  │  Inbound: Security Group A only
  │  Domain policy: acl-proxy-role only
  │  Header trust: acl-proxy-role only
  │  DLS: enforced per role
  │  CloudTrail: every request logged
  ▼
[Documents user is entitled to see]
```

Each layer independently enforces. Compromising one layer does not defeat the others — which is the actual definition of zero-trust.
