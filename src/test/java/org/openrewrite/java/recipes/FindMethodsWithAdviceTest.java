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

class FindMethodsWithAdviceTest implements RewriteTest {

    private static final String KEEP_STATE_ELSEWHERE = "Keep state in a `ScanningRecipe` accumulator";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void marksMethodCallsWithAdvice() {
        rewriteRun(
          spec -> spec.recipe(new FindMethodsWithAdvice("org.openrewrite.ExecutionContext computeMessageIfAbsent(..)", true, KEEP_STATE_ELSEWHERE)),
          java(
            """
              import org.openrewrite.ExecutionContext;

              import java.util.HashSet;
              import java.util.Set;

              class Dedup {
                  Set<String> seen(ExecutionContext ctx) {
                      return ctx.computeMessageIfAbsent("seen", k -> new HashSet<>());
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;

              import java.util.HashSet;
              import java.util.Set;

              class Dedup {
                  Set<String> seen(ExecutionContext ctx) {
                      return /*~~(Keep state in a `ScanningRecipe` accumulator)~~>*/ctx.computeMessageIfAbsent("seen", k -> new HashSet<>());
                  }
              }
              """
          )
        );
    }

    @Test
    void marksConstructorCalls() {
        rewriteRun(
          spec -> spec.recipe(new FindMethodsWithAdvice("org.openrewrite.marker.AlreadyReplaced <constructor>(..)", null, "Check the tree instead")),
          java(
            """
              import org.openrewrite.Tree;
              import org.openrewrite.marker.AlreadyReplaced;

              class Plan {
                  AlreadyReplaced plan() {
                      return new AlreadyReplaced(Tree.randomId(), "plan", "sha256");
                  }
              }
              """,
            """
              import org.openrewrite.Tree;
              import org.openrewrite.marker.AlreadyReplaced;

              class Plan {
                  AlreadyReplaced plan() {
                      return /*~~(Check the tree instead)~~>*/new AlreadyReplaced(Tree.randomId(), "plan", "sha256");
                  }
              }
              """
          )
        );
    }

    @Test
    void marksMethodReferences() {
        rewriteRun(
          spec -> spec.recipe(new FindMethodsWithAdvice("org.openrewrite.ExecutionContext getMessages()", true, KEEP_STATE_ELSEWHERE)),
          java(
            """
              import org.openrewrite.ExecutionContext;

              import java.util.Map;
              import java.util.function.Supplier;

              class Messages {
                  Supplier<Map<String, Object>> messages(ExecutionContext ctx) {
                      return ctx::getMessages;
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;

              import java.util.Map;
              import java.util.function.Supplier;

              class Messages {
                  Supplier<Map<String, Object>> messages(ExecutionContext ctx) {
                      return /*~~(Keep state in a `ScanningRecipe` accumulator)~~>*/ctx::getMessages;
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesOtherMethodsAlone() {
        rewriteRun(
          spec -> spec.recipe(new FindMethodsWithAdvice("org.openrewrite.test.RecipeSpec cycles(int)", null, "Drop `cycles(..)`")),
          java(
            """
              import org.openrewrite.test.RecipeSpec;

              class PlainTest {
                  void defaults(RecipeSpec spec) {
                      spec.expectedCyclesThatMakeChanges(2);
                  }
              }
              """
          )
        );
    }
}
