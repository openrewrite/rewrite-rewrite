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

class SourceSpecTextBlockNewLineTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SourceSpecTextBlockNewLine())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void newlineAfterClosing() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       text(
                          \"""
                           class Test {
              \s
                              \s
                               void test() {
                                   System.out.println("Hello, world!");
                               }
                           }
                           \""")
                    );
                  }
              }
              """,
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       text(
                          ""\"
                           class Test {
              \s
                              \s
                               void test() {
                                   System.out.println("Hello, world!");
                               }
                           }
                           \"""
                       )
                    );
                  }
              }
              """
          )
        );
    }

    @Test
    void newlineAfterOpening() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       text(\"""
                           class Test {
              \s
                              \s
                               void test() {
                                   System.out.println("Hello, world!");
                               }
                           }
                           \"""
                       )
                    );
                  }
              }
              """,
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       text(
                            ""\"
                           class Test {
              \s
                              \s
                               void test() {
                                   System.out.println("Hello, world!");
                               }
                           }
                           \"""
                       )
                    );
                  }
              }
              """
          )
        );
    }

    @Test
    void newlineBetweenMultipleTextBlocks() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       text(
                          \"""
                           class Test {
              \s
                              \s
                               void test() {
                                   System.out.println("Hello, world!");
                               }
                           }
                           \""", ""\"
                           class Test {
              \s
                              \s
                               void test() {
                                   System.out.println("Hello, world!");
                               }
                           }
                           ""\"
                       )
                    );
                  }
              }
              """,
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       text(
                          \"""
                           class Test {
              \s
                              \s
                               void test() {
                                   System.out.println("Hello, world!");
                               }
                           }
                           \""",
                            ""\"
                           class Test {
              \s
                              \s
                               void test() {
                                   System.out.println("Hello, world!");
                               }
                           }
                           ""\"
                       )
                    );
                  }
              }
              """
          )
        );
    }

    @Test
    void newlineWithConsumer() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       text(
                          \"""
                           class Test {
              \s
                              \s
                               void test() {
                                   System.out.println("Hello, world!");
                               }
                           }
                           \""", sourceSpecs -> {}
                       )
                    );
                  }
              }
              """,
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       text(
                          ""\"
                           class Test {
              \s
                              \s
                               void test() {
                                   System.out.println("Hello, world!");
                               }
                           }
                           \""",
                            sourceSpecs -> {
                            }
                       )
                    );
                  }
              }
              """
          )
        );
    }

    @Test
    void builderToString() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       text(
                          \"""
                           class Test {
              \s
                              \s
                               void test() {
                                   System.out.println("Hello, world!");
                               }
                           }
                           \"""
                       )
                    );
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenNotTextBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       text("(1 + 1)"),
                       text("(1 + 1)", "1 + 1")
                    );
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotDuplicateComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       //language=markdown
                       text(
                         \"""
                         # Header
                         \""")
                    );
                  }
              }
              """,
            """
              import org.openrewrite.test.RewriteTest;
              import static org.openrewrite.test.SourceSpecs.text;

              class MyRecipeTest implements RewriteTest {
                  void test() {
                    rewriteRun(
                       //language=markdown
                       text(
                         \"""
                         # Header
                         \"""
                       )
                    );
                  }
              }
              """
          )
        );
    }
}
