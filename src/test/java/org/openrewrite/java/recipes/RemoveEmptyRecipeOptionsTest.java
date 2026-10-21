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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class RemoveEmptyRecipeOptionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveEmptyRecipeOptions());
    }

    @DocumentExample
    @Test
    void removeTrailingColon() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful.
              recipeList:
                - org.openrewrite.java.OrderImports:
                - org.openrewrite.java.ChangeType:
                    oldFullyQualifiedTypeName: com.example.Foo
                    newFullyQualifiedTypeName: com.example.Bar
                - org.openrewrite.java.RemoveUnusedImports:
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful.
              recipeList:
                - org.openrewrite.java.OrderImports
                - org.openrewrite.java.ChangeType:
                    oldFullyQualifiedTypeName: com.example.Foo
                    newFullyQualifiedTypeName: com.example.Bar
                - org.openrewrite.java.RemoveUnusedImports
              """
          )
        );
    }

    @Test
    void emptyFlowMappingInPreconditions() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              preconditions:
                - org.openrewrite.Singleton: {}
              recipeList:
                - org.openrewrite.java.OrderImports
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              preconditions:
                - org.openrewrite.Singleton
              recipeList:
                - org.openrewrite.java.OrderImports
              """
          )
        );
    }

    @Test
    void keepTrailingComment() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              recipeList:
                - org.openrewrite.java.OrderImports: # sort imports
                - org.openrewrite.java.RemoveUnusedImports
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              recipeList:
                - org.openrewrite.java.OrderImports # sort imports
                - org.openrewrite.java.RemoveUnusedImports
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void withOptions() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.MyRecipe
                  recipeList:
                    - org.openrewrite.java.OrderImports
                    - org.openrewrite.java.RemoveUnusedImports:
                        detectAmbiguousImports: true
                  """
              )
            );
        }

        @Test
        void notRecipeDocument() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/style
                  name: com.example.MyStyle
                  styleConfigs:
                    - org.openrewrite.java.style.ImportLayoutStyle:
                  recipeList:
                    - org.openrewrite.java.OrderImports:
                  """
              )
            );
        }
    }
}
