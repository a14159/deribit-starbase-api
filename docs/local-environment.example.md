# Optional local environment

Copy this file to `docs/local-environment.md` and replace the placeholders with values for
your workstation. The destination is ignored by Git and copied into Codex-managed local
worktrees through `.worktreeinclude`.

Keep only machine-specific launch configuration here. Project state, test results,
blockers, and restart instructions belong in `implementation-status.md`. An ignored file
is not a secrets store; use environment or credential-management facilities for secrets.

## PowerShell example

```powershell
$env:JAVA_HOME='<absolute path to a JDK 23 or newer installation>'
.\mvnw.cmd clean test
```

## POSIX shell example

```sh
export JAVA_HOME='<absolute path to a JDK 23 or newer installation>'
./mvnw clean test
```

Optional IDE launchers or other workstation-only preferences may be recorded below. Do
not copy their absolute paths into tracked documentation.
