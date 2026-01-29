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
package org.openrewrite.recipes.rewrite;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class DeclarativeSingleton extends Recipe {

    @Option(displayName = "Whitelist",
            description = "List of recipe names to exclude from having the Singleton precondition added.",
            example = "org.openrewrite.java.cleanup.Cleanup",
            required = false)
    @Nullable
    List<String> whitelist;

    @Override
    public String getDisplayName() {
        return "Make declarative recipes singletons";
    }

    @Override
    public String getDescription() {
        return "Adds the `org.openrewrite.Singleton` precondition to declarative YAML recipes to ensure they only execute " +
                "once, even when included multiple times.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                // Only process YAML files in META-INF/rewrite directory
                if (!documents.getSourcePath().toString().contains("META-INF/rewrite")) {
                    return documents;
                }
                return super.visitDocuments(documents, ctx);
            }

            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                Yaml.Mapping m = super.visitMapping(mapping, ctx);

                // Check if this is a recipe declaration
                String type = getScalarValue(m, "type");
                if (!"specs.openrewrite.org/v1beta/recipe".equals(type)) {
                    return m;
                }

                // Check if recipe name is in whitelist
                String recipeName = getScalarValue(m, "name");
                if (recipeName != null && whitelist != null && whitelist.contains(recipeName)) {
                    return m;
                }

                // Check if preconditions already contains Singleton
                Yaml.Mapping.Entry preconditionsEntry = findEntry(m, "preconditions");
                if (preconditionsEntry != null) {
                    if (preconditionsEntry.getValue() instanceof Yaml.Sequence) {
                        Yaml.Sequence seq = (Yaml.Sequence) preconditionsEntry.getValue();
                        if (hasSingleton(seq)) {
                            return m;
                        }
                        // Add Singleton to existing preconditions
                        return m.withEntries(ListUtils.map(m.getEntries(), entry -> {
                            if (entry == preconditionsEntry) {
                                return entry.withValue(addSingletonToSequence(seq));
                            }
                            return entry;
                        }));
                    }
                } else {
                    // Add new preconditions entry with Singleton
                    return addPreconditionsWithSingleton(m);
                }

                return m;
            }

            private @Nullable String getScalarValue(Yaml.Mapping mapping, String key) {
                Yaml.Mapping.Entry entry = findEntry(mapping, key);
                if (entry != null && entry.getValue() instanceof Yaml.Scalar) {
                    return ((Yaml.Scalar) entry.getValue()).getValue();
                }
                return null;
            }

            private Yaml.Mapping.@Nullable Entry findEntry(Yaml.Mapping mapping, String key) {
                for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
                    if (key.equals(entry.getKey().getValue())) {
                        return entry;
                    }
                }
                return null;
            }

            private boolean hasSingleton(Yaml.Sequence sequence) {
                for (Yaml.Sequence.Entry entry : sequence.getEntries()) {
                    if (entry.getBlock() instanceof Yaml.Scalar) {
                        Yaml.Scalar scalar = (Yaml.Scalar) entry.getBlock();
                        if ("org.openrewrite.Singleton".equals(scalar.getValue())) {
                            return true;
                        }
                    } else if (entry.getBlock() instanceof Yaml.Mapping) {
                        Yaml.Mapping mapping = (Yaml.Mapping) entry.getBlock();
                        for (Yaml.Mapping.Entry mappingEntry : mapping.getEntries()) {
                            if ("org.openrewrite.Singleton".equals(mappingEntry.getKey().getValue())) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            private Yaml.Sequence addSingletonToSequence(Yaml.Sequence sequence) {
                // Parse a new entry for Singleton
                String yaml = "- org.openrewrite.Singleton\n";
                Yaml.Documents parsed = YamlParser.builder().build()
                        .parse(yaml)
                        .findFirst()
                        .map(Yaml.Documents.class::cast)
                        .orElseThrow(() -> new IllegalStateException("Failed to parse Singleton entry"));
                Yaml.Sequence parsedSeq = (Yaml.Sequence) parsed.getDocuments().get(0).getBlock();
                Yaml.Sequence.Entry newEntry = parsedSeq.getEntries().get(0);

                // Copy whitespace from existing entries if available
                if (!sequence.getEntries().isEmpty()) {
                    Yaml.Sequence.Entry existingEntry = sequence.getEntries().get(0);
                    newEntry = newEntry.withPrefix(existingEntry.getPrefix());
                }

                // Add Singleton as first entry
                return sequence.withEntries(ListUtils.concat(newEntry, sequence.getEntries()));
            }

            private Yaml.Mapping addPreconditionsWithSingleton(Yaml.Mapping mapping) {
                // Find the position after "description" or "displayName" to insert preconditions
                int insertPosition = -1;
                for (int i = 0; i < mapping.getEntries().size(); i++) {
                    Yaml.Mapping.Entry entry = mapping.getEntries().get(i);
                    String key = entry.getKey().getValue();
                    if ("description".equals(key) || "displayName".equals(key)) {
                        insertPosition = i + 1;
                    }
                    if ("recipeList".equals(key) && insertPosition == -1) {
                        insertPosition = i;
                        break;
                    }
                }

                if (insertPosition == -1) {
                    insertPosition = mapping.getEntries().size();
                }

                // Parse a new preconditions entry
                String yaml = "preconditions:\n  - org.openrewrite.Singleton\n";
                Yaml.Documents parsed = YamlParser.builder().build()
                        .parse(yaml)
                        .findFirst()
                        .map(Yaml.Documents.class::cast)
                        .orElseThrow(() -> new IllegalStateException("Failed to parse preconditions entry"));
                Yaml.Mapping parsedMapping = (Yaml.Mapping) parsed.getDocuments().get(0).getBlock();
                Yaml.Mapping.Entry newEntry = parsedMapping.getEntries().get(0);

                // Copy whitespace from existing entries if available
                if (!mapping.getEntries().isEmpty()) {
                    Yaml.Mapping.Entry existingEntry = mapping.getEntries().get(0);
                    newEntry = newEntry.withPrefix(existingEntry.getPrefix());
                }

                // Insert the preconditions entry and ensure the next entry has proper whitespace
                List<Yaml.Mapping.Entry> entries = mapping.getEntries();
                List<Yaml.Mapping.Entry> newEntries = ListUtils.insert(entries, newEntry, insertPosition);

                // Ensure the entry after preconditions has a newline prefix
                if (insertPosition < newEntries.size() - 1) {
                    Yaml.Mapping.Entry nextEntry = newEntries.get(insertPosition + 1);
                    if (!nextEntry.getPrefix().contains("\n")) {
                        final int nextIndex = insertPosition + 1;
                        newEntries = ListUtils.map(newEntries, (i, entry) -> {
                            if (i == nextIndex) {
                                return entry.withPrefix("\n" + entry.getPrefix());
                            }
                            return entry;
                        });
                    }
                }

                return mapping.withEntries(newEntries);
            }
        };
    }
}
