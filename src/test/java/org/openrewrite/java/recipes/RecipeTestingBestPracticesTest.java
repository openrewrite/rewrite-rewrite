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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RecipeTestingBestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.recipes.rewrite.OpenRewriteRecipeBestPractices")
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @Test
    void collapsesSingleReturnLambdaWithInlineActual() {
        rewriteRun(
          java(
            """
              import org.openrewrite.test.RewriteTest;

              import java.util.function.UnaryOperator;

              import static org.assertj.core.api.Assertions.assertThat;

              class FooTest implements RewriteTest {
                  UnaryOperator<String> assertion() {
                      return after -> {
                          assertThat(after).contains("~~>");
                          return after;
                      };
                  }
              }
              """,
            """
              import org.openrewrite.test.RewriteTest;

              import java.util.function.UnaryOperator;

              import static org.assertj.core.api.Assertions.assertThat;

              class FooTest implements RewriteTest {
                  UnaryOperator<String> assertion() {
                      return after -> assertThat(after).contains("~~>").actual();
                  }
              }

              """
          )
        );
    }
}
