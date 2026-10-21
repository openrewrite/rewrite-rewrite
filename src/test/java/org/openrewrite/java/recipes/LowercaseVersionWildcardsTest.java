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

class LowercaseVersionWildcardsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LowercaseVersionWildcards());
    }

    @DocumentExample
    @Test
    void lowercaseWildcards() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful.
              recipeList:
                - org.openrewrite.java.dependencies.ChangeDependency:
                    oldGroupId: org.acegisecurity
                    oldArtifactId: acegi-security
                    newGroupId: org.springframework.security
                    newArtifactId: spring-security-core
                    newVersion: 2.0.X
                - org.openrewrite.java.dependencies.AddDependency:
                    groupId: org.springframework.security
                    artifactId: spring-security-web
                    version: 3.X
                    onlyIfUsing: org.springframework.security.web..*
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful.
              recipeList:
                - org.openrewrite.java.dependencies.ChangeDependency:
                    oldGroupId: org.acegisecurity
                    oldArtifactId: acegi-security
                    newGroupId: org.springframework.security
                    newArtifactId: spring-security-core
                    newVersion: 2.0.x
                - org.openrewrite.java.dependencies.AddDependency:
                    groupId: org.springframework.security
                    artifactId: spring-security-web
                    version: 3.x
                    onlyIfUsing: org.springframework.security.web..*
              """
          )
        );
    }

    @Test
    void quotedVersionInPreconditions() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              preconditions:
                - org.openrewrite.java.dependencies.DependencyInsight:
                    groupIdPattern: org.springframework
                    artifactIdPattern: spring-core
                    version: "5.X.X"
              recipeList:
                - org.openrewrite.java.OrderImports
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              preconditions:
                - org.openrewrite.java.dependencies.DependencyInsight:
                    groupIdPattern: org.springframework
                    artifactIdPattern: spring-core
                    version: "5.x.x"
              recipeList:
                - org.openrewrite.java.OrderImports
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void alreadyLowercase() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.MyRecipe
                  recipeList:
                    - org.openrewrite.java.dependencies.UpgradeDependencyVersion:
                        groupId: org.springframework
                        artifactId: "*"
                        newVersion: 6.2.x
                  """
              )
            );
        }

        @Test
        void notVersionOption() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.MyRecipe
                  recipeList:
                    - org.openrewrite.text.FindAndReplace:
                        find: 2.0.X
                        replace: 2.0.Y
                  """
              )
            );
        }

        @Test
        void notWildcardVersion() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.MyRecipe
                  recipeList:
                    - org.openrewrite.java.dependencies.UpgradeDependencyVersion:
                        groupId: com.example
                        artifactId: example
                        newVersion: 1.0.0-X
                  """
              )
            );
        }

        @Test
        void notRecipeDocument() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/example
                  recipeName: com.example.MyRecipe
                  examples:
                    - description: Some example
                      parameters:
                        - version: 2.0.X
                      sources:
                        - before: a
                          after: b
                  """
              )
            );
        }
    }
}
