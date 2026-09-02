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
package org.openrewrite.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.config.RecipeDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

class AddBuildSettingsPluginTest {

    private static final String RECIPE = "org.openrewrite.recipes.rewrite.AddBuildSettingsPlugin";

    /**
     * The recipe it composes lives in rewrite-gradle, which is only a runtime dependency here, so a
     * missing declaration would not surface until someone ran this against a repository.
     */
    @Test
    void loadsAndValidates() {
        Recipe recipe = Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE);

        assertThat(recipe.validateAll().isValid()).isTrue();
        assertThat(recipe.getRecipeList()).isNotEmpty();
    }

    @Test
    void addsTheSettingsPluginByItsPublishedId() {
        RecipeDescriptor descriptor = Environment.builder().scanRuntimeClasspath().build()
                .listRecipeDescriptors().stream()
                .filter(it -> RECIPE.equals(it.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(RECIPE + " is not on the classpath"));

        assertThat(descriptor.getRecipeList())
                .singleElement()
                .satisfies(composed -> {
                    assertThat(composed.getName()).isEqualTo("org.openrewrite.gradle.plugins.AddSettingsPlugin");
                    assertThat(composed.getOptions())
                            .filteredOn(option -> "pluginId".equals(option.getName()))
                            .singleElement()
                            .satisfies(option ->
                                    assertThat(option.getValue()).isEqualTo("org.openrewrite.build.settings"));
                });
    }
}
