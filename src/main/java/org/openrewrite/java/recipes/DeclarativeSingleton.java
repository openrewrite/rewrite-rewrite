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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class DeclarativeSingleton extends Recipe {

    String displayName = "Make declarative recipes singletons";

    @Language("markdown")
    String description = "Adds the `org.openrewrite.Singleton` precondition to declarative YAML recipes to ensure they only execute " +
            "once, even when included multiple times.";

    @Option(displayName = "Exclusions",
            description = "List of recipe names to exclude from having the Singleton precondition added.",
            example = "org.openrewrite.java.cleanup.Cleanup",
            required = false)
    @Nullable
    List<String> exclusions;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                if (!documents.getSourcePath().toString().contains("META-INF/rewrite")) {
                    return documents;
                }
                return super.visitDocuments(documents, ctx);
            }

            @Override
            public Yaml.Document visitDocument(Yaml.Document d, ExecutionContext ctx) {
                if (!(d.getBlock() instanceof Yaml.Mapping)) {
                    return d;
                }

                Yaml.Mapping mapping = (Yaml.Mapping) d.getBlock();

                String type = getScalarValue(mapping, "type");
                if (!"specs.openrewrite.org/v1beta/recipe".equals(type)) {
                    return d;
                }

                if (exclusions != null) {
                    String name = getScalarValue(mapping, "name");
                    if (name != null && exclusions.contains(name)) {
                        return d;
                    }
                }

                return (Yaml.Document) new MergeYaml(
                        "$",
                        "preconditions:\n  - org.openrewrite.Singleton",
                        false,
                        null,
                        null,
                        MergeYaml.InsertMode.Before,
                        "recipeList",
                        true
                ).getVisitor()
                        .visitNonNull(d, ctx, getCursor().getParentTreeCursor());
            }

            private @Nullable String getScalarValue(Yaml.Mapping mapping, String key) {
                for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
                    if (key.equals(entry.getKey().getValue()) && entry.getValue() instanceof Yaml.Scalar) {
                        return ((Yaml.Scalar) entry.getValue()).getValue();
                    }
                }
                return null;
            }
        };
    }
}
