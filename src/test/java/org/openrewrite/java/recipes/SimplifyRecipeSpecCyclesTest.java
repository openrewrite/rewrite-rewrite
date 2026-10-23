/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyRecipeSpecCyclesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyRecipeSpecCycles())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void removesCyclesTheExpectedCountImplies() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class MyTest implements RewriteTest {
                  @Test
                  void cyclesFirst() {
                      rewriteRun(
                        spec -> spec.cycles(3).expectedCyclesThatMakeChanges(2),
                        text("a", "b")
                      );
                  }

                  @Test
                  void cyclesLast() {
                      rewriteRun(
                        spec -> spec.expectedCyclesThatMakeChanges(1).cycles(2),
                        text("a", "b")
                      );
                  }

                  @Test
                  void noChangesExpected() {
                      rewriteRun(
                        spec -> spec.cycles(2).expectedCyclesThatMakeChanges(0),
                        text("a")
                      );
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class MyTest implements RewriteTest {
                  @Test
                  void cyclesFirst() {
                      rewriteRun(
                        spec -> spec.expectedCyclesThatMakeChanges(2),
                        text("a", "b")
                      );
                  }

                  @Test
                  void cyclesLast() {
                      rewriteRun(
                        spec -> spec.expectedCyclesThatMakeChanges(1),
                        text("a", "b")
                      );
                  }

                  @Test
                  void noChangesExpected() {
                      rewriteRun(
                        spec -> spec.expectedCyclesThatMakeChanges(0),
                        text("a")
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsCyclesItCannotProveRedundant() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class MyTest implements RewriteTest {
                  static final int CYCLES = 2;

                  @Test
                  void pinned() {
                      rewriteRun(
                        spec -> spec.cycles(2).expectedCyclesThatMakeChanges(2),
                        text("a", "b")
                      );
                  }

                  @Test
                  void extraVerificationCycle() {
                      rewriteRun(
                        spec -> spec.cycles(3).expectedCyclesThatMakeChanges(1),
                        text("a", "b")
                      );
                  }

                  @Test
                  void notALiteral() {
                      rewriteRun(
                        spec -> spec.cycles(CYCLES),
                        text("a")
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void classLevelSettingsDecideTheDerivedCount() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class ExpectsTwoTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.expectedCyclesThatMakeChanges(2);
                  }

                  @Test
                  void restatesTheDerivedCount() {
                      rewriteRun(
                        spec -> spec.cycles(3),
                        text("a", "b")
                      );
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class ExpectsTwoTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.expectedCyclesThatMakeChanges(2);
                  }

                  @Test
                  void restatesTheDerivedCount() {
                      rewriteRun(
                        text("a", "b")
                      );
                  }
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class FourCyclesTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.cycles(4).expectedCyclesThatMakeChanges(1);
                  }

                  @Test
                  void overridesTheClassCycles() {
                      rewriteRun(
                        spec -> spec.cycles(2),
                        text("a", "b")
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void removesCyclesFromDefaultsOnlyWhenNoTestSetsItsOwn() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class AllDefaultsTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.cycles(3).expectedCyclesThatMakeChanges(2);
                  }

                  @Test
                  void usesTheDefaults() {
                      rewriteRun(
                        text("a", "b")
                      );
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class AllDefaultsTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.expectedCyclesThatMakeChanges(2);
                  }

                  @Test
                  void usesTheDefaults() {
                      rewriteRun(
                        text("a", "b")
                      );
                  }
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class OverriddenDefaultsTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.cycles(3).expectedCyclesThatMakeChanges(2);
                  }

                  @Test
                  void expectsOneCycle() {
                      rewriteRun(
                        spec -> spec.expectedCyclesThatMakeChanges(1),
                        text("a", "b")
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhatIsLeftEmpty() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class DefaultsOnlyTest implements RewriteTest {
                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.cycles(2);
                  }

                  @Test
                  void usesTheDefaults() {
                      rewriteRun(
                        text("a", "b")
                      );
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class DefaultsOnlyTest implements RewriteTest {

                  @Test
                  void usesTheDefaults() {
                      rewriteRun(
                        text("a", "b")
                      );
                  }
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class LambdaOnlyTest implements RewriteTest {
                  @Test
                  void onlyCycles() {
                      rewriteRun(
                        spec -> spec.cycles(2),
                        text("a", "b")
                      );
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.test.SourceSpecs.text;

              class LambdaOnlyTest implements RewriteTest {
                  @Test
                  void onlyCycles() {
                      rewriteRun(
                        text("a", "b")
                      );
                  }
              }
              """
          )
        );
    }
}
