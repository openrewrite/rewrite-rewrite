/*
 * Copyright 2025 the original author or authors.
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

class ReorderTestMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new ReorderTestMethods())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void reorderTestMethods() {
        rewriteRun(
          //language=java
          java(
            """
              package org.openrewrite.java.cleanup;

              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.DocumentExample;
              import org.openrewrite.Recipe;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class UnnecessaryParenthesesTest implements RewriteTest {

                  private static String helper() {
                      return "value";
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                        java(
                          \"""
                            BEFORE
                            \""",
                          \"""
                            AFTER
                            \"""
                        )
                      );
                  }

                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.recipe(Recipe.noop());
                  }

                  @BeforeEach void foo(){}

                  @AfterEach void bar(){}

                  @DocumentExample
                  @Test
                  void test1() {
                      rewriteRun(
                        java(
                          \"""
                            BEFORE
                            \""",
                          \"""
                            AFTER
                            \"""
                        )
                      );
                  }

                  @AfterAll static void bar(){}

                  private static int intHelper() {
                      return 42;
                  }
              }
              """,
            """
              package org.openrewrite.java.cleanup;

              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.openrewrite.DocumentExample;
              import org.openrewrite.Recipe;
              import org.openrewrite.test.RecipeSpec;
              import org.openrewrite.test.RewriteTest;

              import static org.openrewrite.java.Assertions.java;

              class UnnecessaryParenthesesTest implements RewriteTest {

                  @BeforeEach void foo(){}

                  @AfterEach void bar(){}

                  @AfterAll static void bar(){}

                  @Override
                  public void defaults(RecipeSpec spec) {
                      spec.recipe(Recipe.noop());
                  }

                  @DocumentExample
                  @Test
                  void test1() {
                      rewriteRun(
                        java(
                          \"""
                            BEFORE
                            \""",
                          \"""
                            AFTER
                            \"""
                        )
                      );
                  }

                  private static String helper() {
                      return "value";
                  }

                  @Test
                  void test2() {
                      rewriteRun(
                        java(
                          \"""
                            BEFORE
                            \""",
                          \"""
                            AFTER
                            \"""
                        )
                      );
                  }

                  private static int intHelper() {
                      return 42;
                  }
              }
              """
          )
        );
    }
}
