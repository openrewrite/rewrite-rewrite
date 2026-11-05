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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Collections.emptyList;

public class FindDeprecatedMethodDelegations extends ScanningRecipe<FindDeprecatedMethodDelegations.Accumulator> {

    private static final AnnotationMatcher DEPRECATED_MATCHER = new AnnotationMatcher("@java.lang.Deprecated");
    private static final AnnotationMatcher TO_BE_REMOVED_MATCHER = new AnnotationMatcher("@org.openrewrite.internal.ToBeRemoved");

    transient DeprecatedMethodDelegations dataTable = new DeprecatedMethodDelegations(this);

    @Getter
    final String displayName = "Find deprecated methods that delegate to their replacement";

    @Getter
    final String description = "Finds `@Deprecated` and `@ToBeRemoved` methods and constructors outside of tests " +
            "whose body is a single call to another member of the same class, and that callers in other packages could inline. " +
            "Lists the `InlineMethodCalls` step for each, with the imports its replacement needs, " +
            "and the artifacts that provide their types as `classpathFromResources`.";

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
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile sourceFile = (JavaSourceFile) tree;
                    for (J.ClassDeclaration classDeclaration : sourceFile.getClasses()) {
                        if (classDeclaration.getType() != null) {
                            acc.declaringSourcePaths.put(classDeclaration.getType().getFullyQualifiedName(), sourceFile.getSourcePath());
                        }
                    }
                }
                // Kotlin build scripts are Java source files too
                if (tree instanceof SourceFile) {
                    SourceFile sourceFile = (SourceFile) tree;
                    String coordinates = buildCoordinates(sourceFile);
                    if (coordinates != null) {
                        Path directory = sourceFile.getSourcePath().getParent();
                        acc.coordinatesByDirectory.put(directory == null ? Paths.get("") : directory, coordinates);
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return super.isAcceptable(sourceFile, ctx) && sourceFile.getMarkers().findFirst(JavaSourceSet.class)
                        .map(sourceSet -> !sourceSet.getName().toLowerCase(Locale.ROOT).contains("test"))
                        .orElse(true);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = md.getMethodType();
                if (methodType == null || !isDeprecated(md) || !isVisibleOutsidePackage(methodType)) {
                    return md;
                }
                MethodCall delegate = delegateInSameClass(md, methodType);
                if (delegate == null) {
                    return md;
                }
                Requirements requirements = requirements(delegate, methodType);
                if (!requirements.inlinable) {
                    return md;
                }
                JavaType.FullyQualified constructed = md.isConstructor() ? outermost(methodType.getDeclaringType()) : null;
                if (constructed != null) {
                    // `InlineMethodCalls` otherwise removes the import of the class its `new Outer.Inner(..)` names,
                    // when it can not attribute that `new` for lack of a classpath
                    requirements.imports.add(dotted(constructed));
                }
                Set<String> classpath = classpath(requirements, constructed, getCursor().firstEnclosingOrThrow(JavaSourceFile.class), acc);
                if (classpath == null) {
                    return md;
                }

                String methodPattern = MethodMatcher.methodPattern(methodType);
                String replacement = printReplacement(delegate);
                dataTable.insertRow(ctx, new DeprecatedMethodDelegations.Row(
                        methodPattern,
                        replacement,
                        recipeYaml(methodPattern, replacement, requirements, classpath)));
                return SearchResult.found(md);
            }

            private String printReplacement(MethodCall delegate) {
                J commentFree = new JavaIsoVisitor<Integer>() {
                    @Override
                    public Space visitSpace(Space space, Space.Location loc, Integer p) {
                        return space.withComments(emptyList());
                    }
                }.visitNonNull(delegate, 0);
                return commentFree.printTrimmed(getCursor())
                        .replaceAll("\\s*\\n\\s*(?=[.)])", "")
                        .replaceAll("(?<=\\()\\s*\\n\\s*", "")
                        .replaceAll("\\s*\\n\\s*", " ");
            }
        };
    }

    private static boolean isDeprecated(J.MethodDeclaration md) {
        return md.getLeadingAnnotations().stream().anyMatch(
                ann -> DEPRECATED_MATCHER.matches(ann) || TO_BE_REMOVED_MATCHER.matches(ann));
    }

    private static @Nullable String buildCoordinates(SourceFile sourceFile) {
        Optional<GradleProject> gradleProject = sourceFile.getMarkers().findFirst(GradleProject.class);
        if (gradleProject.isPresent()) {
            return coordinates(gradleProject.get().getGroup(), gradleProject.get().getName());
        }
        return sourceFile.getMarkers().findFirst(MavenResolutionResult.class)
                .map(MavenResolutionResult::getPom)
                .map(pom -> coordinates(pom.getGroupId(), pom.getArtifactId()))
                .orElse(null);
    }

    private static @Nullable String coordinates(@Nullable String groupId, @Nullable String artifactId) {
        return StringUtils.isBlank(groupId) || StringUtils.isBlank(artifactId) ? null : groupId + ":" + artifactId;
    }

    // The `group:artifact` of each artifact whose types the replacement imports, from the build of the module in this
    // repository that declares the type, or else the source set's dependencies, or null when that can not be told.
    // The JDK is always on the classpath, and the class a constructor call names is already imported.
    private static @Nullable Set<String> classpath(Requirements requirements, JavaType.@Nullable FullyQualified constructed,
                                                   JavaSourceFile sourceFile, Accumulator acc) {
        Set<String> classpath = new TreeSet<>();
        for (JavaType.FullyQualified type : requirements.importedTypes) {
            if (type.getPackageName().startsWith("java.") ||
                    constructed != null && TypeUtils.isOfType(outermost(type), constructed)) {
                continue;
            }
            Path declaringSourcePath = acc.declaringSourcePaths.get(outermost(type).getFullyQualifiedName());
            String coordinates = declaringSourcePath != null ?
                    moduleCoordinates(declaringSourcePath, acc) :
                    dependencyCoordinates(type, sourceFile);
            if (coordinates == null) {
                return null;
            }
            classpath.add(coordinates);
        }
        return classpath;
    }

    private static @Nullable String dependencyCoordinates(JavaType.FullyQualified type, JavaSourceFile sourceFile) {
        String outermost = outermost(type).getFullyQualifiedName();
        return sourceFile.getMarkers().findFirst(JavaSourceSet.class)
                .flatMap(sourceSet -> sourceSet.getGavToTypes().entrySet().stream()
                        .filter(gavTypes -> gavTypes.getValue().stream()
                                .anyMatch(t -> outermost.equals(t.getFullyQualifiedName())))
                        .map(gavTypes -> gavTypes.getKey().replaceFirst(":[^:]*$", ""))
                        .findFirst())
                .orElse(null);
    }

    private static @Nullable String moduleCoordinates(Path sourcePath, Accumulator acc) {
        for (Path directory = sourcePath.getParent(); directory != null; directory = directory.getParent()) {
            String coordinates = acc.coordinatesByDirectory.get(directory);
            if (coordinates != null) {
                return coordinates;
            }
        }
        return acc.coordinatesByDirectory.get(Paths.get(""));
    }

    private static JavaType.FullyQualified outermost(JavaType.FullyQualified type) {
        JavaType.FullyQualified outermost = type;
        while (outermost.getOwningClass() != null) {
            outermost = outermost.getOwningClass();
        }
        return outermost;
    }

    private static boolean isVisibleOutsidePackage(JavaType.Method methodType) {
        if (!methodType.hasFlags(Flag.Public) && !methodType.hasFlags(Flag.Protected)) {
            return false;
        }
        for (JavaType.FullyQualified type = methodType.getDeclaringType(); type != null; type = type.getOwningClass()) {
            if (!type.hasFlags(Flag.Public) && !type.hasFlags(Flag.Protected)) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable MethodCall delegateInSameClass(J.MethodDeclaration md, JavaType.Method methodType) {
        if (md.getBody() == null || md.getBody().getStatements().size() != 1) {
            return null;
        }
        Statement statement = md.getBody().getStatements().get(0);
        J call = statement instanceof J.Return ? ((J.Return) statement).getExpression() : statement;
        if (!(call instanceof MethodCall)) {
            return null;
        }
        JavaType.Method delegateType = ((MethodCall) call).getMethodType();
        if (delegateType == null || !TypeUtils.isOfType(methodType.getDeclaringType(), delegateType.getDeclaringType())) {
            return null;
        }
        return (MethodCall) call;
    }

    // Callers outside the declaring class only see public members, or protected ones too when they subclass to reach a
    // protected deprecated method. Unqualified instance calls and fields only resolve inside the declaring class, other
    // than the delegate itself, which `InlineMethodCalls` qualifies with the original receiver. A parameter used twice
    // would evaluate the argument a caller passes twice.
    private static Requirements requirements(MethodCall delegate, JavaType.Method deprecated) {
        boolean protectedVisible = deprecated.hasFlags(Flag.Protected);
        return new JavaIsoVisitor<Requirements>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Requirements r) {
                J.Identifier id = super.visitIdentifier(identifier, r);
                if ("super".equals(id.getSimpleName())) {
                    r.inlinable = false;
                    return id;
                }
                Object parent = getCursor().getParentTreeCursor().getValue();
                boolean qualified = parent instanceof J.FieldAccess && ((J.FieldAccess) parent).getName() == identifier;
                JavaType.Variable variable = id.getFieldType();
                if (variable != null) {
                    if (deprecated.getParameterNames().contains(variable.getName()) && !(variable.getOwner() instanceof JavaType.FullyQualified) &&
                            !r.usedParameters.add(variable.getName())) {
                        r.inlinable = false;
                    }
                    if (variable.getOwner() instanceof JavaType.FullyQualified) {
                        r.requireVisible(variable.hasFlags(Flag.Public), variable.hasFlags(Flag.Protected), protectedVisible);
                        if (!qualified) {
                            if (variable.hasFlags(Flag.Static)) {
                                r.staticImports.add(dotted((JavaType.FullyQualified) variable.getOwner()) + "." + variable.getName());
                            } else {
                                r.inlinable = false;
                            }
                        }
                    }
                } else if (!qualified && !"this".equals(id.getSimpleName()) &&
                        id.getType() instanceof JavaType.FullyQualified && !(id.getType() instanceof JavaType.Unknown)) {
                    JavaType.FullyQualified type = (JavaType.FullyQualified) id.getType();
                    r.requireVisible(type.hasFlags(Flag.Public), type.hasFlags(Flag.Protected), protectedVisible);
                    if (!"java.lang".equals(type.getPackageName())) {
                        r.imports.add(dotted(type));
                        r.importedTypes.add(type);
                    }
                }
                return id;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Requirements r) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, r);
                JavaType.Method type = mi.getMethodType();
                if (type == null) {
                    r.inlinable = false;
                    return mi;
                }
                r.requireVisible(type.hasFlags(Flag.Public), type.hasFlags(Flag.Protected), protectedVisible);
                if (mi.getSelect() == null) {
                    if (type.hasFlags(Flag.Static)) {
                        r.staticImports.add(dotted(type.getDeclaringType()) + "." + type.getName());
                    } else if (method != delegate) {
                        r.inlinable = false;
                    }
                }
                return mi;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, Requirements r) {
                J.NewClass nc = super.visitNewClass(newClass, r);
                JavaType.Method type = nc.getConstructorType();
                if (type == null || nc.getBody() != null) {
                    r.inlinable = false;
                    return nc;
                }
                r.requireVisible(type.hasFlags(Flag.Public), type.hasFlags(Flag.Protected), protectedVisible);
                return nc;
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, Requirements r) {
                J.MemberReference mr = super.visitMemberReference(memberRef, r);
                JavaType.Method type = mr.getMethodType();
                if (type == null) {
                    r.inlinable = false;
                    return mr;
                }
                r.requireVisible(type.hasFlags(Flag.Public), type.hasFlags(Flag.Protected), protectedVisible);
                return mr;
            }
        }.reduce(delegate, new Requirements());
    }

    private static String dotted(JavaType.FullyQualified type) {
        return TypeUtils.toFullyQualifiedName(type.getFullyQualifiedName());
    }

    private static String recipeYaml(String methodPattern, String replacement, Requirements requirements, Set<String> classpath) {
        StringBuilder yaml = new StringBuilder("- org.openrewrite.java.InlineMethodCalls:\n")
                .append("    methodPattern: ").append(quote(methodPattern)).append('\n')
                .append("    replacement: ").append(quote(replacement));
        appendList(yaml, "imports", requirements.imports);
        appendList(yaml, "staticImports", requirements.staticImports);
        if (!classpath.isEmpty()) {
            // `classpathFromResources` matches a type table's `artifactId-version` by prefix; the comment names the
            // artifact for `parserClasspath` to build that type table from, which then pins the prefix's major version
            yaml.append("\n    classpathFromResources:");
            for (String coordinates : classpath) {
                yaml.append("\n      - ").append(quote(coordinates.substring(coordinates.indexOf(':') + 1) + "-")).append(" # ").append(coordinates);
            }
        }
        return yaml.toString();
    }

    private static void appendList(StringBuilder yaml, String key, Set<String> values) {
        if (!values.isEmpty()) {
            yaml.append("\n    ").append(key).append(':');
            for (String value : values) {
                yaml.append("\n      - ").append(quote(value));
            }
        }
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static class Requirements {
        final Set<String> imports = new TreeSet<>();
        final Set<String> staticImports = new TreeSet<>();
        final Set<JavaType.FullyQualified> importedTypes = new HashSet<>();
        final Set<String> usedParameters = new HashSet<>();
        boolean inlinable = true;

        void requireVisible(boolean isPublic, boolean isProtected, boolean protectedVisible) {
            if (!isPublic && !(protectedVisible && isProtected)) {
                inlinable = false;
            }
        }
    }

    public static class Accumulator {
        final Map<Path, String> coordinatesByDirectory = new HashMap<>();
        final Map<String, Path> declaringSourcePaths = new HashMap<>();
    }
}
