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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.newSetFromMap;

public class GroupNoChangeTests extends Recipe {

    private static final String NESTED_CLASS_NAME = "NoChange";
    private static final String NESTED_FQN = "org.junit.jupiter.api.Nested";
    private static final String REWRITE_TEST_FQN = "org.openrewrite.test.RewriteTest";
    private static final String SOURCE_SPECS_FQN = "org.openrewrite.test.SourceSpecs";

    private static final AnnotationMatcher TEST = new AnnotationMatcher("@org.junit.jupiter.api.Test");
    private static final AnnotationMatcher PARAMETERIZED_TEST = new AnnotationMatcher("@org.junit.jupiter.params.ParameterizedTest");
    private static final AnnotationMatcher NESTED = new AnnotationMatcher("@" + NESTED_FQN);
    private static final AnnotationMatcher DOCUMENT_EXAMPLE = new AnnotationMatcher("@org.openrewrite.DocumentExample");
    private static final AnnotationMatcher ENABLED_IF = new AnnotationMatcher("@org.junit.jupiter.api.condition.EnabledIf");
    private static final AnnotationMatcher DISABLED_IF = new AnnotationMatcher("@org.junit.jupiter.api.condition.DisabledIf");
    private static final MethodMatcher REWRITE_RUN = new MethodMatcher(REWRITE_TEST_FQN + " rewriteRun(..)");

    private static final Set<String> CHANGE_ASSERTIONS = new HashSet<>(asList(
            "after", "afterRecipe", "dataTable", "dataTableAsCsv", "expectedCyclesThatMakeChanges"));

    private static final Pattern CHANGE_SUGGESTING_NAME = Pattern.compile(
            "^(add|change|convert|delete|fix|insert|migrate|move|remove|rename|replace|simplify|update|upgrade|use)(e?s)?([A-Z_0-9].*)?$");
    private static final Pattern NO_CHANGE_SUGGESTING_NAME = Pattern.compile(
            "(^|[a-z0-9_])(Not|No|Nothing|Never|Dont|Doesnt|Keep|Keeps|Retain|Retains|Preserve|Preserves|Ignore|Ignores|Ignored|" +
            "Skip|Skips|Skipped|Leave|Leaves|Unchanged|Already|Noop|Untouched)([A-Z_0-9]|$)|" +
            "^(not|no|nothing|never|dont|doesNot|doNot|keep|keeps|retain|retains|preserve|preserves|ignore|ignores|ignored|" +
            "skip|skips|skipped|leave|leaves|unchanged|already|noop|untouched)([A-Z_0-9]|$)");

    @Getter
    final String displayName = "Group no-change tests in a `@Nested class NoChange`";

    @Getter
    final String description = "Moves `RewriteTest` methods that only assert a recipe makes no changes into a " +
            "`@Nested class NoChange`, placed after the last test that does expect a change. " +
            "Only applies when at least three such tests sit next to at least one test that expects a change, " +
            "or when a `NoChange` class already exists, " +
            "and leaves test classes that already group tests by topic in other nested classes alone.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesMethod<>(REWRITE_RUN),
                        new UsesType<>("org.junit.jupiter.api.Test", false)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        if (!(getCursor().getParentTreeCursor().getValue() instanceof J.CompilationUnit) ||
                            classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class ||
                            classDecl.hasModifier(J.Modifier.Type.Abstract) ||
                            !TypeUtils.isAssignableTo(REWRITE_TEST_FQN, classDecl.getType())) {
                            return classDecl;
                        }

                        J.@Nullable ClassDeclaration existingNoChange = null;
                        for (Statement statement : classDecl.getBody().getStatements()) {
                            if (statement instanceof J.ClassDeclaration) {
                                J.ClassDeclaration nested = (J.ClassDeclaration) statement;
                                if (NESTED_CLASS_NAME.equals(nested.getSimpleName())) {
                                    if (!isNested(nested)) {
                                        return classDecl;
                                    }
                                    existingNoChange = nested;
                                } else if (isNested(nested)) {
                                    return classDecl;
                                }
                            }
                        }

