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
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.regex.Pattern;

public class LowercaseVersionWildcards extends Recipe {

    private static final Pattern UPPERCASE_WILDCARD_VERSION = Pattern.compile("\\d+(\\.\\d+)*(\\.[xX])*\\.X(\\.[xX])*");

    @Getter
    final String displayName = "Use a lowercase `x` as version wildcard in declarative recipes";

    @Getter
    final String description = "Replace an uppercase `X` wildcard in the `version` and `newVersion` options of declarative YAML recipes, " +
            "such as `2.0.X`, with the conventional lowercase `2.0.x`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                return DeclarativeRecipeDocument.isRecipe(document) ? super.visitDocument(document, ctx) : document;
            }

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                String key = e.getKey().getValue();
                if (("version".equals(key) || "newVersion".equals(key)) && e.getValue() instanceof Yaml.Scalar) {
                    Yaml.Scalar version = (Yaml.Scalar) e.getValue();
                    if (UPPERCASE_WILDCARD_VERSION.matcher(version.getValue()).matches()) {
                        return e.withValue(version.withValue(version.getValue().replace('X', 'x')));
                    }
                }
                return e;
            }
        };
    }
}
