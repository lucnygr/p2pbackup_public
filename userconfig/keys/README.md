This directory contains the users keystores and certificates used for local development.
Generate with P2PBackupKeyGenerator.

Test keys are not stored here or committed anywhere: `TestKeyFixtures` (module `application.test`) generates
`<user>.pfx` / `<user>.pem` into each test's config directory. `*.pfx` and `*.pem` stay gitignored.
