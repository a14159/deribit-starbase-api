# Goal plan: migrate tests from JUnit Jupiter to Surefire POJO tests

## Goal checkpoint

| Field | Value |
| --- | --- |
| Goal ID | `TEST-MIGRATION` |
| State | `DONE` |
| Active phase | None |
| Objective | Remove the `junit-jupiter` dependency while preserving automated execution and semantics of every Starbase test using a dependency-free Surefire POJO convention |
| Baseline | 281 tests, 0 failures, 0 errors, 0 skipped |
| Expected final | 292 tests (281 baseline + 11 assertion-contract tests) |
| Production-code scope | None |
| Protocol pause | Unchanged; this maintenance goal neither resolves nor bypasses `SPEC-01`/`ORD-07` |

Update this checkpoint after every phase. Do not mark the goal complete until every
completion criterion below is proved.

### Phase 0 working-tree record (2026-07-31)

Before migration edits, `git status --short --branch` reported:

```text
deribit-starbase-api: ## main
  M AGENTS.md
  ?? .gitignore
  ?? README.md
  ?? docs/
  ?? pom.xml
  ?? src/
deribit-api: ## doubles...origin/doubles [ahead 1]
  M STARBASE_IMPLEMENTATION_AGENT.md
  M STARBASE_IMPLEMENTATION_TRACKER.md
  ?? .idea/
  ?? deribit-api.iml
consumer repository: ## doubles (clean)
```

All pre-existing tracked and untracked files are user-owned and must be preserved. This
goal will edit only `deribit-starbase-api`; neither sibling repository is in scope.

### Phase 0 baseline evidence (complete, 2026-07-31)

- The recorded `mvn clean test` command passed with Surefire 3.5.3 and
  `org.apache.maven.surefire.junitplatform.JUnitPlatformProvider`: 281 tests, 0 failures,
  0 errors, and 0 skipped.
- The baseline reports, original test sources, and original POM are retained outside the
  worktree at
  `<local-temp-directory>\deribit-starbase-api-test-migration-baseline-20260731-183529`.
- Inventory scans found 64 Java files, 62 default-Surefire `*Test.java` files, 63 files
  importing JUnit, 62 classes containing tests, and exactly 281 `@Test` methods.
- Four ordering-annotation occurrences were confined to `FeedArbitratorTest` and
  `InstrumentRegistryTest` (one class annotation and one `@Order(1)` method each).

### Phase 1 assertion-contract evidence (complete, 2026-07-31)

- Added 11 deliberate tests in `TestAssertionsTest`; the final parity target is exactly
  292 tests.
- RED: focused `-Dtest=TestAssertionsTest test` failed during test compilation with 29
  `cannot find symbol: variable TestAssertions` errors.
- Passing: after adding the test-only helper, the same focused command ran 11 tests with
  0 failures, 0 errors, and 0 skipped under the still-present JUnit Platform provider.
- The helper explicitly throws `AssertionError`, preserves caller messages, provides
  primitive equality overloads, and returns typed checked exceptions from `assertThrows`.

### Phase 2 conversion evidence (complete, 2026-07-31)

- Converted all 63 real test classes and all 292 methods in the nine prescribed batches;
  every batch passed `mvn -DskipTests test` after conversion.
- The suite-wide scan found zero JUnit imports/annotations, 63 public final test classes,
  292 public `test*` methods, and no non-public explicit constructor.
- Exact class/name mapping accounted for all 281 original methods with zero missing or
  unexpected mappings. The other 11 methods are the recorded `TestAssertionsTest`
  contract additions.
- Normalized comparison against the retained original sources found all 64 original Java
  files present and all 1,574 original assertion invocations unchanged. The only original
  method-body delta is the intentional `FeedArbitratorTest` allocation setup, which now
  requires and enables thread-allocation measurement rather than relying on another test.
- A compile-time ambiguity for `long` versus nullable `Long` was the only helper gap. A
  narrowly tested overload resolved the two affected REST assertions without editing
  their call sites or intent.

