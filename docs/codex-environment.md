# Local and Codex environments

Keep project state/build instructions portable. Put machine paths only in ignored
`docs/local-environment.md`, copied from
[`local-environment.example.md`](local-environment.example.md).

## Local checkout/worktrees

Use JDK 23+ and the Maven Wrapper:

```text
Windows:      .\mvnw.cmd clean test
Linux/macOS:  ./mvnw clean test
```

`.worktreeinclude` copies an existing optional `docs/local-environment.md` into local
Codex-managed worktrees; wrapper commands remain authoritative.

## Codex cloud

In the repository's Codex environment settings:

1. Set non-secret `CODEX_ENV_JAVA_VERSION=23`.
2. While setup has internet access, prime Maven/plugins with
   `./mvnw -B -ntp -DskipTests package`.
3. Validate agents with `./mvnw -B -ntp clean test`.

Cloud checkouts cannot inherit a laptop's ignored file. A setup script may create it for
non-secret container notes; store credentials as Codex environment secrets.

Official references: [cloud environments](https://learn.chatgpt.com/docs/environments/cloud-environment),
[managed worktrees](https://learn.chatgpt.com/docs/environments/git-worktrees), and
[`codex-universal` variables](https://github.com/openai/codex-universal#configuring-language-runtimes).