                        List<J.MethodDeclaration> toMove = new ArrayList<>();
                        int changeTests = 0;
                        for (Statement statement : classDecl.getBody().getStatements()) {
                            if (statement instanceof J.MethodDeclaration && isTest((J.MethodDeclaration) statement)) {
                                J.MethodDeclaration test = (J.MethodDeclaration) statement;
                                Expectation expectation = expectation(test);
                                if (expectation == Expectation.CHANGE) {
                                    changeTests++;
                                } else if (expectation == Expectation.NO_CHANGE && isMovable(test)) {
                                    toMove.add(test);
                                }
                            }
                        }
                        if (changeTests == 0 || toMove.isEmpty() || existingNoChange == null && toMove.size() < 3) {
                            return classDecl;
                        }

                        String indent = detectIndent(classDecl);
                        Set<Statement> moved = newSetFromMap(new IdentityHashMap<>());
                        moved.addAll(toMove);
                        List<Statement> remaining = ListUtils.filter(classDecl.getBody().getStatements(), s -> !moved.contains(s));
                        if (existingNoChange != null) {
                            J.ClassDeclaration noChange = existingNoChange;
                            J.ClassDeclaration merged = noChange.withBody(noChange.getBody().withStatements(
                                    ListUtils.concatAll(noChange.getBody().getStatements(), moveInto(noChange, toMove, indent, ctx))));
                            return classDecl.withBody(classDecl.getBody().withStatements(
                                    ListUtils.map(remaining, s -> s == noChange ? merged : s)));
                        }

