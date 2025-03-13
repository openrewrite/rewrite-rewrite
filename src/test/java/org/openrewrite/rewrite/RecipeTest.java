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
package org.openrewrite.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.yaml.Assertions.yaml;

class RecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(RewriteTest.toRecipe(() -> new Recipe.Matcher().asVisitor(recipe ->
          SearchResult.found(recipe.getTree(), recipe.getDisplayName() + "=" + recipe.getDescription()))));
    }

  @DocumentExample
  @Test
  void classDefinedRecipe() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jetbrains.annotations.NotNull;
              import org.openrewrite.Recipe;

              class MyRecipe extends Recipe {
                  public @NotNull String getDisplayName() {
                      return "My recipe";
                  }

                  public @NotNull String getDescription() {
                      return "My recipe description";
                  }
              }
              """,
            """
              import org.jetbrains.annotations.NotNull;
              import org.openrewrite.Recipe;

              /*~~(My recipe=My recipe description)~~>*/class MyRecipe extends Recipe {
                  public @NotNull String getDisplayName() {
                      return "My recipe";
                  }

                  public @NotNull String getDescription() {
                      return "My recipe description";
                  }
              }
              """
          )
        );
    }

    @Test
    void yamlDefinedRecipe() {
        rewriteRun(
          yaml(
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.MyRecipe
              displayName: My recipe
              description: My recipe description
              """,
            //language=yaml
            """
              ~~(My recipe=My recipe description)~~>type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.MyRecipe
              displayName: My recipe
              description: My recipe description
              """
          )
        );
    }

    @Test
    void changesDisplayName() {
        rewriteRun(
          spec -> spec.recipe(changeRecipeNameAndDescription()),
          //language=java
          java(
            """
              import org.jetbrains.annotations.NotNull;
              import org.openrewrite.Recipe;

              class MyRecipe extends Recipe {
                  public @NotNull String getDisplayName() {
                      return "My recipe";
                  }

                  public @NotNull String getDescription() {
                      return "My recipe description";
                  }
              }
              """,
            """
              import org.jetbrains.annotations.NotNull;
              import org.openrewrite.Recipe;

              class MyRecipe extends Recipe {
                  public @NotNull String getDisplayName() {
                      return "My new recipe";
                  }

                  public @NotNull String getDescription() {
                      return "My new recipe description";
                  }
              }
              """
          )
        );
    }

    @Test
    void changeYamlDefinedRecipe() {
        rewriteRun(
          spec -> spec.recipe(changeRecipeNameAndDescription()),
          yaml(
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.MyRecipe
              displayName: My recipe
              description: My recipe description
              """,
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.MyRecipe
              displayName: My new recipe
              description: My new recipe description
              """
          )
        );
    }

    private static org.openrewrite.Recipe changeRecipeNameAndDescription() {
        return RewriteTest.toRecipe(() -> new Recipe.Matcher()
          .asVisitor(recipe -> recipe
            .withDisplayName("My new recipe")
            .withDescription("My new recipe description").getTree())
        );
    }
}
