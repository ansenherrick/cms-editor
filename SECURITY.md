# Security model and production launch requirements

## Protections implemented in the prototype

- The server binds to `127.0.0.1` by default; it is not publicly reachable by
  default.
- All CMS routes require authorization. Local mode only accepts loopback
  requests. Production mode fails to start without configured access rules.
- Authorization is checked for every website ID and distinguishes `OWNER`,
  `EDITOR`, and `VIEWER` roles. A user cannot access another website merely by
  changing its ID in a request URL.
- Write operations are rate-limited, reject declared bodies above 1 MiB, and
  produce structured audit events without recording post content or access
  values.
- Responses set `X-Content-Type-Options`, `X-Frame-Options`,
  `Referrer-Policy`, and `Cache-Control: no-store`.
- The iOS app keeps access tokens in the device Keychain using
  `WhenUnlockedThisDeviceOnly`, never in `UserDefaults`, source code, or its
  website-profile data.
- iOS permits HTTP only for loopback development URLs. Hosted APIs must use
  HTTPS.

## Current production configuration boundary

Set `CMS_ENVIRONMENT=production` and use a private deployment configuration
source to provide `CMS_ACCESS_TOKENS`. Each rule has this shape:

```text
token|subject|role|website-id,website-id
```

Rules are separated by semicolons. `role` is `OWNER`, `EDITOR`, or `VIEWER`.
This static-token mechanism is an interim deployment gate, not the final public
account system. Never put these values in the repository, an `.env` file that is
committed, or the iOS app.

Also set `CMS_BIND_HOST` to a private interface behind a managed TLS reverse
proxy. Do not bind a production process directly to the public internet.

## Required before public launch

1. Replace `StaticTokenAccessControl` with an OIDC provider and short-lived,
   audience-validated JWT access tokens. The authorization layer should load
   per-website roles from a database, not from an environment value.
2. Replace `FileBackedCmsProvider` with a transactional database-backed provider
   and encrypt backups. Keep Framer access values in a managed server-side
   secret store.
3. Put the API behind HTTPS with certificate management, a web application
   firewall, distributed rate limiting, monitoring, and alerts.
4. Persist audit records to a protected, append-only log with retention and
   deletion policies; avoid logging post content and authentication material.
5. Add account recovery, email verification, session revocation, MFA for
   owners, invitation flows, privacy policy, data export/deletion, and abuse
   reporting.
6. Run dependency vulnerability scanning, SAST, DAST, load tests, threat
   modeling, penetration testing, backups/restores, and an incident-response
   exercise before launch.

Ktor supports custom bearer-token and JWT authentication workflows; see the
[Ktor authentication documentation](https://ktor.io/docs/server-auth.html).
Apple documents the Keychain as encrypted storage for small app secrets in its
[Keychain Services documentation](https://developer.apple.com/documentation/security/keychain-services/).
