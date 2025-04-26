/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.recipes;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.config.RecipeExample;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.trait.Literal;
import org.openrewrite.java.trait.Traits;
import org.openrewrite.java.tree.*;
import org.openrewrite.text.PlainText;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml.Documents;
import org.yaml.snakeyaml.Yaml;

import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class ExamplesExtractor extends ScanningRecipe<ExamplesExtractor.Accumulator> {

    private static final String DOCUMENT_EXAMPLE = "org.openrewrite.DocumentExample";
    private static final AnnotationMatcher DOCUMENT_EXAMPLE_ANNOTATION_MATCHER = new AnnotationMatcher("@" + DOCUMENT_EXAMPLE);

    private static final MethodMatcher DEFAULTS_METHOD_MATCHER = new MethodMatcher(
            "org.openrewrite.test.RewriteTest defaults(org.openrewrite.test.RecipeSpec)", true);
    private static final MethodMatcher REWRITE_RUN_METHOD_MATCHER_WITH_SPEC = new MethodMatcher(
            "org.openrewrite.test.RewriteTest rewriteRun(java.util.function.Consumer, org.openrewrite.test.SourceSpecs[])");
    private static final MethodMatcher REWRITE_RUN_METHOD_MATCHER = new MethodMatcher(
            "org.openrewrite.test.RewriteTest rewriteRun(org.openrewrite.test.SourceSpecs[])");

    private static final MethodMatcher ASSERTIONS_METHOD_MATCHER = new MethodMatcher("org.openrewrite.*.Assertions *(..)");
    private static final MethodMatcher BUILD_GRADLE_METHOD_MATCHER = new MethodMatcher("org.openrewrite.gradle.Assertions buildGradle(..)");
    private static final MethodMatcher POM_XML_METHOD_MATCHER = new MethodMatcher("org.openrewrite.maven.Assertions pomXml(..)");
    private static final MethodMatcher ACTIVE_RECIPES_METHOD_MATCHER = new MethodMatcher("org.openrewrite.config.Environment activateRecipes(..)");
    private static final MethodMatcher RECIPE_FROM_RESOURCES_METHOD_MATCHER = new MethodMatcher("org.openrewrite.test.RecipeSpec#recipeFromResource*(..)");
    private static final MethodMatcher PATH_METHOD_MATCHER = new MethodMatcher("org.openrewrite.test.SourceSpec path(java.lang.String)");
    private static final MethodMatcher RECIPE_METHOD_MATCHER = new MethodMatcher("org.openrewrite.test.RecipeSpec#recipe*(..)");

    @Override
    public String getDisplayName() {
        return "Extract documentation examples from tests";
    }

    @Override
    public String getDescription() {
        return "Extract the before/after sources from tests annotated with `@DocumentExample`, " +
                "and generate a YAML file with those examples to be shown in the documentation to show usage.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> examplesExtractorVisitor = Preconditions.check(
                new UsesType<>(DOCUMENT_EXAMPLE, false),
                new ExamplesExtractorVisitor(acc)
        );
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (tree instanceof JavaSourceFile) {
                    examplesExtractorVisitor.visit(tree, ctx);
                } else if (tree instanceof Documents) {
                    new YamlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Documents visitDocuments(Documents documents, ExecutionContext ctx) {
                            Path sourcePath = documents.getSourcePath();
                            if (sourcePath.endsWith("examples.yml")) {
                                acc.existingExampleFiles.add(sourcePath);
                            }
                            return documents;
                        }
                    }.visit(tree, ctx);
                } else if (tree instanceof PlainText &&
                        ((PlainText) tree).getSourcePath().endsWith("licenseHeader.txt")) {
                    acc.licenseHeader = ((PlainText) tree).getText();
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        Documents emptyDoc = YamlParser.builder().build()
                .parse("---\n")
                .filter(sf -> sf instanceof Documents)
                .map(sf -> (Documents) sf)
                .findFirst().get();
        return acc.projectRecipeExamples.entrySet().stream()
                .filter(entry -> !acc.existingExampleFiles.contains(entry.getKey()))
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> emptyDoc.withSourcePath(entry.getKey()))
                .map(doc -> doc.withId(Tree.randomId()))
                .collect(toList());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Documents visitDocuments(Documents existingDocuments, ExecutionContext ctx) {
                Map<String, List<RecipeExample>> recipeExamples = acc.projectRecipeExamples.get(existingDocuments.getSourcePath());
                if (recipeExamples == null || recipeExamples.isEmpty()) {
                    return existingDocuments;
                }
                String yaml = new YamlPrinter().print(acc.licenseHeader, recipeExamples);
                Optional<Documents> first = YamlParser.builder().build().parse(yaml)
                        .filter(sf -> sf instanceof Documents)
                        .map(sf -> (Documents) sf)
                        .findFirst();
                if (first.isPresent() && !first.get().printAll().equals(existingDocuments.printAll())) {
                    return existingDocuments.withDocuments(first.get().getDocuments());
                }
                return existingDocuments;
            }
        };
    }

    public static class Accumulator {
        @Nullable
        String licenseHeader;

        final List<Path> existingExampleFiles = new ArrayList<>();
        // Target example file path -> RecipeName -> Examples
        final Map<Path, Map<String, List<RecipeExample>>> projectRecipeExamples = new HashMap<>();
    }

    static class ExamplesExtractorVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String RECIPE_KEY = "recipeName";
        private static final String DESCRIPTION_KEY = "description";

        private final Accumulator acc;

        public ExamplesExtractorVisitor(Accumulator acc) {
            this.acc = acc;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (DEFAULTS_METHOD_MATCHER.matches(method.getMethodType())) {
                getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, RECIPE_KEY, findRecipe(method));
                return method;
            }

            Optional<Annotated> annotated = Traits.annotated(DOCUMENT_EXAMPLE_ANNOTATION_MATCHER).lower(getCursor()).findAny();
            if (annotated.isPresent()) {
                String exampleDescription = annotated.get().getDefaultAttribute("value").map(Literal::getString).orElse("");
                getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, DESCRIPTION_KEY, exampleDescription);
                return super.visitMethodDeclaration(method, ctx);
            }

            return method;
        }

        private static class RecipeNameAndParameters {
            String name = "";
            List<String> parameters = new ArrayList<>();
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            List<Expression> args = method.getArguments();

            int sourceStartIndex;
            if (REWRITE_RUN_METHOD_MATCHER_WITH_SPEC.matches(method)) {
                getCursor().putMessage(RECIPE_KEY, findRecipe(args.get(0)));
                sourceStartIndex = 1;
            } else if (REWRITE_RUN_METHOD_MATCHER.matches(method)) {
                sourceStartIndex = 0;
            } else {
                return method;
            }

            // Through cursor, retrieve the project, recipe and description we've visited so far
            RecipeNameAndParameters recipe = getCursor().getNearestMessage(RECIPE_KEY); // Default or local spec recipe
            if (recipe == null) {
                return method; // Some parser tests do not include a recipe
            }
            String exampleDescription = getCursor().getNearestMessage(DESCRIPTION_KEY);

            RecipeExample example = new RecipeExample();
            List<Expression> sourceArgs = args.subList(sourceStartIndex, args.size());
            example.setSources(extractRecipeExampleSources(sourceArgs));
            if (!example.getSources().isEmpty()) {
                example.setDescription(exampleDescription);
                example.setParameters(recipe.parameters);

                String testSourcePath = getCursor().firstEnclosingOrThrow(J.CompilationUnit.class).getSourcePath().toString();
                String root = testSourcePath.substring(0, testSourcePath.indexOf("src/"));
                Path targetPath = Paths.get(root).resolve("src/main/resources/META-INF/rewrite/examples.yml");
                acc.projectRecipeExamples
                        .computeIfAbsent(targetPath, key -> new TreeMap<>())
                        .computeIfAbsent(recipe.name, key -> new ArrayList<>()).add(example);
            }

            return method;
        }


        private @Nullable RecipeNameAndParameters findRecipe(J tree) {
            return new JavaIsoVisitor<AtomicReference<RecipeNameAndParameters>>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicReference<RecipeNameAndParameters> recipe) {
                    if (RECIPE_METHOD_MATCHER.matches(method)) {
                        new JavaIsoVisitor<AtomicReference<RecipeNameAndParameters>>() {
                            @Override
                            public J.NewClass visitNewClass(J.NewClass newClass, AtomicReference<RecipeNameAndParameters> recipe) {
                                JavaType type = newClass.getClazz() != null ? newClass.getClazz().getType() : null;
                                if (type == null) {
                                    type = newClass.getType();
                                }

                                if (TypeUtils.isAssignableTo("org.openrewrite.Recipe", type) && type instanceof JavaType.Class) {
                                    JavaType.Class tc = (JavaType.Class) type;
                                    RecipeNameAndParameters recipeNameAndParameters = new RecipeNameAndParameters();
                                    recipeNameAndParameters.name = tc.getFullyQualifiedName();
                                    recipeNameAndParameters.parameters = extractParameters(newClass.getArguments());
                                    recipe.set(recipeNameAndParameters);
                                }
                                return newClass;
                            }

                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                            AtomicReference<RecipeNameAndParameters> recipe) {
                                if (ACTIVE_RECIPES_METHOD_MATCHER.matches(method)) {
                                    Expression arg = method.getArguments().get(0);
                                    if (arg instanceof J.Literal && ((J.Literal) arg).getValue() != null) {
                                        RecipeNameAndParameters recipeNameAndParameters = new RecipeNameAndParameters();
                                        recipeNameAndParameters.name = ((J.Literal) arg).getValue().toString();
                                        recipe.set(recipeNameAndParameters);
                                    }
                                    return method;
                                }
                                if (RECIPE_FROM_RESOURCES_METHOD_MATCHER.matches(method)) {
                                    Expression arg = method.getArguments().get(method.getArguments().size() - 1);
                                    if (arg instanceof J.Literal && ((J.Literal) arg).getValue() != null) {
                                        RecipeNameAndParameters recipeNameAndParameters = new RecipeNameAndParameters();
                                        recipeNameAndParameters.name = ((J.Literal) arg).getValue().toString();
                                        recipe.set(recipeNameAndParameters);
                                    }
                                    return method;
                                }
                                return super.visitMethodInvocation(method, recipe);
                            }
                        }.visit(tree, recipe);
                    }
                    return super.visitMethodInvocation(method, recipe);
                }
            }.reduce(tree, new AtomicReference<>()).get();
        }

        private List<String> extractParameters(List<Expression> args) {
            return args.stream()
                    .map(arg -> {
                        if (arg instanceof J.Empty) {
                            return null;
                        } else if (arg instanceof J.Literal) {
                            J.Literal literal = (J.Literal) arg;
                            if (literal.getValue() != null) {
                                return literal.getValue().toString();
                            } else {
                                return ((J.Literal) arg).getValueSource();
                            }
                        } else if (arg instanceof J.NewArray) {
                            List<Expression> initializer = Objects.requireNonNull(((J.NewArray) arg).getInitializer());
                            return extractParameters(initializer).stream().collect(joining(", ", "[ ", " ]"));
                        } else {
                            return arg.toString();
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(toList());
        }

        private List<RecipeExample.Source> extractRecipeExampleSources(List<Expression> sourceSpecArg) {
            return new JavaIsoVisitor<List<RecipeExample.Source>>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, List<RecipeExample.Source> sources) {
                    method = super.visitMethodInvocation(method, sources);

                    RecipeExample.Source source = new RecipeExample.Source("", null, null, "");
                    String language;
                    if (BUILD_GRADLE_METHOD_MATCHER.matches(method)) {
                        source.setPath("build.gradle");
                        language = "groovy";
                    } else if (POM_XML_METHOD_MATCHER.matches(method)) {
                        source.setPath("pom.xml");
                        language = "xml";
                    } else if (ASSERTIONS_METHOD_MATCHER.matches(method)) {
                        language = method.getSimpleName();
                    } else if (PATH_METHOD_MATCHER.matches(method)) {
                        if (method.getArguments().get(0) instanceof J.Literal) {
                            source.setPath((String) ((J.Literal) method.getArguments().get(0)).getValue());
                        }
                        return method;
                    } else {
                        return method;
                    }
                    source.setLanguage(language);

                    // arg0 is always `before`. arg1 is optional to be `after`, to adjust if code changed
                    List<Expression> args = method.getArguments();
                    J.Literal before = !args.isEmpty() ? args.get(0).getType() == JavaType.Primitive.String ? (J.Literal) args.get(0) : null : null;
                    J.Literal after = args.size() > 1 ? args.get(1).getType() == JavaType.Primitive.String ? (J.Literal) args.get(1) : null : null;
                    if (before != null && before.getValue() != null) {
                        source.setBefore((String) before.getValue());
                    }
                    if (after != null) {
                        source.setAfter((String) after.getValue());
                    }
                    if (StringUtils.isNotEmpty(source.getBefore()) || StringUtils.isNotEmpty(source.getAfter())) {
                        sources.add(source);
                    }
                    return method;
                }
            }.reduce(sourceSpecArg, new ArrayList<>());
        }
    }

    static class YamlPrinter {

        private final Yaml yaml = new Yaml();

        String print(@Nullable String licenseHeader, Map<String, List<RecipeExample>> recipeExamples) {
            StringWriter stringWriter = new StringWriter();
            if (StringUtils.isNotEmpty(licenseHeader)) {
                stringWriter
                        .append("# ")
                        .append(licenseHeader
                                .replace("${year}", "2025") // Hardcoded to avoid suggestions in 2026+
                                .replace("\n", "\n# "))
                        .append("\n");
            }
            for (Map.Entry<String, List<RecipeExample>> recipeEntry : recipeExamples.entrySet()) {
                Map<String, Object> yamlDoc = print(recipeEntry.getKey(), recipeEntry.getValue());
                stringWriter.append("---\n").append(yaml.dumpAsMap(yamlDoc));
            }
            return stringWriter.toString();
        }

        private Map<String, Object> print(String recipeName, List<RecipeExample> examples) {
            Map<String, Object> yamlDoc = new LinkedHashMap<>();
            yamlDoc.put("type", "specs.openrewrite.org/v1beta/example");
            yamlDoc.put("recipeName", recipeName);
            List<Map<String, Object>> examplesData = new ArrayList<>();
            yamlDoc.put("examples", examplesData);

            for (RecipeExample example : examples) {
                Map<String, Object> exampleData = new LinkedHashMap<>();
                example.getDescription();
                exampleData.put("description", example.getDescription());

                List<String> params = example.getParameters();
                if (!params.isEmpty()) {
                    exampleData.put("parameters", params);
                }

                List<Map<String, String>> sourcesData = new ArrayList<>();
                for (RecipeExample.Source source : example.getSources()) {

                    Map<String, String> sourceData = new LinkedHashMap<>();
                    if (StringUtils.isNotEmpty(source.getBefore())) {
                        sourceData.put("before", source.getBefore());
                    }

                    if (StringUtils.isNotEmpty(source.getAfter())) {
                        sourceData.put("after", source.getAfter());
                    }

                    if (StringUtils.isNotEmpty(source.getPath())) {
                        sourceData.put("path", PathUtils.separatorsToUnix(source.getPath()));
                    }

                    if (StringUtils.isNotEmpty(source.getLanguage())) {
                        sourceData.put("language", source.getLanguage());
                    }

                    sourcesData.add(sourceData);
                }

                exampleData.put("sources", sourcesData);
                examplesData.add(exampleData);
            }
            return yamlDoc;
        }
    }
}
