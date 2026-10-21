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

class EndDeclarativeRecipeDescriptionsWithPeriodTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EndDeclarativeRecipeDescriptionsWithPeriod());
    }

    @DocumentExample
    @Test
    void plainDescription() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful with `Foo`
              recipeList:
                - org.openrewrite.java.OrderImports
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful with `Foo`.
              recipeList:
                - org.openrewrite.java.OrderImports
              """
          )
        );
    }

    @Test
    void foldedDescription() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: >-
                Does something useful,
                  over multiple lines
              # Trailing comment
              recipeList:
                - org.openrewrite.java.OrderImports
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: >-
                Does something useful,
                  over multiple lines.
              # Trailing comment
              recipeList:
                - org.openrewrite.java.OrderImports
              """
          )
        );
    }

    @Test
    void literalDescription() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: |
                Does something useful:
                - first
                - second

              recipeList:
                - org.openrewrite.java.OrderImports
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: |
                Does something useful:
                - first
                - second.

              recipeList:
                - org.openrewrite.java.OrderImports
              """
          )
        );
    }

    @Test
    void quotedDescription() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: "Does something useful: really"
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: "Does something useful: really."
              """
          )
        );
    }

    @Test
    void endsWithCodeSpan() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Replace `Foo.bar(..)` with
                `Foo.baz(..)`
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Replace `Foo.bar(..)` with
                `Foo.baz(..)`.
              """
          )
        );
    }

    @Test
    void onlyRecipeDocuments() {
        rewriteRun(
          yaml(
            """
              ---
              type: specs.openrewrite.org/v1beta/category
              name: Example
              packageName: com.example
              description: Recipes for examples
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful
              """,
            """
              ---
              type: specs.openrewrite.org/v1beta/category
              name: Example
              packageName: com.example
              description: Recipes for examples
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful.
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void endsWithPunctuation() {
            rewriteRun(
              yaml(
                """
                  ---
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.Period
                  description: Does something useful.
                  ---
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.Parenthesized
                  description: Does something useful (or not.)
                  ---
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.Question
                  description: Does something useful?
                  ---
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.Colon
                  description: 'Does the following:'
                  """
              )
            );
        }

        @Test
        void endsWithCodeFence() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.MyRecipe
                  description: |
                    Does something like:
                    ```java
                    foo();
                    ```
                  """
              )
            );
        }

        @Test
        void endsWithListOfLinks() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.MyRecipe
                  description: >-
                    Does something as described in:
                    - [Foo](https://example.com/foo)
                  """
              )
            );
        }

        @Test
        void emptyDescription() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.MyRecipe
                  description: >-
                  recipeList:
                    - org.openrewrite.java.OrderImports
                  """
              )
            );
        }

        @Test
        void examplesDocument() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/example
                  recipeName: com.example.MyRecipe
                  examples:
                    - description: Some example
                      sources:
                        - before: a
                          after: b
                  """
              )
            );
        }

        @Test
        void nestedDescription() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.MyRecipe
                  description: Does something useful.
                  recipeList:
                    - org.openrewrite.text.FindAndReplace:
                        find: description
                        description: Not a recipe description
                  """
              )
            );
        }
    }
}
