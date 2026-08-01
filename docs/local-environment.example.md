# Optional local environment

Copy to ignored `docs/local-environment.md`; `.worktreeinclude` carries it into local
Codex worktrees. Keep only machine launch details here—project state belongs in
`implementation-status.md`, and secrets belong in credential storage.

```powershell
$env:JAVA_HOME='<absolute JDK 23+ path>'
.\mvnw.cmd clean test
```

```sh
export JAVA_HOME='<absolute JDK 23+ path>'
./mvnw clean test
```

Workstation-only IDE launchers may follow; never copy their absolute paths into tracked
docs.
