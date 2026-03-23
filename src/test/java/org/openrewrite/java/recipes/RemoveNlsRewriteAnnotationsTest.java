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

class RemoveNlsRewriteAnnotationsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveNlsRewriteAnnotations());
    }

    @DocumentExample
    @Test
    void removeFromRecipeClass() {
        rewriteRun(
          java(
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.NlsRewrite;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              public class MyRecipe extends Recipe {
                  @Override
                  public @NlsRewrite.DisplayName String getDisplayName() {
                      return "My Recipe";
                  }

                  @Override
                  public @NlsRewrite.Description String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """,
            """
              import org.openrewrite.ExecutionContext;
              import org.openrewrite.Recipe;
              import org.openrewrite.TreeVisitor;

              public class MyRecipe extends Recipe {
                  @Override
                  public String getDisplayName() {
                      return "My Recipe";
                  }

                  @Override
                  public String getDescription() {
                      return "My description.";
                  }

                  @Override
                  public TreeVisitor<?, ExecutionContext> getVisitor() {
                      return TreeVisitor.noop();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRemoveFromNonRecipeClass() {
        rewriteRun(
          java(
            """
              import org.openrewrite.NlsRewrite;
              import org.openrewrite.Recipe;

              public class DevCenter {
                  private Recipe recipe;

                  public @NlsRewrite.DisplayName String getDisplayName() {
                      return "Dev Center";
                  }

                  public @NlsRewrite.Description String getDescription() {
                      return "A dev center.";
                  }
              }
              """
          )
        );
    }
}
