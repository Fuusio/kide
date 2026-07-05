<!-- Thanks for contributing! See CONTRIBUTING.md for conventions and invariants. -->

## What & why

<!-- What does this change, and what problem does it solve? Link related issues. -->

## Checklist

- [ ] `./gradlew build` is green (compiles, tests pass, ABI check passes)
- [ ] Tests added/updated for behavior changes
- [ ] `./gradlew updateKotlinAbi` run and `api/` dumps committed (if public API changed)
- [ ] KDoc on new public declarations; `CHANGELOG.md` updated for user-visible changes
- [ ] Core-module dependency rule respected (`kide` = kotlinx-coroutines only)
