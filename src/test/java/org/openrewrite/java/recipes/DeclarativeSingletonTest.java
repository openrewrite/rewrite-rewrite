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
                - org.openrewrite.java.search.FindTypes:
                    fullyQualifiedTypeName: org.openrewrite.Recipe
                - org.openrewrite.Singleton
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

    @Test
    void handlesBlockScalarDescriptionCorrectly() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.google.guava.InlineGuavaMethods
              displayName: Inline `guava` methods annotated with `@InlineMe`
              description: >-
                Automatically generated recipes to inline method calls based on `@InlineMe` annotations
                discovered in the type table.
              recipeList:
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: com.google.common.base.Preconditions#checkNotNull(T)
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.google.guava.InlineGuavaMethods
              displayName: Inline `guava` methods annotated with `@InlineMe`
              description: >-
                Automatically generated recipes to inline method calls based on `@InlineMe` annotations
                discovered in the type table.
              preconditions:
                - org.openrewrite.Singleton
              recipeList:
                - org.openrewrite.java.InlineMethodCalls:
                    methodPattern: com.google.common.base.Preconditions#checkNotNull(T)
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/test.yml")
          )
        );
    }

    @Test
    void handlesMultiLineBlockScalarDescription() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.java.migrate.javax.AddJaxbDependenciesWithRuntime
              displayName: Add explicit JAXB API dependencies and runtime
              description: >-
                This recipe will add explicit dependencies for Jakarta EE 8 when a Java 8 application is using JAXB. Any existing
                dependencies will be upgraded to the latest version of Jakarta EE 8. The artifacts are moved to Jakarta EE 8 version 2.x
                which allows for the continued use of the `javax.xml.bind` namespace. Running a full javax to Jakarta migration
                using `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta` will update to versions greater than 3.x which
                necessitates the package change as well.
              tags:
                - javax
                - java11
              recipeList:
                - org.openrewrite.java.dependencies.AddDependency:
                    groupId: jakarta.xml.bind
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.java.migrate.javax.AddJaxbDependenciesWithRuntime
              displayName: Add explicit JAXB API dependencies and runtime
              description: >-
                This recipe will add explicit dependencies for Jakarta EE 8 when a Java 8 application is using JAXB. Any existing
                dependencies will be upgraded to the latest version of Jakarta EE 8. The artifacts are moved to Jakarta EE 8 version 2.x
                which allows for the continued use of the `javax.xml.bind` namespace. Running a full javax to Jakarta migration
                using `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta` will update to versions greater than 3.x which
                necessitates the package change as well.
              tags:
                - javax
                - java11
              preconditions:
                - org.openrewrite.Singleton
              recipeList:
                - org.openrewrite.java.dependencies.AddDependency:
                    groupId: jakarta.xml.bind
              """,
            spec -> spec.path("src/main/resources/META-INF/rewrite/test.yml")
          )
        );
    }
}
