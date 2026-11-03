# Private Desktop pairing

Implemented in the commercial development fork. This is an authenticated local
connection, over LAN or the user's own [Tailscale network](TAILSCALE.md). The separate [mobile vault](VAULT.md)
protects downloaded app data in the new emulator-tested build. The personal app,
its data, and its ComfyUI instance remain separate.

## Use

1. Open **bwdacc Desktop Preview**, prepare the managed runtime, download the
   desired models, and start the engine.
2. Open **Dispositivos**, select the computer's LAN or Tailscale address, and activate
   the connection. If Windows requests network access, restrict access to the chosen private network.
   This build does not create firewall rules or router port forwards.
3. Choose **Parear celular** to generate a QR. In **bwdacc dev** on Android, open **Ajustes > Conexão >
   Parear com Desktop**. Scan the QR, or explicitly paste the copied pairing code.
4. Confirm the named phone on the computer. The name is a display label, not proof
   of identity: approve only the phone you are currently pairing.
5. Revoke a phone in **Dispositivos** to stop new requests and abort its active
   transfers and progress connection. Media already downloaded to the phone is
   not removed by revocation.

The gateway must be active while using the phone. Explicit activation persists
and resumes on Desktop startup. It binds only to the saved RFC1918 LAN IPv4 or
an address assigned to the Tailscale adapter in 100.64.0.0/10, plus loopback.
An absent interface waits without falling back to another network. Explicit
deactivation persists. DHCP/address changes require another QR; automatic
endpoint migration is not implemented. Remove the old device entry after replacing
pairing. Remote access uses Tailscale on both devices; never forward ComfyUI ports.

## Trust and data flow

Android authenticates the Desktop's public-key identity from the QR. Desktop
authenticates Android using a separate random credential for each approved phone.
The authenticated gateway forwards only the app's required HTTP routes and
WebSocket progress to the managed ComfyUI instance on `127.0.0.1:8189`.
Its LAN HTTPS listener normally uses port `8190`. ComfyUI itself remains bound
to loopback. Manager endpoints, raw terminal logs, arbitrary backend URLs, and
unneeded mutations are not forwarded.

A paired phone is a trusted controller for this user's studio. Generation
workflows execute trusted installed node code with the Windows user's permissions.
The route allowlist is not a sandbox for arbitrary workflow graphs or a boundary
between mutually untrusted tenants. Do not use this gateway as a shared cloud
service. Software running as the same Windows user and the OS remain trusted;
loopback PID checks are not cryptographic protection from a malicious local OS.

The current runtime still stores input/output media in plaintext. The separate
Android vault now encrypts generated media, prompts, thumbnails and project
metadata in the new development build. Pairing encryption alone is not the vault;
see VAULT.md for migration, device testing and key-loss boundaries.

## Protocol

- TLS 1.2/1.3, with a persistent RSA-3072 Desktop identity. Private-key material
  saved by the manager is protected with Windows DPAPI for the current user.
  Windows Schannel additionally uses a temporary user-key container associated
  with the active server certificate; the manager does not export a plaintext PFX
  file or install a trusted CA.
- Each server certificate has an IP-address SAN for the selected listener,
  plus loopback for the remote bridge, server-authentication EKU, and a one-year validity. A fresh certificate on the
  same Desktop key preserves its SHA-256 SPKI fingerprint.
- An in-memory invitation contains a 256-bit random ticket, lasts two minutes,
  and is consumed atomically before asking for approval. New QR generation
  invalidates the prior QR. Approval times out after sixty seconds.
- The local v1 invitation format is `bwdacc://pair` with exactly `v`, `endpoint`, `pin`,
  `ticket`, and `expires`. Android accepts no duplicate/unknown fields, credentials
  in URLs, DNS hosts, public IP endpoints, or cleartext pairing. Tailscale's default
  CGNAT IPv4 range is accepted with the same identity checks. Historical remote v2 adds
  exactly `relay` and `relayKey`, requires the inner endpoint on 127.0.0.1, and
  separately validates the allowlisted public relay. V3 uses the same fields for
  an explicitly configured fixed HTTPS hostname. It preserves the relay admission
  across restart; provider credentials never enter QR/Android. See REMOTE.md.
- Android uses a connection-scoped trust manager that checks the certificate's
  validity, self-signature, usage, and SPKI. Normal hostname verification remains
  enabled. There is no global trust-all certificate handler.
