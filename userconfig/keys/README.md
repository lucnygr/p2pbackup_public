This directory contains the users keystores and certificates used for local development.
Generate with P2PBackupKeyGenerator.

Test keys are not stored here or committed anywhere: `TestKeyFixtures` (module `application.test`) generates
`<user>.pfx` / `<user>.pem` once per test run into `application.test/target/test-keys` and copies them into each test's
config directory. `*.pfx` and `*.pem` stay gitignored.
