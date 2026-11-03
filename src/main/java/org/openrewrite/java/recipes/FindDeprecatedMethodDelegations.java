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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Collections.emptyList;

public class FindDeprecatedMethodDelegations extends Recipe {

    private static final AnnotationMatcher DEPRECATED_MATCHER = new AnnotationMatcher("@java.lang.Deprecated");
    private static final AnnotationMatcher TO_BE_REMOVED_MATCHER = new AnnotationMatcher("@org.openrewrite.internal.ToBeRemoved");

    transient DeprecatedMethodDelegations dataTable = new DeprecatedMethodDelegations(this);

    @Getter
    final String displayName = "Find deprecated methods that delegate to their replacement";

    @Getter
    final String description = "Finds `@Deprecated` and `@ToBeRemoved` methods and constructors outside of tests " +
            "whose body is a single call to another member of the same class, and that callers in other packages could inline. " +
            "Lists the `InlineMethodCalls` step for each, with the imports its replacement needs.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
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
                Requirements requirements = requirements(delegate, methodType.hasFlags(Flag.Protected));
                if (!requirements.inlinable) {
                    return md;
                }

                String methodPattern = MethodMatcher.methodPattern(methodType);
                String replacement = printReplacement(delegate);
                dataTable.insertRow(ctx, new DeprecatedMethodDelegations.Row(
                        methodPattern,
                        replacement,
                        recipeYaml(methodPattern, replacement, requirements)));
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
    // than the delegate itself, which `InlineMethodCalls` qualifies with the original receiver.
    private static Requirements requirements(MethodCall delegate, boolean protectedVisible) {
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

    private static String recipeYaml(String methodPattern, String replacement, Requirements requirements) {
        StringBuilder yaml = new StringBuilder("- org.openrewrite.java.InlineMethodCalls:\n")
                .append("    methodPattern: ").append(quote(methodPattern)).append('\n')
                .append("    replacement: ").append(quote(replacement));
        appendList(yaml, "imports", requirements.imports);
        appendList(yaml, "staticImports", requirements.staticImports);
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
        boolean inlinable = true;

        void requireVisible(boolean isPublic, boolean isProtected, boolean protectedVisible) {
            if (!isPublic && !(protectedVisible && isProtected)) {
                inlinable = false;
            }
        }
    }
}
