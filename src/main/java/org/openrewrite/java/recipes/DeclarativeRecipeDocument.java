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

import org.openrewrite.yaml.tree.Yaml;

final class DeclarativeRecipeDocument {

    private DeclarativeRecipeDocument() {
    }

    static boolean isRecipe(Yaml.Document document) {
        return document.getBlock() instanceof Yaml.Mapping &&
                ((Yaml.Mapping) document.getBlock()).getEntries().stream().anyMatch(entry ->
                        "type".equals(entry.getKey().getValue()) &&
                                entry.getValue() instanceof Yaml.Scalar &&
                                "specs.openrewrite.org/v1beta/recipe".equals(((Yaml.Scalar) entry.getValue()).getValue()));
    }
}
