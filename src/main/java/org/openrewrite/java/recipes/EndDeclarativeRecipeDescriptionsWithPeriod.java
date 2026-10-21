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

import java.util.regex.Pattern;

public class EndDeclarativeRecipeDescriptionsWithPeriod extends Recipe {

    private static final Pattern ENDS_WITH_PUNCTUATION = Pattern.compile("(?<!\\(\\.)[.!?:;,][\"')\\]*_]*$");
    private static final Pattern MARKDOWN_LAST_LINE = Pattern.compile("(```|~~~|\\|).*|[-*] \\[.*\\)");

    @Getter
    final String displayName = "End declarative recipe descriptions with a period";

    @Getter
    final String description = "Append a period to the `description` of declarative YAML recipes, as is expected of recipe descriptions. " +
            "Descriptions that already end in punctuation, or end in a code fence, table or list of links, are left as is.";

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
                    if (!"description".equals(entry.getKey().getValue()) || !(entry.getValue() instanceof Yaml.Scalar)) {
                        return entry;
                    }
                    Yaml.Scalar scalar = (Yaml.Scalar) entry.getValue();
                    String value = scalar.getValue();
                    String text = value.replaceAll("\\s+$", "");
                    boolean blockScalar = scalar.getStyle() == Yaml.Scalar.Style.FOLDED || scalar.getStyle() == Yaml.Scalar.Style.LITERAL;
                    int bodyStart = blockScalar ? text.indexOf('\n') + 1 : 0;
                    String body = text.substring(bodyStart);
                    if (blockScalar && bodyStart == 0 || body.isEmpty() ||
                            ENDS_WITH_PUNCTUATION.matcher(body).find() ||
                            MARKDOWN_LAST_LINE.matcher(body.substring(body.lastIndexOf('\n') + 1).trim()).matches()) {
                        return entry;
                    }
                    return entry.withValue(scalar.withValue(text + "." + value.substring(text.length())));
                })));
            }
        };
    }
}
