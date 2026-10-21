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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

public class RemoveEmptyRecipeOptions extends Recipe {

    @Getter
    final String displayName = "Remove the trailing colon from declarative recipe entries without options";

    @Getter
    final String description = "Remove the trailing colon, or `: {}`, from entries in the `recipeList` or `preconditions` " +
            "of a declarative YAML recipe that configure no options, to list just the recipe name.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                if (!DeclarativeRecipeDocument.isRecipe(document)) {
                    return document;
                }
                Yaml.Mapping recipe = (Yaml.Mapping) document.getBlock();
                return document.withBlock(recipe.withEntries(ListUtils.map(recipe.getEntries(), entry -> {
                    String key = entry.getKey().getValue();
                    if (!("recipeList".equals(key) || "preconditions".equals(key)) || !(entry.getValue() instanceof Yaml.Sequence)) {
                        return entry;
                    }
                    Yaml.Sequence recipes = (Yaml.Sequence) entry.getValue();
                    return entry.withValue(recipes.withEntries(ListUtils.map(recipes.getEntries(), recipeEntry -> {
                        if (!(recipeEntry.getBlock() instanceof Yaml.Mapping) ||
                                ((Yaml.Mapping) recipeEntry.getBlock()).getEntries().size() != 1) {
                            return recipeEntry;
                        }
                        Yaml.Mapping.Entry recipeWithOptions = ((Yaml.Mapping) recipeEntry.getBlock()).getEntries().get(0);
                        Yaml.Block options = recipeWithOptions.getValue();
                        boolean noOptions = options instanceof Yaml.Scalar &&
                                ((Yaml.Scalar) options).getStyle() == Yaml.Scalar.Style.PLAIN &&
                                ((Yaml.Scalar) options).getValue().isEmpty() ||
                                options instanceof Yaml.Mapping && ((Yaml.Mapping) options).getEntries().isEmpty();
                        if (noOptions && recipeWithOptions.getKey() instanceof Yaml.Scalar) {
                            return recipeEntry.withBlock(((Yaml.Scalar) recipeWithOptions.getKey()).withPrefix(recipeWithOptions.getPrefix()));
                        }
                        return recipeEntry;
                    })));
                })));
            }
        };
    }
}
