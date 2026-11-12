# Security

<p><b>English</b> · <a href="SECURITY.pl.md">Polski</a></p>

## Supported versions

The project is in early access. Security fixes go only into the latest release and the `main` branch.

## Reporting a vulnerability

Do not report vulnerabilities in public issues. Use GitHub's private reporting: **Security → Report a vulnerability** in this repository (GitHub Private Vulnerability Reporting). Include:
- the app version (or commit), device and Android version;
- the Sunshine version and host OS, if relevant;
- steps to reproduce and the impact.

We aim to reply within 7 days. After a fix is released we publish an advisory and thank the reporter (if they wish).

Vulnerabilities in the streaming layer that comes from upstream (`nvstream`, `moonlight-common-c`) should also be reported to the Moonlight project. Host vulnerabilities go to the Sunshine project.

## Threat model (short)

- The app connects only to hosts you paired with a PIN (pairing and encryption come from upstream).
- It collects no data and has no telemetry. Crash logs stay on the device; you export them manually.
- Imported profiles (`.thumbcast.json`) are untrusted data: the parser rejects unknown fields and types, and the validator checks ranges. A bug that lets a profile bypass validation (for example to add a sequence of inputs) is treated as a vulnerability.
- Do not expose Sunshine's ports to the internet. To play away from home, use a VPN ([docs/tailscale.md](docs/tailscale.md)).

## Release signatures

APKs on GitHub Releases are signed with the project key, with a `SHA256SUMS.txt` file next to them. To check:

```bash
sha256sum -c SHA256SUMS.txt
apksigner verify --print-certs thumbcast-*.apk
```

The certificate fingerprint will be published in the README after the first release.
