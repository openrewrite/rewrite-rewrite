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

class ReplaceNullWithDoesNotExistTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceNullWithDoesNotExist())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void replaceFirstNullArgument() {
        rewriteRun(
          java(
            """
              import static org.openrewrite.java.Assertions.java;

              class Test {
                  void test() {
                      java(null, "after content");
                  }
              }
              """,
            """
              import static org.openrewrite.java.Assertions.java;

              import static org.openrewrite.test.RewriteTest.doesNotExist;

              class Test {
                  void test() {
                      java(doesNotExist(), "after content");
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceSecondNullArgument() {
        rewriteRun(
          java(
            """
              import static org.openrewrite.java.Assertions.java;

              class Test {
                  void test() {
                      java("before content", null);
                  }
              }
              """,
            """
              import static org.openrewrite.java.Assertions.java;
              import static org.openrewrite.test.RewriteTest.doesNotExist;

              class Test {
                  void test() {
                      java("before content", doesNotExist());
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceOnlyFirstNullWhenBothAreNull() {
        rewriteRun(
          java(
            """
              import static org.openrewrite.java.Assertions.java;

              class Test {
                  void test() {
                      java(null, null);
                  }
              }
              """,
            """
              import static org.openrewrite.java.Assertions.java;
              import static org.openrewrite.test.RewriteTest.doesNotExist;

              class Test {
                  void test() {
                      java(doesNotExist(), null);
                  }
              }
              """
          )
        );
    }

    @Test
    void handleYamlAssertions() {
        rewriteRun(
          java(
            """
              import static org.openrewrite.yaml.Assertions.yaml;

              class Test {
                  void test() {
                      yaml(null, "content: value");
                  }
              }
              """,
            """
              import static org.openrewrite.test.RewriteTest.doesNotExist;
              import static org.openrewrite.yaml.Assertions.yaml;

              class Test {
                  void test() {
                      yaml(doesNotExist(), "content: value");
                  }
              }
              """
          )
        );
    }

    @Test
    void handleThreeArgumentMethods() {
        rewriteRun(
          java(
            """
              import static org.openrewrite.java.Assertions.java;

              class Test {
                  void test() {
                      java(null, "after", spec -> spec.path("Test.java"));
                  }
              }
              """,
            """
              import static org.openrewrite.java.Assertions.java;
              import static org.openrewrite.test.RewriteTest.doesNotExist;

              class Test {
                  void test() {
                      java(doesNotExist(), "after", spec -> spec.path("Test.java"));
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonNullArguments() {
        rewriteRun(
          java(
            """
              import static org.openrewrite.java.Assertions.java;

              class Test {
                  void test() {
                      java("before", "after");
                      java("single argument");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNullInNonAssertionMethods() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      someMethod(null, null);
                      String.valueOf(null);
                  }

                  void someMethod(Object a, Object b) {
                  }
              }
              """
          )
        );
    }

    @Test
    void handleNullInNestedCalls() {
        rewriteRun(
          java(
            """
              import static org.openrewrite.java.Assertions.java;
              import static org.openrewrite.java.Assertions.srcMainJava;

              class Test {
                  void test() {
                      rewriteRun(
                          java(null, "after"),
                          srcMainJava(null, "after")
                      );
                  }

                  void rewriteRun(Object... sources) {
                  }
              }
              """,
            """
              import static org.openrewrite.java.Assertions.java;
              import static org.openrewrite.java.Assertions.srcMainJava;
              import static org.openrewrite.test.RewriteTest.doesNotExist;

              class Test {
                  void test() {
                      rewriteRun(
                          java(doesNotExist(), "after"),
                          srcMainJava(doesNotExist(), "after")
                      );
                  }

                  void rewriteRun(Object... sources) {
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveFormatting() {
        rewriteRun(
          java(
            """
              import static org.openrewrite.java.Assertions.java;

              class Test {
                  void test() {
                      java(
                          null,
                          \"\"\"
                          public class Foo {
                          }
                          \"\"\"
                      );
                  }
              }
              """,
            """
              import static org.openrewrite.java.Assertions.java;
              import static org.openrewrite.test.RewriteTest.doesNotExist;

              class Test {
                  void test() {
                      java(
                          doesNotExist(),
                          \"\"\"
                          public class Foo {
                          }
                          \"\"\"
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void handleFullyQualifiedAssertionCalls() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      org.openrewrite.java.Assertions.java(null, "after");
                  }
              }
              """,
            """
              import static org.openrewrite.test.RewriteTest.doesNotExist;

              class Test {
                  void test() {
                      org.openrewrite.java.Assertions.java(doesNotExist(), "after");
                  }
              }
              """
          )
        );
    }
}
