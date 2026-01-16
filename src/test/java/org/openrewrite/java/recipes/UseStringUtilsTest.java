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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseStringUtilsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseStringUtilsRecipes());
    }

    @DocumentExample
    @Test
    void replaceNullAndEmptyCheck() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean test(String str) {
                      return str != null && !str.isEmpty();
                  }
              }
              """,
            """
              import org.openrewrite.internal.StringUtils;

              class Test {
                  boolean test(String str) {
                      return StringUtils.isNotEmpty(str);
                  }
              }
              """
          )
        );
    }

    @Nested
    class StringIsNotEmptyTests {
        @Test
        void inIfCondition() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      void test(String name) {
                          if (name != null && !name.isEmpty()) {
                              System.out.println(name);
                          }
                      }
                  }
                  """,
                """
                  import org.openrewrite.internal.StringUtils;

                  class Test {
                      void test(String name) {
                          if (StringUtils.isNotEmpty(name)) {
                              System.out.println(name);
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noChangeWhenDifferentVariables() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      boolean test(String a, String b) {
                          return a != null && !b.isEmpty();
                      }
                  }
                  """
              )
            );
        }
    }
}
