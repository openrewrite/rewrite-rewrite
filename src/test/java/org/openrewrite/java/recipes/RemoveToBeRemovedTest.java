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

class RemoveToBeRemovedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveToBeRemoved());
    }

    @DocumentExample
    @Test
    void removeMethodDeclarationPastDate() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ToBeRemoved;

              class A {
                  void keep() {}

                  @ToBeRemoved(after = "2020-01-01")
                  void remove() {}
              }
              """,
            """
              class A {
                  void keep() {}
              }
              """
          )
        );
    }

    @Test
    void retainMethodInvocationStatement() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ToBeRemoved;

              class A {
                  @ToBeRemoved(after = "2020-01-01")
                  void deprecated() {}

                  void caller() {
                      System.out.println("before");
                      deprecated(); // Retained; replacement or removal should be manual
                      System.out.println("after");
                  }
              }
              """,
            """
              class A {

                  void caller() {
                      System.out.println("before");
                      deprecated(); // Retained; replacement or removal should be manual
                      System.out.println("after");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveMethodWithFutureDate() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ToBeRemoved;

              class A {
                  @ToBeRemoved(after = "2099-12-31")
                  void notYet() {}
              }
              """
          )
        );
    }

    @Test
    void removeConstructorPastDate() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ToBeRemoved;

              class A {
                  A(String s) {}

                  @ToBeRemoved(after = "2020-01-01")
                  A(String s, int i) {}
              }
              """,
            """
              class A {
                  A(String s) {}
              }
              """
          )
        );
    }

    @Test
    void removeMethodWithReason() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.ToBeRemoved;

              class A {
                  @ToBeRemoved(after = "2020-01-01", reason = "no longer needed")
                  void old() {}

                  void current() {}
              }
              """,
            """
              class A {

                  void current() {}
              }
              """
          )
        );
    }
}
