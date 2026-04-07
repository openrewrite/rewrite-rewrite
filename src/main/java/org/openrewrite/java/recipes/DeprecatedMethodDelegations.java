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
package org.openrewrite.java.recipes;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class DeprecatedMethodDelegations extends DataTable<DeprecatedMethodDelegations.Row> {

    public DeprecatedMethodDelegations(Recipe recipe) {
        super(recipe,
                "Deprecated method delegations",
                "Deprecated methods that delegate to another method in the same class, " +
                        "suitable for inlining via `InlineMethodCalls`.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Method pattern",
                description = "The method pattern of the deprecated method.")
        String methodPattern;

        @Column(displayName = "Replacement",
                description = "The replacement expression to inline.")
        String replacement;

        @Column(displayName = "Recipe YAML",
                description = "A YAML snippet that can be copied into a recipe list.")
        String recipeYaml;
    }
}
