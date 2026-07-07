# Security Policy

## Supported Versions

Kide is distributed as a set of modules published to Maven Central under the
`org.fuusio.kide` group, all sharing a single version. Security fixes are applied to
the latest released minor version only. Older versions do not receive backported
patches — please upgrade to the latest release before reporting an issue where possible.

| Version | Supported          |
| ------- | ------------------ |
| 1.1.x   | :white_check_mark: |
| 1.0.x   | :x:                |
| < 1.0   | :x:                |

## Reporting a Vulnerability

Please **do not** report security vulnerabilities through public GitHub issues,
pull requests, or discussions.

Instead, use one of the following private channels:

- **Preferred:** GitHub's private vulnerability reporting. Go to the
  [Security tab](https://github.com/Fuusio/kide/security) of the repository and click
  **"Report a vulnerability"**. This opens a private advisory visible only to you and
  the maintainers.
- **Email:** If you cannot use GitHub, send the details to
  **fuusio.info@gmail.com** with a subject line beginning with `[Kide Security]`.

To help us triage quickly, please include as much of the following as you can:

- The affected module(s) and version(s) (e.g. `kide-clean-architecture:1.1.0`).
- A description of the vulnerability and its potential impact.
- Steps to reproduce, a proof-of-concept, or a failing test if available.
- Any known workarounds or suggested mitigations.

### What to expect

- **Acknowledgement:** We aim to acknowledge your report within **3 business days**.
- **Assessment:** Within **10 business days** we will confirm whether we can reproduce
  the issue, decide whether it is accepted as a vulnerability, and share an initial
  assessment of severity.
- **Progress updates:** While an accepted report is being worked on, you can expect an
  update on its status at least **every 7 days** until it is resolved or closed.

**If the vulnerability is accepted**, we will work on a fix, keep you informed of the
timeline, and prepare a new release. Unless you request otherwise, we will credit you in
the release notes and the published GitHub Security Advisory once a fix is available.
We follow a coordinated-disclosure approach and ask that you keep the details private
until a patched release has been published.

**If the report is declined** (for example, it is out of scope, a duplicate, a
configuration issue on the reporting side, or not reproducible), we will explain the
reasoning so you can follow up if you believe the decision was made in error.

Thank you for helping keep Kide and its users safe.
