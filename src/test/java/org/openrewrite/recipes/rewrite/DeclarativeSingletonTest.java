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
package org.openrewrite.recipes.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.yaml.Assertions.yaml;

class DeclarativeSingletonTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DeclarativeSingleton(null));
    }

    @DocumentExample
    @Test
    void addsSingletonPreconditionToRecipeWithoutPreconditions() {
        rewriteRun(
          yaml(
            """
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My Recipe
              description: Does something useful
              recipeList:
                - org.openrewrite.java.OrderImports
              """,
            """
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My Recipe
              description: Does something useful
              preconditions:
                - org.openrewrite.Singleton
              recipeList:
                - org.openrewrite.java.OrderImports
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/test.yml")
          )
        );
    }

    @Test
    void addsSingletonToExistingPreconditions() {
        rewriteRun(
          yaml(
            """
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My Recipe
              description: Does something useful
              preconditions:
                - org.openrewrite.java.search.FindTypes:
                    fullyQualifiedTypeName: org.openrewrite.Recipe
              recipeList:
                - org.openrewrite.java.OrderImports
              """,
            """
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My Recipe
              description: Does something useful
              preconditions:
                - org.openrewrite.Singleton
                - org.openrewrite.java.search.FindTypes:
                    fullyQualifiedTypeName: org.openrewrite.Recipe
              recipeList:
                - org.openrewrite.java.OrderImports
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/test.yml")
          )
        );
    }

    @Test
    void skipsNonRecipeYaml() {
        rewriteRun(
          yaml(
            """
              ---
              type: specs.openrewrite.org/v1beta/example
              recipeName: org.openrewrite.java.recipes.Test
              examples:
                - description: Test
                  sources:
                    - before: |
                        class Test {}
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/test.yml")
          )
        );
    }
    @Test
    void respectsWhitelist() {
        rewriteRun(
          spec -> spec.recipe(new DeclarativeSingleton(List.of("com.example.MyRecipe"))),
          yaml(
            """
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My Recipe
              description: Does something useful
              recipeList:
                - org.openrewrite.java.OrderImports
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/test.yml")
          )
        );
    }

    @Test
    void processesMultipleRecipesInOneFile() {
        rewriteRun(
          yaml(
            """
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.Recipe1
              displayName: Recipe 1
              recipeList:
                - org.openrewrite.java.OrderImports
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.Recipe2
              displayName: Recipe 2
              recipeList:
                - org.openrewrite.java.RemoveUnusedImports
              """,
            """
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.Recipe1
              displayName: Recipe 1
              preconditions:
                - org.openrewrite.Singleton
              recipeList:
                - org.openrewrite.java.OrderImports
              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.Recipe2
              displayName: Recipe 2
              preconditions:
                - org.openrewrite.Singleton
              recipeList:
                - org.openrewrite.java.RemoveUnusedImports
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/test.yml")
          )
        );
    }
}