- A successful approved pairing issues a 256-bit random bearer token. Desktop
  persists only its SHA-256 hash and device metadata in a DPAPI-protected registry.
  No token appears in a URL or in ComfyUI's upstream request headers.
- Android protects its endpoint, pin, token, and device ID with AES-256-GCM and
  an Android Keystore key, using an atomic file in `noBackupFilesDir`. Corruption
  or missing keys fail closed and require pairing again. The camera activity is
  not exported and prevents screenshots. Scanning is offline.
- All commercial HTTP and WSS operations use the same paired-origin transport.
  Cross-origin requests, cleartext connections, and redirects are refused.
  Direct unauthenticated ComfyUI URL entry is no longer a commercial connection
  path; the personal app is unchanged.
- Authenticated responses use `Cache-Control: no-store`. Gateway logging
  providers are disabled. Bodies stream without gateway temporary media files.
  Pairing requests are limited to 4 KB and 10 attempts/minute per source IP, with
  a bounded address table; prompts to 10 MB; uploads to 512 MB. Connections and
  WebSocket messages also have limits. These bounds are not a guarantee against
  denial of service by devices on the same network.
- A saved revocation removes the credential and cancels its active request and
  WebSocket lifetimes. Stopping Desktop cancels the gateway as well.

The QR code and explicitly copied fallback code are secrets until consumed or
expired. Clipboard history is controlled by the OS and is not erased by this app.
Loss of the Windows identity requires repairing pairing; no identity is silently
regenerated when an existing device registry lacks its private key.

## Validation

`desktop/build.ps1 -Test` runs 15 installer/runtime, ten real TLS gateway, seven
carrier, eight persistent-remote regression checks, and seven private-connection
checks on a PC with an assigned private adapter. See TAILSCALE.md for current
validation and physical-device limits. Gateway checks cover unauthorized access, wrong identity, invalid/
expired/replayed invitations, denied approval, JSON/media/upload/WSS forwarding,
blocked admin routes/origins/redirects, upstream credential isolation, backend
ownership, persistent identity, and revocation of an existing WSS connection.

The isolated Android test APK lives under `tests/android-pairing`, not the product
manifest. `run.ps1` refuses physical phones. It tests the actual Android transport
and progress classes against an explicitly started, loopback-only synthetic
fixture. The fixture's automatic approval is test-only and never used by the UI.

Validated on API 36 emulator: QR decoding, wrong SPKI and hostname rejection,
pairing, encrypted credential storage across process restart, HTTP generation
requests, chunked uploads, binary download, WSS progress, origin/redirect checks,
revocation, and fail-closed behavior after credential corruption. Camera optics
and private-network firewall behavior still require a physical-device pilot.
The actual commercial UI also completed manual-code pairing and displayed its
authenticated connection/queue state in the emulator. Pairing controls were
checked against status/navigation bar insets. Desktop's new page was rendered
and visually inspected. The updated manager was installed with a verified
shortcut target; the commercial APK was installed separately on the user's
phone, without pairing or changing the personal package.

A real managed-runtime integration test creates one synthetic 64×64 EmptyImage
PNG, retrieves its history and output through authenticated HTTPS, and uploads
that PNG back as a reference. It downloads no model weights, submits no model
inference, and does not access the personal runtime.

```powershell
.\desktop\artifacts\bwdacc-desktop.exe --smoke-connection <managed-test-root> <report.json>
```

Test reports and fixture secrets stay in ignored `desktop/artifacts`. The
development EXE includes explicit test CLI modes; release packaging must exclude
test harnesses. These checks and the design review are not a full security audit.

## Implementation references

- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android TLS and hostname validation](https://developer.android.com/privacy-and-security/security-ssl)
- [Kestrel HTTPS endpoints](https://learn.microsoft.com/en-us/aspnet/core/fundamentals/servers/kestrel/endpoints)
- [Windows TLS ephemeral-key limitation](https://learn.microsoft.com/en-us/dotnet/core/extensions/sslstream-troubleshooting#handshake-failed-with-ephemeral-keys)
- [QRCoder](https://github.com/codebude/QRCoder), MIT, pinned NuGet lock
- [ZXing](https://github.com/zxing/zxing), Apache-2.0, pinned JAR digest
