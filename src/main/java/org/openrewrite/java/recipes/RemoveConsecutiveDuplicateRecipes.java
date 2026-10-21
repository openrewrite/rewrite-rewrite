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

import java.util.List;

public class RemoveConsecutiveDuplicateRecipes extends Recipe {

    @Getter
    final String displayName = "Remove consecutive duplicate entries from declarative recipe lists";

    @Getter
    final String description = "Remove an entry from the `recipeList` or `preconditions` of a declarative YAML recipe " +
            "when it repeats the entry right before it, with the same recipe name and options, as running it twice in a row has no effect. " +
            "Duplicates further apart are kept, as the recipes in between can produce new matches for the repeated recipe.";

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
                    if (!("recipeList".equals(key) || "preconditions".equals(key)) ||
                            !(entry.getValue() instanceof Yaml.Sequence) ||
                            ((Yaml.Sequence) entry.getValue()).getOpeningBracketPrefix() != null) {
                        return entry;
                    }
                    List<Yaml.Sequence.Entry> recipes = ((Yaml.Sequence) entry.getValue()).getEntries();
                    return entry.withValue(((Yaml.Sequence) entry.getValue()).withEntries(ListUtils.map(recipes, (i, recipeEntry) -> {
                        if (isRepeatedByNext(recipes, i)) {
                            return null;
                        }
                        int first = i;
                        while (first > 0 && isRepeatedByNext(recipes, first - 1)) {
                            first--;
                        }
                        return recipeEntry.withPrefix(recipes.get(first).getPrefix());
                    })));
                })));
            }

            private boolean isRepeatedByNext(List<Yaml.Sequence.Entry> recipes, int i) {
                return i + 1 < recipes.size() &&
                        !recipes.get(i + 1).getPrefix().contains("#") &&
                        recipes.get(i).getBlock().print(getCursor()).trim().equals(recipes.get(i + 1).getBlock().print(getCursor()).trim());
            }
        };
    }
}