### Phase 3 POM/provider-switch evidence (complete, 2026-07-31)

- Removed the `junit.version` property, the complete `junit-jupiter` dependency, and the
  now-empty dependencies section.
- Pinned `maven-surefire-plugin` 3.5.4 and configured
  `<enableAssertions>true</enableAssertions>` while retaining the module-path setting.
- Extended `ArtifactBaselineTest` to reject any POM JUnit reference, require the explicit
  Surefire version/assertion setting, and prove Java assertions are active at runtime.
- `mvn -DskipTests test` compiled 66 test source files successfully with Surefire 3.5.4.
  Fresh scans found zero JUnit references in both the POM and test sources.

### Phase 4 discovery/failure evidence (complete, 2026-07-31)

- A focused `TestAssertionsTest` selector used
  `org.apache.maven.surefire.junit.JUnit3Provider` and passed all 11 expected methods.
  Surefire XML method-name comparison found 11 expected, 11 reported, and zero mapping
  differences.
- A temporary terminal `AssertionError("discovery canary")` in
  `testExpectedExceptionAcceptsCheckedThrowables` produced `BUILD FAILURE`, exactly 11
  tests run and 1 failure, and the expected canary message/stack through
  `PojoTestSetExecutor`.
- The canary line was immediately removed with `apply_patch`; the identical focused
  selector then passed 11/0/0/0, and the final source/POM canary scan is empty.

### Phase 5 regression/parity evidence (complete, 2026-07-31)

- The initial repeated-run audit exposed a pre-existing observation race in
  `StarbaseMarketDataTransportLifecycleTest`: the aggregate receive counter advanced
  before the per-feed diagnostics counter. The test now waits for both already-required
  observations within its unchanged two-second deadline. Its two methods passed once
  normally and in five additional focused repetitions.
- From that fixed state, two consecutive `mvn clean test` runs passed under
  `org.apache.maven.surefire.junit.JUnit3Provider`, each with 292 tests, 0 failures, 0
  errors, and 0 skipped. First-run reports are retained at
  `<local-temp-directory>\deribit-starbase-api-test-migration-fixed-clean-run-1-20260731-185944`.
- Final XML contained 63 suites and exactly 292 testcase elements. Source-to-XML mapping
  found zero differences; original-name mapping retained all 281 baseline methods and
  explained the other 11 as `TestAssertionsTest` additions.
- No original file lost an assertion invocation: the original 1,574 remain, plus five
  new `ArtifactBaselineTest` checks for the migration contract.
- The discovered 34 allocation-sensitive classes passed 189 focused tests with no order
  annotations. `mvn dependency:tree` reported only this project artifact and no
  dependencies.
- `git diff --check` passed (apart from the pre-existing line-ending warning for
  `AGENTS.md`), and all 19 local Markdown links resolved.

### Phase 6 handoff evidence (complete, 2026-07-31)

- Updated `README.md` and `docs/implementation-status.md` with the dependency-free POJO
  convention, exact verification command, Surefire 3.5.4 provider, 292-test result, and
  verification date. Current documentation no longer describes the migration as planned.
- Changed scope: `pom.xml`; all 63 `*Test.java` classes; `BufferAssertions.java`; the new
  `TestAssertions.java`; `README.md`; `docs/implementation-status.md`; and this goal plan.
  No `src/main` file was changed.
- Final scans: zero JUnit source/POM references, zero canary residue, 63 public final test
  classes, 292 public `test*` methods, zero trailing-whitespace hits, and 19/19 valid local
  Markdown links.
- Final `git status --short --branch` output in all three repositories matched the Phase 0
  record exactly. `deribit-api` and consumer repositories were not edited.
- Every completion criterion below is satisfied. The formal maintenance goal is complete;
  the protocol checkpoint remains paused on `SPEC-01`/`ORD-07`.

