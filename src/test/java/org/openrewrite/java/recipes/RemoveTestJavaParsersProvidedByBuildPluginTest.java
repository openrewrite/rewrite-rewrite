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
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;

class RemoveTestJavaParsersProvidedByBuildPluginTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.recipes.RemoveTestJavaParsersProvidedByBuildPlugin")
          .beforeRecipe(withToolingApi());
    }

    // Stands in for the published plugin, whose own dependencies the tooling API build can not resolve
    private static SourceSpecs recipeLibraryPluginStub() {
        return SourceSpecs.dir("buildSrc",
          buildGradle(
            """
              plugins {
                  id 'groovy-gradle-plugin'
              }
              """
          ),
          buildGradle(
            """
              plugins {
                  id 'java-library'
              }

              repositories {
                  mavenCentral()
              }
              """,
            spec -> spec.path("src/main/groovy/org.openrewrite.build.recipe-library.gradle")
          )
        );
    }

    @DocumentExample
    @Test
    void removeTestRuntimeParsers() {
        rewriteRun(
          recipeLibraryPluginStub(),
          buildGradleKts(
            """
              plugins {
                  id("org.openrewrite.build.recipe-library")
              }

              dependencies {
                  implementation(platform("org.openrewrite:rewrite-bom:8.80.0"))
                  implementation("org.openrewrite:rewrite-java")

                  testImplementation("org.openrewrite:rewrite-test")
                  testRuntimeOnly("org.openrewrite:rewrite-java-17")
                  testRuntimeOnly("org.openrewrite:rewrite-java-21")
                  testRuntimeOnly("org.openrewrite:rewrite-java-25")
              }
              """,
            """
              plugins {
                  id("org.openrewrite.build.recipe-library")
              }

              dependencies {
                  implementation(platform("org.openrewrite:rewrite-bom:8.80.0"))
                  implementation("org.openrewrite:rewrite-java")

                  testImplementation("org.openrewrite:rewrite-test")
              }
              """
          )
        );
    }

    @Test
    void keepParsersOutsideTestRuntimeOnly() {
        rewriteRun(
          recipeLibraryPluginStub(),
          buildGradleKts(
            """
              plugins {
                  id("org.openrewrite.build.recipe-library")
              }

              dependencies {
                  implementation(platform("org.openrewrite:rewrite-bom:8.80.0"))
                  implementation("org.openrewrite:rewrite-java")
                  runtimeOnly("org.openrewrite:rewrite-java-17")
                  runtimeOnly("org.openrewrite:rewrite-java-21")

                  testImplementation("org.openrewrite:rewrite-java-21")
              }
              """
          )
        );
    }

    @Test
    void keepParsersWithoutBuildPlugin() {
        rewriteRun(
          buildGradleKts(
            """
              plugins {
                  `java-library`
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation(platform("org.openrewrite:rewrite-bom:8.80.0"))
                  implementation("org.openrewrite:rewrite-java")
                  testRuntimeOnly("org.openrewrite:rewrite-java-21")
              }
              """
          )
        );
    }
}
