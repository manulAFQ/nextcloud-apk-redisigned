# Contributing

Contributions that improve Nextcloud compatibility, correctness, performance, accessibility, translations, tests, and the photo-first Android experience are welcome.

## Development workflow

1. Create a focused branch.
2. Keep authentication and media traffic direct between Android and Nextcloud.
3. Do not add analytics, advertising, hidden telemetry, proprietary relay services, or hard-coded accounts.
4. Keep auto-upload and folder synchronization opt-in by default.
5. Add or update tests for behavioral changes.
6. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` before opening a pull request.

Do not commit `local.properties`, signing keys, app passwords, access tokens, real server URLs, personal media, database files, caches, or generated build outputs.