## Confirmed consumer test convention

The representative consumer has no JUnit, TestNG, or other test dependency. A focused run on 2026-07-31:

```powershell
$env:JAVA_HOME='<local-jdk-home>'
& 'mvn' "-Dtest=is.fm.crypto.orders.TestClientOrderIdGenerator" test
```

reported:

```text
Using auto detected provider org.apache.maven.surefire.junit.JUnit3Provider
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

Despite its name, this provider has a dependency-free POJO execution path. Inspection of
the locally resolved Surefire 3.5.4 provider confirms that it:

- accepts a public, non-abstract class with a public no-argument constructor;
- discovers public, non-static, zero-argument `void` methods whose names start with
  `test`;
- optionally invokes public zero-argument `setUp` and `tearDown` methods;
- constructs a new test-class instance for each test method; and
- reports thrown `AssertionError` or any other exception as a failed test.

Test-method reflection order is unspecified. The migrated suite must not depend on method
order.

## Current Starbase test inventory

As of the baseline:

- 64 Java files exist under `src/test/java`;
- 62 classes contain JUnit `@Test` methods;
- 63 files import JUnit because `BufferAssertions` also delegates to JUnit;
- 281 methods carry `@Test`;
- two classes use `@TestMethodOrder`/`@Order` for an allocation test;
- the suite contains approximately 962 `assertEquals`, 273 `assertThrows`, 163
  `assertTrue`, 91 `assertFalse`, and smaller numbers of identity, null, inequality, and
  array assertions; and
- every current test filename already matches a default Surefire test-class pattern.

The migration is broad enough to warrant its own goal, but it is mechanical and does not
require a new test runner or production change.

## Fixed migration design

1. Use Maven Surefire's dependency-free `JUnit3Provider` POJO path, using the dependency-free convention.
2. Pin `maven-surefire-plugin` 3.5.4 explicitly and set `enableAssertions` to `true` for
   reproducibility. Do not add `junit:junit` or a provider dependency to the project.
3. Remove the `junit.version` property and the complete `junit-jupiter` dependency.
4. Add one small test-only utility,
   `io.contek.invoker.deribit.starbase.testutil.TestAssertions`, to preserve typed
   equality, identity, array, null, boolean, and expected-exception semantics without an
   external library.
5. `TestAssertions` must throw `AssertionError` explicitly and retain useful messages. Its
   `assertThrows` equivalent must accept checked exceptions, return the caught typed
   exception, and fail for both no exception and the wrong exception type.
6. Implement only overloads exercised by this repository. Primitive equality overloads
   must avoid accidental boxed-type mismatches such as `Integer(1)` versus `Long(1)`.
7. Convert each test class to `public final class` with an accessible no-argument
   constructor, normally the implicit constructor.
8. Convert each JUnit test method to `public void test...()`, retaining any declared
   checked exceptions. Prefix the existing descriptive method name with `test` rather
   than replacing it with a number.
9. Remove `@Test`, `@TestMethodOrder`, `@Order`, and all `org.junit` imports.
10. Make the two ordered allocation tests self-contained and order-independent. Do not
    depend on reflection naming/order to run them first.
11. Preserve one fresh test instance per method; do not introduce shared mutable fixtures
    merely to reduce edits.
12. Do not change production sources or test intent, weaken an assertion, increase a
    tolerance, remove boundary/corrupt-input coverage, or suppress an allocation failure.

Simple conditions may use the Java `assert` statement, as in consumer code, but the shared
utility is preferred where it prevents semantic drift or preserves a failure message.

## Execution phases

### Phase 0: bootstrap and baseline

1. Read `AGENTS.md`, `docs/implementation-contract.md`,
   `docs/implementation-status.md`, and this plan completely.
2. Inspect `git status --short` in this repository and any dependency or consumer repository explicitly placed in scope.
   Preserve every unrelated/user-owned change. Do not edit either sibling repository.
3. Mark this goal `IN_PROGRESS`, set the active phase, and record exact working-tree
   state here before changing files.
4. Run the current clean JUnit suite and retain the Surefire XML reports:

   ```powershell
   $env:JAVA_HOME='<local-jdk-home>'
   & 'mvn' clean test
   ```

5. Record the exact baseline count and scan results for JUnit imports, annotations, test
   classes, and test methods. The expected starting count is 281.

### Phase 1: dependency-free assertion contract

1. Add deterministic tests for `TestAssertions` before its implementation. Cover:
   primitive/object equality, identity, arrays, booleans, nulls, message propagation,
   expected exception return, no exception, wrong exception, and a checked exception.
2. Observe the intended compile failure because `TestAssertions` does not exist.
3. Implement the smallest test-only helper that passes those tests while JUnit is still
   available for this phase.
4. Record how many deliberate assertion-contract tests were added. The final expected
   test count is `281 + this number`; never accept a lower count.

The assertion-contract test class itself must be converted to POJO form with the rest of
the suite before JUnit is removed.

### Phase 2: mechanical conversion by package

Convert in bounded batches so reviews and compile errors stay local:

1. `testutil`, root, and `common`;
2. `codec/common`;
3. `codec/marketdata`;
4. `marketdata`;
5. `book` and `channel`;
6. `codec/orderentry`;
7. `orderentry/connection`;
8. `orderentry/state` and `orderentry/command`; and
9. `rest` and `protocol`.

For each batch:

- replace static JUnit imports with `TestAssertions` imports or direct Java assertions;
- make test classes and test methods public and prefix methods with `test`;
- preserve method bodies, messages, exception types, timeouts, cleanup, and resource
  ownership;
- run `mvn -DskipTests test` to compile all tests while mixed JUnit/POJO source remains;
- inspect the diff for dropped assertions or changed literals; and
- update the conversion ledger below.

Do not remove the JUnit dependency until all source files compile without `org.junit`.
With Jupiter still present, converted POJO methods may not execute, so compilation is only
an intermediate check—not passing evidence.

### Phase 3: POM/provider switch

1. Prove these scans are empty:

   ```powershell
   rg -n "org\.junit|@Test\b|@TestMethodOrder|@Order\(" src/test/java
   ```

2. Prove there are at least 281 public POJO methods and that every real test class is
   public with a no-argument constructor:

   ```powershell
   rg -n "^\s*public void test[A-Za-z0-9_]*\s*\(" src/test/java
   ```

3. Remove `junit.version` and `junit-jupiter` from `pom.xml`.
4. Keep an explicit `maven-surefire-plugin` entry, pin it to 3.5.4, and configure
   `<enableAssertions>true</enableAssertions>`.
5. Extend `ArtifactBaselineTest` so it checks that the POM contains no JUnit dependency,
   retains the explicit Surefire pin/assertion setting, and that assertions are enabled
   for its class.

### Phase 4: prove discovery and failure behavior

1. Run one converted class by selector. Confirm the log names
   `org.apache.maven.surefire.junit.JUnit3Provider` and reports every expected `test*`
   method.
2. Perform a temporary negative control with `apply_patch`: add an unmistakable
   `AssertionError("discovery canary")` to one focused test, run it, and observe Maven
   `BUILD FAILURE` for that exact reason. Immediately remove only the temporary line with
   `apply_patch` and rerun the class successfully.
3. Do not leave an intentional failure, skip flag, exclusion, or canary file in the final
   tree.

### Phase 5: full regression and parity audit

1. Run `mvn clean test` twice. Both runs must pass with the JUnit3Provider POJO path.
2. Sum Surefire XML `tests`, `failures`, `errors`, and `skipped`. The test count must equal
   the recorded final expectation (`281 + deliberate new helper tests`), with all other
   values zero.
3. Compare the final method inventory against the original 281-method inventory by
   class/name mapping. Explain every added name; no original test may disappear.
4. Run all allocation-sensitive test classes again as focused tests and confirm the
   zero-allocation assertions still pass without order annotations.
5. Run:

   ```powershell
   & 'mvn' dependency:tree
   ```

   Confirm no JUnit artifact exists in the project dependency tree.
6. Run `git diff --check` and verify documentation links.

### Phase 6: handoff

1. Update `README.md` and `docs/implementation-status.md` with the new dependency-free
   test convention, final count, exact command, provider, and verification date.
2. Update `ArtifactBaselineTest`/documentation references that still say JUnit Jupiter.
3. Mark this plan `DONE`, clear its active phase, and record files changed, commands,
   results, test-count parity, and any intentionally added tests.
4. Mark the formal goal complete only after all completion criteria pass. Do not resume
   Starbase protocol work as part of this goal.

## Conversion ledger

| Batch | State | Classes converted | Compile/test evidence | Notes |
| --- | --- | ---: | --- | --- |
| Assertion helper | DONE | 0 | RED compile failure; focused 11/0/0/0 green | 11 deliberate tests; POJO conversion belongs to batch 1 |
| Root/testutil/common | DONE | 5 | `mvn -DskipTests test` passed | 25 methods; normalized bodies and 160 assertion invocations preserved |
| Codec common | DONE | 6 | `mvn -DskipTests test` passed | 27 methods; normalized bodies and 133 assertion invocations preserved |
| Codec market data | DONE | 7 | `mvn -DskipTests test` passed | 27 methods; normalized bodies and 163 assertion invocations preserved |
| Market data | DONE | 12 | `mvn -DskipTests test` passed | 48 methods and 232 assertions preserved; `FeedArbitratorTest` now enables/requires allocation measurement itself |
| Book/channel | DONE | 8 | `mvn -DskipTests test` passed | 34 methods and 156 assertions preserved; `InstrumentRegistryTest` order annotations removed from its self-contained warm-up test |
| Codec order entry | DONE | 6 | `mvn -DskipTests test` passed | 33 methods; normalized bodies and 233 assertion invocations preserved |
| Order-entry connection | DONE | 7 | `mvn -DskipTests test` passed | 36 methods; normalized bodies and 211 assertion invocations preserved |
| Order-entry state/command | DONE | 6 | `mvn -DskipTests test` passed | 42 methods; normalized bodies and 247 assertion invocations preserved |
| REST/protocol | DONE | 6 | Initial boxed-long compile RED; final `mvn -DskipTests test` passed | 20 methods; normalized bodies and 132 assertion invocations preserved |
| POM/provider switch | DONE | 0 | Compile passed; focused 11/0/0/0; canary failed 11/1/0/0 then rerun passed | JUnit3Provider and exact method discovery proved; canary removed |
| Full parity audit | DONE | 0 | Two clean 292/0/0/0 runs; 34 allocation classes/189 tests; dependency tree empty | 281 original + 11 recorded tests; XML/source mappings exact |

## Completion criteria

The goal is complete only when:

- `pom.xml` has no `junit-jupiter`, JUnit property, or other project test dependency;
- all former JUnit tests are public Surefire POJO tests with public `test*` methods;
- the repository contains no `org.junit` import or JUnit annotation;
- the auto-detected provider is `org.apache.maven.surefire.junit.JUnit3Provider`;
- the negative discovery canary failed for the intended reason and was removed;
- the final reported count is the 281-test baseline plus only recorded helper-contract
  tests, with zero failures/errors/skips;
- focused allocation tests and two clean full runs pass;
- no production source or sibling repository changed; and
- the plan and central implementation status contain the final evidence.

## If blocked

Do not reintroduce JUnit just to make an awkward assertion compile. Extend the local
test-only helper narrowly and test the new overload. If Surefire POJO discovery cannot
preserve a test's semantics, stop with the smallest failing focused example, record the
provider output and exact class/method signature here, and request direction before adding
another framework or custom runner.
