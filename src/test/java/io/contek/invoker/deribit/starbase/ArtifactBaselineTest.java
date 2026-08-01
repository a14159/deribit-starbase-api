package io.contek.invoker.deribit.starbase;

import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertEquals;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertFalse;
import static io.contek.invoker.deribit.starbase.testutil.TestAssertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ArtifactBaselineTest {
  public void testArtifactTargetsJava23AndKeepsLocalInstructionBootstrap() throws IOException {
    String pom = Files.readString(Path.of("pom.xml"));
    String instructions = Files.readString(Path.of("AGENTS.md"));

    assertTrue(pom.contains("<groupId>io.contek.invoker</groupId>"));
    assertTrue(pom.contains("<artifactId>invoker-deribit-starbase-api</artifactId>"));
    assertTrue(instructions.contains("docs/implementation-contract.md"));
    assertTrue(instructions.contains("docs/implementation-status.md"));
    assertTrue(Files.isRegularFile(Path.of("docs/implementation-contract.md")));
    assertTrue(Files.isRegularFile(Path.of("docs/implementation-status.md")));
    assertEquals("io.contek.invoker.deribit.starbase", ArtifactBaseline.PACKAGE_ROOT);
    boolean assertionsEnabled = false;
    assert assertionsEnabled = true;
    assertTrue(assertionsEnabled, "Surefire must enable Java assertions");
  }
}
