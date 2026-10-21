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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class DeclarativeRecipeBestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.recipes.DeclarativeRecipeBestPractices");
    }

    @DocumentExample
    @Test
    void recipeResource() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful
              recipeList:
                - org.openrewrite.java.dependencies.UpgradeDependencyVersion:
                    groupId: org.springframework
                    artifactId: "*"
                    newVersion: 6.2.X
                - org.openrewrite.java.OrderImports:
                - org.openrewrite.java.OrderImports
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful.
              recipeList:
                - org.openrewrite.java.dependencies.UpgradeDependencyVersion:
                    groupId: org.springframework
                    artifactId: "*"
                    newVersion: 6.2.x
                - org.openrewrite.java.OrderImports
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/my-recipe.yml")
          )
        );
    }

    @Test
    void noChangeOutsideRecipeResources() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful
              recipeList:
                - org.openrewrite.java.OrderImports:
              """,
            spec -> spec.path("src/test/resources/my-recipe.yml")
          )
        );
    }
}
