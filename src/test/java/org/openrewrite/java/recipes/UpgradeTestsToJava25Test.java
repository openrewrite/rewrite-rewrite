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

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.java.Assertions.srcTestResources;
import static org.openrewrite.java.Assertions.version;

class UpgradeTestsToJava25Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.recipes.UpgradeTestsToJava25");
    }

    @DocumentExample
    @Test
    void upgradeJavaTestSources() {
        rewriteRun(
          srcTestJava(
            //language=java
            version(java(
              """
                class Answer {
                    Integer answer = new Integer(42);
                }
                """,
              """
                class Answer {
                    Integer answer = Integer.valueOf(42);
                }
                """
            ), 17)
          )
        );
    }

    @Test
    void keepBuildFixturesInTestResources() {
        rewriteRun(
          srcTestResources(
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java'
                }

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(11)
                    }
                }
                """,
              spec -> spec.path("fixture/build.gradle")
            ),
            //language=kotlin
            buildGradleKts(
              """
                plugins {
                    java
                    id("com.github.johnrengelman.shadow") version "6.1.0"
                }

                java {
                    toolchain {
                        languageVersion.set(JavaLanguageVersion.of(11))
                    }
                }
                """,
              spec -> spec.path("shadow/build.gradle.kts")
            )
          )
        );
    }
}