                        maybeAddImport(NESTED_FQN);
                        J.ClassDeclaration noChange = createNoChangeClass(classDecl, indent);
                        String testIndent = classDecl.getPrefix().getIndent() + indent + indent;
                        noChange = noChange.withBody(noChange.getBody().withStatements(ListUtils.mapFirst(
                                moveInto(noChange, toMove, indent, ctx),
                                first -> first.withPrefix(first.getPrefix().withWhitespace("\n\n" + testIndent)))));
                        int insertAt = 0;
                        for (int i = 0; i < remaining.size(); i++) {
                            if (remaining.get(i) instanceof J.MethodDeclaration && isTest((J.MethodDeclaration) remaining.get(i))) {
                                insertAt = i + 1;
                            }
                        }
                        return classDecl.withBody(classDecl.getBody().withStatements(
                                ListUtils.insert(remaining, noChange, insertAt)));
                    }

                    private List<Statement> moveInto(J.ClassDeclaration noChange, List<J.MethodDeclaration> tests, String indent, ExecutionContext ctx) {
                        List<Statement> moved = new ArrayList<>(tests.size());
                        for (J.MethodDeclaration test : tests) {
                            moved.add(declareIn(shiftRight(test, indent, ctx), noChange.getType()));
                        }
                        return moved;
                    }

                    private J.ClassDeclaration createNoChangeClass(J.ClassDeclaration outer, String indent) {
                        J.ClassDeclaration withNoChange = JavaTemplate.builder("@Nested\nclass " + NESTED_CLASS_NAME + " {\n}")
                                .contextSensitive()
                                .imports(NESTED_FQN)
                                .javaParser(JavaParser.fromJavaVersion()
                                        .dependsOn("package org.junit.jupiter.api; public @interface Nested {}"))
                                .build()
                                .apply(new Cursor(getCursor().getParentOrThrow(), outer),
                                        outer.getBody().getCoordinates().lastStatement());
                        List<Statement> statements = withNoChange.getBody().getStatements();
                        J.ClassDeclaration noChange = (J.ClassDeclaration) statements.get(statements.size() - 1);
                        String memberIndent = outer.getPrefix().getIndent() + indent;
                        J.ClassDeclaration.Kind kind = noChange.getPadding().getKind();
                        return noChange.getPadding().withKind(kind.withPrefix(Space.format("\n" + memberIndent)))
                                .withPrefix(Space.format("\n\n" + memberIndent))
                                .withBody(noChange.getBody().withEnd(Space.format("\n" + memberIndent)));
                    }

                    private Statement declareIn(J.MethodDeclaration test, JavaType.@Nullable FullyQualified noChange) {
                        JavaType.Method methodType = test.getMethodType();
                        if (methodType == null || noChange == null) {
                            return test;
                        }
                        JavaType.Method declared = methodType.withDeclaringType(noChange);
                        return test.withMethodType(declared).withName(test.getName().withType(declared));
                    }
                });
    }

    private static boolean isNested(J.ClassDeclaration classDecl) {
        return classDecl.getLeadingAnnotations().stream().anyMatch(NESTED::matches);
    }

    private static boolean isTest(J.MethodDeclaration method) {
        return method.getLeadingAnnotations().stream().anyMatch(a -> TEST.matches(a) || PARAMETERIZED_TEST.matches(a));
    }

    private static boolean isMovable(J.MethodDeclaration test) {
        for (J.Annotation annotation : test.getLeadingAnnotations()) {
            if (DOCUMENT_EXAMPLE.matches(annotation) || PARAMETERIZED_TEST.matches(annotation) ||
                ENABLED_IF.matches(annotation) || DISABLED_IF.matches(annotation)) {
                return false;
            }
        }
        if (referencesEnclosingInstance(test)) {
            return false;
        }
        String name = test.getSimpleName();
        return NO_CHANGE_SUGGESTING_NAME.matcher(name).find() || !CHANGE_SUGGESTING_NAME.matcher(name).matches();
    }

    private enum Expectation {
        NO_CHANGE, UNKNOWN, CHANGE;

        Expectation and(Expectation other) {
            return compareTo(other) >= 0 ? this : other;
        }
    }

    private static Expectation expectation(J.MethodDeclaration test) {
        if (test.getBody() == null || test.getBody().getStatements().isEmpty()) {
            return Expectation.UNKNOWN;
        }
        Expectation expectation = Expectation.NO_CHANGE;
        for (Statement statement : test.getBody().getStatements()) {
            if (!(statement instanceof J.MethodInvocation) || !REWRITE_RUN.matches((J.MethodInvocation) statement)) {
                return Expectation.UNKNOWN;
            }
            for (Expression argument : ((J.MethodInvocation) statement).getArguments()) {
                if (!(argument instanceof J.Lambda || argument instanceof J.MemberReference)) {
                    expectation = expectation.and(sourceSpecsExpectation(argument));
                }
            }
        }
        if (expectation == Expectation.NO_CHANGE && callsChangeAssertion(test)) {
            return Expectation.UNKNOWN;
        }
        return expectation;
    }

    private static Expectation sourceSpecsExpectation(Expression sourceSpecs) {
        if (!(sourceSpecs instanceof J.MethodInvocation)) {
            return Expectation.UNKNOWN;
        }
        J.MethodInvocation invocation = (J.MethodInvocation) sourceSpecs;
        if (invocation.getMethodType() == null ||
            !TypeUtils.isAssignableTo(SOURCE_SPECS_FQN, invocation.getMethodType().getReturnType()) ||
            !isSourceSpecFactory(invocation.getMethodType().getDeclaringType())) {
            return Expectation.UNKNOWN;
        }

        List<JavaType> parameterTypes = invocation.getMethodType().getParameterTypes();
        Expectation nested = null;
        int strings = 0;
        for (int i = 0; i < invocation.getArguments().size(); i++) {
            Expression argument = invocation.getArguments().get(i);
            if (argument instanceof J.Empty || parameterTypes.isEmpty()) {
                continue;
            }
            JavaType parameterType = parameterTypes.get(Math.min(i, parameterTypes.size() - 1));
            if (parameterType instanceof JavaType.Array) {
                parameterType = ((JavaType.Array) parameterType).getElemType();
            }
            if (TypeUtils.isAssignableTo(SOURCE_SPECS_FQN, parameterType)) {
                Expectation argumentExpectation = sourceSpecsExpectation(argument);
                nested = nested == null ? argumentExpectation : nested.and(argumentExpectation);
            } else if (TypeUtils.isString(parameterType)) {
                strings++;
            } else if (TypeUtils.isOfClassType(parameterType, "java.util.function.UnaryOperator") ||
                       TypeUtils.isOfClassType(parameterType, "java.util.function.Function")) {
                return Expectation.CHANGE;
            }
        }
        if (nested != null) {
            return nested;
        }
        return strings == 1 ? Expectation.NO_CHANGE : strings > 1 ? Expectation.CHANGE : Expectation.UNKNOWN;
    }

    private static boolean isSourceSpecFactory(JavaType.FullyQualified declaringType) {
        String fqn = declaringType.getFullyQualifiedName();
        return fqn.endsWith(".Assertions") || fqn.startsWith("org.openrewrite.test.");
    }

    // Inside the nested class `this`, `super` and `getClass()` would refer to the nested class instead
    private static boolean referencesEnclosingInstance(J.MethodDeclaration test) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                if ("this".equals(identifier.getSimpleName()) || "super".equals(identifier.getSimpleName())) {
                    found.set(true);
                }
                return identifier;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                if (method.getSelect() == null && "getClass".equals(method.getSimpleName())) {
                    found.set(true);
                }
                return super.visitMethodInvocation(method, found);
            }
        }.reduce(test.getBody(), new AtomicBoolean()).get();
    }

    private static boolean callsChangeAssertion(J.MethodDeclaration test) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                if (CHANGE_ASSERTIONS.contains(method.getSimpleName())) {
                    found.set(true);
                    return method;
                }
                return super.visitMethodInvocation(method, found);
            }
        }.reduce(test, new AtomicBoolean()).get();
    }

    // The styles autodetected for some repositories report an indent size that does not match their test classes,
    // so the indent is taken from the members of the class itself instead.
    private static String detectIndent(J.ClassDeclaration classDecl) {
        String outer = classDecl.getPrefix().getIndent();
        for (Statement statement : classDecl.getBody().getStatements()) {
            String member = statement.getPrefix().getIndent();
            if (member.startsWith(outer) && member.length() > outer.length()) {
                return member.substring(outer.length());
            }
        }
        return "    ";
    }

    // Unlike `autoFormat`, shifting every line by the same amount keeps the file's own continuation indent,
    // and unlike `ShiftFormat` it also shifts text blocks and multi-line comments.
    private static J.MethodDeclaration shiftRight(J.MethodDeclaration method, String indent, ExecutionContext ctx) {
        return (J.MethodDeclaration) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public Space visitSpace(@Nullable Space space, Space.Location loc, ExecutionContext ctx) {
                Space s = super.visitSpace(space, loc, ctx);
                if (s == null || s.isEmpty()) {
                    return s;
                }
                return s.withWhitespace(indentLines(s.getWhitespace(), indent))
                        .withComments(ListUtils.map(s.getComments(), comment -> {
                            Comment c = comment.withSuffix(indentLines(comment.getSuffix(), indent));
                            if (c instanceof TextComment && c.isMultiline()) {
                                TextComment text = (TextComment) c;
                                c = text.withText(indentLines(text.getText(), indent));
                            }
                            return c;
                        }));
            }

            @Override
            protected JavadocVisitor<ExecutionContext> getJavadocVisitor() {
                return new JavadocVisitor<ExecutionContext>(this) {
                    @Override
                    public Javadoc visitLineBreak(Javadoc.LineBreak lineBreak, ExecutionContext ctx) {
                        return lineBreak.withMargin(indentLines(lineBreak.getMargin(), indent));
                    }
                };
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (l.getValueSource() != null && l.getValueSource().startsWith("\"\"\"")) {
                    return l.withValueSource(indentLines(l.getValueSource(), indent));
                }
                return l;
            }
        }.visitNonNull(method, ctx);
    }

    // Blank lines stay as they are, so that text block values remain the same, but the indent before a token is shifted
    private static String indentLines(String text, String indent) {
        if (text.indexOf('\n') < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + indent.length() * 4);
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                String line = text.substring(lineStart, i);
                if (lineStart > 0 && (i == text.length() || !StringUtils.isBlank(line))) {
                    sb.append(indent);
                }
                sb.append(line);
                if (i < text.length()) {
                    sb.append('\n');
                }
                lineStart = i + 1;
            }
        }
        return sb.toString();
    }
}
