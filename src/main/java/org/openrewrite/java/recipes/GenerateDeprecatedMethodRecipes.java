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

import lombok.Getter;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class GenerateDeprecatedMethodRecipes extends ScanningRecipe<GenerateDeprecatedMethodRecipes.Accumulator> {

    private static final AnnotationMatcher DEPRECATED_MATCHER = new AnnotationMatcher("@java.lang.Deprecated");
    private static final AnnotationMatcher TO_BE_REMOVED_MATCHER = new AnnotationMatcher("@org.openrewrite.internal.ToBeRemoved");
    private static final Path OUTPUT_RELATIVE = Paths.get("src/main/resources/META-INF/rewrite/inline-deprecated-methods.yml");

    @Getter
    final String displayName = "Generate `InlineMethodCalls` recipes for deprecated delegating methods";
    @Getter
    final String description = "Finds `@Deprecated` method declarations whose body is a single delegation call " +
            "to another method in the same class, and generates a declarative YAML recipe file " +
            "containing `InlineMethodCalls` entries for each.";

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (tree instanceof SourceFile) {
                    SourceFile sourceFile = (SourceFile) tree;
                    if (sourceFile.getSourcePath().endsWith(OUTPUT_RELATIVE)) {
                        acc.existingOutputFiles.add(sourceFile.getSourcePath());
                    }
                }
                if (tree instanceof JavaSourceFile) {
                    SourceFile sourceFile = (SourceFile) tree;
                    JavaProject javaProject = sourceFile.getMarkers()
                            .findFirst(JavaProject.class).orElse(null);
                    Path projectBase = projectBasePath(sourceFile.getSourcePath());

                    new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                            if (md.getMethodType() == null || md.getBody() == null) {
                                return md;
                            }

                            // Check for @Deprecated or @ToBeRemoved annotation
                            if (md.getLeadingAnnotations().stream().noneMatch(
                                    ann -> DEPRECATED_MATCHER.matches(ann) || TO_BE_REMOVED_MATCHER.matches(ann))) {
                                return md;
                            }

                            // Check body has exactly one statement
                            List<Statement> statements = md.getBody().getStatements();
                            if (statements.size() != 1 || !(statements.get(0) instanceof J.MethodInvocation)) {
                                return md;
                            }

                            J.MethodInvocation invocation = (J.MethodInvocation) statements.get(0);
                            JavaType.Method invokedMethod = invocation.getMethodType();
                            if (invokedMethod == null) {
                                return md;
                            }

                            // Check the invoked method is in the same declaring type
                            JavaType.FullyQualified declaringType = md.getMethodType().getDeclaringType();
                            JavaType.FullyQualified invokedDeclaringType = invokedMethod.getDeclaringType();
                            if (!declaringType.getFullyQualifiedName().equals(invokedDeclaringType.getFullyQualifiedName())) {
                                return md;
                            }

                            String methodPattern = buildMethodPattern(md.getMethodType());
                            String replacement = buildReplacement(invocation, getCursor());

                            acc.candidatesByProject
                                    .computeIfAbsent(javaProject, k -> new ArrayList<>())
                                    .add(new MethodInlineCandidate(methodPattern, replacement));
                            acc.projectBasePaths.putIfAbsent(javaProject, projectBase);
                            return md;
                        }
                    }.visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        if (acc.candidatesByProject.isEmpty()) {
            return emptyList();
        }

        List<SourceFile> generated = new ArrayList<>();
        for (Map.Entry<JavaProject, List<MethodInlineCandidate>> entry : acc.candidatesByProject.entrySet()) {
            Path projectBase = acc.projectBasePaths.getOrDefault(entry.getKey(), Paths.get(""));
            Path outputPath = projectBase.resolve(OUTPUT_RELATIVE);

            if (acc.existingOutputFiles.contains(outputPath)) {
                continue;
            }

            List<MethodInlineCandidate> candidates = entry.getValue();
            String recipeName = deriveRecipeName(entry.getKey());
            StringBuilder yaml = new StringBuilder();
            yaml.append("type: specs.openrewrite.org/v1beta/recipe\n");
            yaml.append("name: ").append(recipeName).append("\n");
            yaml.append("displayName: Inline deprecated delegating methods\n");
            yaml.append("description: Automatically generated recipes to inline deprecated method calls that delegate to other methods in the same class.\n");
            yaml.append("recipeList:\n");

            for (MethodInlineCandidate candidate : candidates) {
                yaml.append("  - org.openrewrite.java.InlineMethodCalls:\n");
                yaml.append("      methodPattern: '").append(candidate.getMethodPattern()).append("'\n");
                yaml.append("      replacement: '").append(candidate.getReplacement()).append("'\n");
            }

            YamlParser.builder().build()
                    .parse(yaml.toString())
                    .map(sf -> (SourceFile) sf.withSourcePath(outputPath))
                    .forEach(generated::add);
        }
        return generated;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.candidatesByProject.isEmpty()) {
            return TreeVisitor.noop();
        }
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (!(tree instanceof Yaml.Documents)) {
                    return tree;
                }
                Yaml.Documents docs = (Yaml.Documents) tree;
                if (!docs.getSourcePath().endsWith(OUTPUT_RELATIVE)) {
                    return tree;
                }

                // Only merge into pre-existing files; newly generated ones already have entries
                if (!acc.existingOutputFiles.contains(docs.getSourcePath())) {
                    return tree;
                }

                // Find which project this file belongs to
                List<MethodInlineCandidate> candidates = null;
                for (Map.Entry<JavaProject, Path> entry : acc.projectBasePaths.entrySet()) {
                    Path outputPath = entry.getValue().resolve(OUTPUT_RELATIVE);
                    if (docs.getSourcePath().equals(outputPath)) {
                        candidates = acc.candidatesByProject.get(entry.getKey());
                        break;
                    }
                }
                if (candidates == null || candidates.isEmpty()) {
                    return tree;
                }

                // Check if all candidates already exist with matching replacements
                Map<String, String> existingEntries = extractExistingEntries(docs);
                boolean allMatch = candidates.stream().allMatch(c ->
                        c.getReplacement().equals(existingEntries.get(c.getMethodPattern())));
                if (allMatch) {
                    return tree;
                }

                // Remove existing entries with matching methodPatterns, then merge new ones
                Set<String> newPatterns = new HashSet<>();
                for (MethodInlineCandidate c : candidates) {
                    newPatterns.add(c.getMethodPattern());
                }
                Tree pruned = new RemoveMatchingEntries(newPatterns).visitNonNull(tree, ctx, getCursor().getParentOrThrow());

                String yamlSnippet = buildRecipeListSnippet(candidates);
                MergeYaml mergeYaml = new MergeYaml(
                        "$.recipeList", yamlSnippet, false,
                        "methodPattern", null, null, null, null);
                return mergeYaml.getVisitor().visitNonNull(pruned, ctx, getCursor().getParentOrThrow());
            }
        };
    }

    private static Map<String, String> extractExistingEntries(Yaml.Documents docs) {
        Map<String, String> entries = new HashMap<>();
        new YamlIsoVisitor<Integer>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, Integer p) {
                if ("recipeList".equals(entry.getKey().getValue()) &&
                        entry.getValue() instanceof Yaml.Sequence) {
                    Yaml.Sequence seq = (Yaml.Sequence) entry.getValue();
                    for (Yaml.Sequence.Entry seqEntry : seq.getEntries()) {
                        if (seqEntry.getBlock() instanceof Yaml.Mapping) {
                            Yaml.Mapping mapping = (Yaml.Mapping) seqEntry.getBlock();
                            for (Yaml.Mapping.Entry me : mapping.getEntries()) {
                                if (me.getValue() instanceof Yaml.Mapping) {
                                    String methodPattern = null;
                                    String replacement = null;
                                    Yaml.Mapping inner = (Yaml.Mapping) me.getValue();
                                    for (Yaml.Mapping.Entry ie : inner.getEntries()) {
                                        if ("methodPattern".equals(ie.getKey().getValue()) &&
                                                ie.getValue() instanceof Yaml.Scalar) {
                                            methodPattern = ((Yaml.Scalar) ie.getValue()).getValue();
                                        } else if ("replacement".equals(ie.getKey().getValue()) &&
                                                ie.getValue() instanceof Yaml.Scalar) {
                                            replacement = ((Yaml.Scalar) ie.getValue()).getValue();
                                        }
                                    }
                                    if (methodPattern != null && replacement != null) {
                                        entries.put(methodPattern, replacement);
                                    }
                                }
                            }
                        }
                    }
                }
                return entry;
            }
        }.visit(docs, 0);
        return entries;
    }

    private static String buildRecipeListSnippet(List<MethodInlineCandidate> candidates) {
        StringBuilder yaml = new StringBuilder();
        for (MethodInlineCandidate candidate : candidates) {
            yaml.append("- org.openrewrite.java.InlineMethodCalls:\n");
            yaml.append("    methodPattern: '").append(candidate.getMethodPattern()).append("'\n");
            yaml.append("    replacement: '").append(candidate.getReplacement()).append("'\n");
        }
        return yaml.toString();
    }

    private static String deriveRecipeName(JavaProject javaProject) {
        if (javaProject != null && javaProject.getPublication() != null) {
            JavaProject.Publication pub = javaProject.getPublication();
            String groupId = pub.getGroupId();
            String artifactId = pub.getArtifactId();
            StringBuilder pascalCase = new StringBuilder();
            boolean capitalize = true;
            for (char c : artifactId.toCharArray()) {
                if (c == '-' || c == '.') {
                    capitalize = true;
                } else {
                    pascalCase.append(capitalize ? Character.toUpperCase(c) : c);
                    capitalize = false;
                }
            }
            return groupId + ".recipes." + pascalCase + "DeprecatedMethods";
        }
        return "org.openrewrite.recipes.InlineDeprecatedMethods";
    }

    private static String buildMethodPattern(JavaType.Method methodType) {
        String declaringType = methodType.getDeclaringType().getFullyQualifiedName();
        String methodName = methodType.isConstructor()
                ? methodType.getDeclaringType().getClassName()
                : methodType.getName();
        String params = methodType.getParameterTypes().stream()
                .map(GenerateDeprecatedMethodRecipes::typeToPattern)
                .collect(Collectors.joining(", "));
        return declaringType + " " + methodName + "(" + params + ")";
    }

    private static String buildReplacement(J.MethodInvocation invocation, Cursor cursor) {
        String name;
        if (invocation.getMethodType() != null && invocation.getMethodType().isConstructor()) {
            name = "this";
        } else {
            String select = invocation.getSelect() != null
                    ? invocation.getSelect().printTrimmed(cursor) + "."
                    : "";
            name = select + invocation.getSimpleName();
        }
        String args = invocation.getArguments().stream()
                .map(arg -> arg.printTrimmed(cursor))
                .collect(Collectors.joining(", "));
        return name + "(" + args + ")";
    }

    static String typeToPattern(JavaType type) {
        if (type instanceof JavaType.Primitive) {
            return ((JavaType.Primitive) type).getKeyword();
        }
        if (type instanceof JavaType.Array) {
            return typeToPattern(((JavaType.Array) type).getElemType()) + "[]";
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq != null) {
            return fq.getFullyQualifiedName();
        }
        return type.toString();
    }

    private static Path projectBasePath(Path sourcePath) {
        String pathStr = sourcePath.toString().replace('\\', '/');
        int idx = pathStr.indexOf("src/main/java/");
        if (idx < 0) {
            idx = pathStr.indexOf("src/test/java/");
        }
        if (idx <= 0) {
            return Paths.get("");
        }
        return Paths.get(pathStr.substring(0, idx));
    }

    private static class RemoveMatchingEntries extends YamlIsoVisitor<ExecutionContext> {
        private final Set<String> patternsToRemove;

        RemoveMatchingEntries(Set<String> patternsToRemove) {
            this.patternsToRemove = patternsToRemove;
        }

        @Override
        public Yaml.Sequence visitSequence(Yaml.Sequence sequence, ExecutionContext ctx) {
            Yaml.Sequence seq = super.visitSequence(sequence, ctx);

            // Only process the recipeList sequence
            Cursor parent = getCursor().getParentTreeCursor();
            if (!(parent.getValue() instanceof Yaml.Mapping.Entry)) {
                return seq;
            }
            Yaml.Mapping.Entry parentEntry = parent.getValue();
            if (!"recipeList".equals(parentEntry.getKey().getValue())) {
                return seq;
            }

            List<Yaml.Sequence.Entry> filtered = seq.getEntries().stream()
                    .filter(entry -> {
                        String methodPattern = extractMethodPattern(entry);
                        return methodPattern == null || !patternsToRemove.contains(methodPattern);
                    })
                    .collect(Collectors.toList());

            if (filtered.size() == seq.getEntries().size()) {
                return seq;
            }
            return seq.withEntries(filtered);
        }

        private static String extractMethodPattern(Yaml.Sequence.Entry entry) {
            if (!(entry.getBlock() instanceof Yaml.Mapping)) {
                return null;
            }
            Yaml.Mapping mapping = (Yaml.Mapping) entry.getBlock();
            for (Yaml.Mapping.Entry e : mapping.getEntries()) {
                if (e.getValue() instanceof Yaml.Mapping) {
                    Yaml.Mapping innerMapping = (Yaml.Mapping) e.getValue();
                    for (Yaml.Mapping.Entry inner : innerMapping.getEntries()) {
                        if ("methodPattern".equals(inner.getKey().getValue()) &&
                                inner.getValue() instanceof Yaml.Scalar) {
                            return ((Yaml.Scalar) inner.getValue()).getValue();
                        }
                    }
                }
            }
            return null;
        }
    }

    public static class Accumulator {
        final Set<Path> existingOutputFiles = new HashSet<>();
        final Map<JavaProject, List<MethodInlineCandidate>> candidatesByProject = new LinkedHashMap<>();
        final Map<JavaProject, Path> projectBasePaths = new HashMap<>();
    }

    @Value
    static class MethodInlineCandidate {
        String methodPattern;
        String replacement;
    }
}
