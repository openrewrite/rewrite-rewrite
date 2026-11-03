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
package org.openrewrite.java.recipes.migrate;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.InlineMethodCalls;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class InlineMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          // Only entries whose replacement imports types carry `classpathFromResources`, so for the others
          // `InlineMethodCalls` builds its template with a bare parser and can not attribute the replacement type.
          .typeValidationOptions(TypeValidation.builder().constructorInvocations(false).build())
          .recipeFromResources("org.openrewrite.recipes.rewrite.InlineMethods");
    }

    @DocumentExample
    @Test
    void staticAnalysisRemoveUnusedLocalVariables() {
        rewriteRun(
          java(
            """
              import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables;

              class Test {
                  void test() {
                      RemoveUnusedLocalVariables recipe = new RemoveUnusedLocalVariables(new String[]{"foo"}, true);
                  }
              }
              """,
            """
              import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables;

              class Test {
                  void test() {
                      RemoveUnusedLocalVariables recipe = new RemoveUnusedLocalVariables(new String[]{"foo"}, null, true);
                  }
              }
              """
          )
        );
    }

    @Test
    void addsImportsTheReplacementNeeds() {
        rewriteRun(
          java(
            """
              import org.openrewrite.maven.MavenSettings;
              import org.openrewrite.maven.cache.MavenArtifactCache;
              import org.openrewrite.maven.utilities.MavenArtifactDownloader;

              class Test {
                  MavenArtifactDownloader downloader(MavenArtifactCache cache, MavenSettings settings) {
                      return new MavenArtifactDownloader(cache, settings, Throwable::printStackTrace);
                  }
              }
              """,
            """
              import org.openrewrite.InMemoryExecutionContext;
              import org.openrewrite.ipc.http.HttpUrlConnectionSender;
              import org.openrewrite.maven.MavenSettings;
              import org.openrewrite.maven.cache.MavenArtifactCache;
              import org.openrewrite.maven.utilities.MavenArtifactDownloader;

              class Test {
                  MavenArtifactDownloader downloader(MavenArtifactCache cache, MavenSettings settings) {
                      return new MavenArtifactDownloader(cache, settings, new HttpUrlConnectionSender(), Throwable::printStackTrace, new InMemoryExecutionContext());
                  }
              }
              """
          )
        );
    }

    @Test
    void everyMethodPatternIsValid() {
        Recipe inlineMethods = Environment.builder().scanRuntimeClasspath().build()
          .activateRecipes("org.openrewrite.recipes.rewrite.InlineMethods");
        assertThat(flatten(inlineMethods).filter(InlineMethodCalls.class::isInstance).map(InlineMethodCalls.class::cast))
          .isNotEmpty()
          .allSatisfy(step -> assertThat(MethodMatcher.validate(step.getMethodPattern()).isValid())
            .as(step.getMethodPattern())
            .isTrue());
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        return Stream.concat(Stream.of(recipe), recipe.getRecipeList().stream().flatMap(InlineMethodsTest::flatten));
    }
}
