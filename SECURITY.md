# Security policy

## Supported versions

Security fixes are applied to the latest version on the `main` branch.

## Reporting a vulnerability

Please do not publish credentials, session material, private server URLs, personal photographs, or exploitable details in a public issue. Open a minimal issue asking for a private reporting channel, without including the sensitive payload.

Reports should describe the affected version, Android version, Nextcloud version, reproducible steps, expected impact, and a sanitized proof of concept. Never test against a server or account you do not own or have explicit permission to assess.

## Security boundaries

Nextcloud authenticates users and enforces permissions. Oblako is a client and does not replace server-side access control. Operators remain responsible for HTTPS, reverse-proxy security, server updates, storage security, backups, and account policy.
