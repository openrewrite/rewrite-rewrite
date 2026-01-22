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

    @DocumentExample
    @Test
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

    @Test
    void skipModificationWhenTrailingCommentPresent() {
        // When there's a trailing comment on the previous line but no blank line,
        // we skip modification to avoid corrupting the comment placement.
        rewriteRun(
          java(
            """
              class Test {
                  private static final String VALUE = "LOGGER"; // comment
                  @Deprecated
                  String displayName = "Migrate from Plexus";
              }
              """,
            """
              class Test {
                  private static final String VALUE = "LOGGER"; // comment
                  @Deprecated
                  String displayName = "Migrate from Plexus";
              }
              """
          )
        );
    }

    @Test
    void preserveExistingBlankLineWithTrailingComment() {
        rewriteRun(
          java(
            """
              class Test {
                  private static final String LOGGER_VARIABLE_NAME = "LOGGER"; // Checkstyle requires constants to be uppercase

                  @Deprecated
                  String displayName = "Migrate from Plexus";
              }
              """
          )
        );
    }
}
