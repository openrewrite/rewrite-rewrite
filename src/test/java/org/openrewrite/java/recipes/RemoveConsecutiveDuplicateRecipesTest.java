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

class RemoveConsecutiveDuplicateRecipesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveConsecutiveDuplicateRecipes());
    }

    @DocumentExample
    @Test
    void removeRepeatedEntryWithOptions() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful.
              recipeList:
                - org.openrewrite.maven.UpgradePluginVersion:
                    groupId: org.springframework.boot
                    artifactId: spring-boot-maven-plugin
                    newVersion: 4.0.x
                - org.openrewrite.maven.UpgradePluginVersion:
                    groupId: org.springframework.boot
                    artifactId: spring-boot-maven-plugin
                    newVersion: 4.0.x
                - org.openrewrite.java.OrderImports
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              displayName: My recipe
              description: Does something useful.
              recipeList:
                - org.openrewrite.maven.UpgradePluginVersion:
                    groupId: org.springframework.boot
                    artifactId: spring-boot-maven-plugin
                    newVersion: 4.0.x
                - org.openrewrite.java.OrderImports
              """
          )
        );
    }

    @Test
    void removeRepeatedRecipeNames() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              preconditions:
                - org.openrewrite.Singleton
                - org.openrewrite.Singleton
              recipeList:
                - org.openrewrite.java.OrderImports
                - org.openrewrite.java.OrderImports
                - org.openrewrite.java.OrderImports
                - org.openrewrite.java.RemoveUnusedImports
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              preconditions:
                - org.openrewrite.Singleton
              recipeList:
                - org.openrewrite.java.OrderImports
                - org.openrewrite.java.RemoveUnusedImports
              """
          )
        );
    }

    @Test
    void removeRepeatedEntryEndingInBlockScalar() {
        rewriteRun(
          yaml(
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              recipeList:
                - org.openrewrite.text.CreateTextFile:
                    relativeFileName: Jenkinsfile
                    fileContents: |
                      pipeline {
                      }
                - org.openrewrite.text.CreateTextFile:
                    relativeFileName: Jenkinsfile
                    fileContents: |
                      pipeline {
                      }

              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.Other
              """,
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.MyRecipe
              recipeList:
                - org.openrewrite.text.CreateTextFile:
                    relativeFileName: Jenkinsfile
                    fileContents: |
                      pipeline {
                      }

              ---
              type: specs.openrewrite.org/v1beta/recipe
              name: com.example.Other
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void differentOptions() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.MyRecipe
                  recipeList:
                    - org.openrewrite.java.ChangePackage:
                        oldPackageName: com.example.foo
                        newPackageName: com.example.bar
                    - org.openrewrite.java.ChangePackage:
                        oldPackageName: com.example.foo
                        newPackageName: com.example.baz
                  """
              )
            );
        }

        @Test
        void repeatedAfterOtherRecipe() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.MyRecipe
                  recipeList:
                    - org.openrewrite.java.OrderImports
                    - org.openrewrite.java.ChangeType:
                        oldFullyQualifiedTypeName: com.example.Foo
                        newFullyQualifiedTypeName: com.example.Bar
                    - org.openrewrite.java.OrderImports
                  """
              )
            );
        }

        @Test
        void separatedByComment() {
            rewriteRun(
              yaml(
                """
                  type: specs.openrewrite.org/v1beta/recipe
                  name: com.example.MyRecipe
                  recipeList:
                    - org.openrewrite.java.OrderImports
                    # Once more, with feeling
                    - org.openrewrite.java.OrderImports
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
                  recipeList:
                    - org.openrewrite.java.OrderImports
                    - org.openrewrite.java.OrderImports
                  """
              )
            );
        }
    }
}
