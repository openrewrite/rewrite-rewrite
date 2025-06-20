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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class IsLiteralNullTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new IsLiteralNullRecipe());
    }

    @DocumentExample
    @Test
    void expressionArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.java.tree.Expression;
              import org.openrewrite.java.tree.J;
              class Test {
                  void test(Expression e) {
                      if (e instanceof J.Literal && ((J.Literal) e).getValue() == null) {
                          System.out.println("null");
                      }
                  }
              }
              """,
            """
              import org.openrewrite.java.tree.Expression;
              import org.openrewrite.java.tree.J;
              class Test {
                  void test(Expression e) {
                      if (J.Literal.isLiteralValue(e, null)) {
                          System.out.println("null");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void binaryRight() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.java.tree.Expression;
              import org.openrewrite.java.tree.J;
              class Test {
                  void test(J.Binary b) {
                      if (b.getRight() instanceof J.Literal &&
                          ((J.Literal) b.getRight()).getValue() == null) {
                          System.out.println("null");
                      }
                  }
              }
              """,
            """
              import org.openrewrite.java.tree.Expression;
              import org.openrewrite.java.tree.J;
              class Test {
                  void test(J.Binary b) {
                      if (J.Literal.isLiteralValue(b.getRight(), null)) {
                          System.out.println("null");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeOnMismatchedCast() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.java.tree.Expression;
              import org.openrewrite.java.tree.J;
              class Test {
                  void test(J.Binary b) {
                      if (b.getLeft() instanceof J.Literal &&
                          ((J.Literal) b.getRight()).getValue() == null) {
                          System.out.println("null");
                      }
                  }
              }
              """
          )
        );
    }
}
