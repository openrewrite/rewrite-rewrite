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
package org.openrewrite.java.recipes.migrate;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class InlineMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          // Module generated recipes carry no `classpathFromResources`, so `InlineMethodCalls`
          // builds its template with a bare parser and can not attribute the replacement type.
          .typeValidationOptions(TypeValidation.builder().constructorInvocations(false).build())
          .recipeFromResources("org.openrewrite.recipes.rewrite.InlineMethods");
    }

    @DocumentExample
    @Test
    void staticAnalysisRemoveUnusedLocalVariables() {
        rewriteRun(
          java(
            """
              import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables;

              class Test {
                  void test() {
                      RemoveUnusedLocalVariables recipe = new RemoveUnusedLocalVariables(new String[]{"foo"}, true);
                  }
              }
              """,
            """
              import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables;

              class Test {
                  void test() {
                      RemoveUnusedLocalVariables recipe = new RemoveUnusedLocalVariables(new String[]{"foo"}, null, true);
                  }
              }
              """
          )
        );
    }
}
