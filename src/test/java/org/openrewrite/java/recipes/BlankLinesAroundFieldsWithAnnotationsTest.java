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

class BlankLinesAroundFieldsWithAnnotationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new BlankLinesAroundFieldsWithAnnotations());
    }

    @Test
    @DocumentExample
    void spaceBetweenFields() {
        rewriteRun(
          java(
            """
              class Test {
                  @Deprecated
                  int a;
                  int b;
                  @Deprecated
                  int c;
                  int d;
              }
              """,
            """
              class Test {
                  @Deprecated
                  int a;

                  int b;

                  @Deprecated
                  int c;

                  int d;
              }
              """
          )
        );
    }
}
