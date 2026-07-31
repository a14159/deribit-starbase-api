# Local and Codex environments

The repository keeps project state and build instructions portable. Machine-specific
paths belong only in the ignored `local-environment.md`, created from
[`local-environment.example.md`](local-environment.example.md).

## Local checkout and Codex-managed worktrees

Use the checked-in Maven Wrapper with JDK 23 or newer:

```text
Windows:        .\mvnw.cmd clean test
Linux/macOS:    ./mvnw clean test
```

`.worktreeinclude` copies an existing ignored `docs/local-environment.md` into local
Codex-managed worktrees. The file is optional; the portable wrapper commands remain the
authoritative build instructions.

## Codex cloud container

Create or edit the repository's Codex cloud environment in Codex settings:

1. Add the non-secret environment variable `CODEX_ENV_JAVA_VERSION=23`. The universal
   Codex image supports this variable and installs the selected JDK before setup.
2. Use this setup script while setup-phase internet access is available:

   ```sh
   ./mvnw -B -ntp -DskipTests package
   ```

   This verifies the pinned Maven distribution, downloads build plugins, and primes the
   container cache.
3. Use `./mvnw -B -ntp clean test` for normal validation in the agent phase.

Cloud containers check out tracked Git content and cannot inherit an ignored file from a
laptop. If a container needs additional non-secret local notes, its setup script may
create `docs/local-environment.md` with container-specific values. Configure credentials
as Codex environment secrets instead of writing them to that file.

See the official documentation for [Codex cloud environments](https://learn.chatgpt.com/docs/environments/cloud-environment),
[Codex-managed worktrees](https://learn.chatgpt.com/docs/environments/git-worktrees), and
the [`codex-universal` runtime variables](https://github.com/openai/codex-universal#configuring-language-runtimes).
