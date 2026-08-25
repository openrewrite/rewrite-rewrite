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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.format.BlankLinesVisitor;
import org.openrewrite.java.format.ShiftFormat;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.java.style.TabsAndIndentsStyle;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.style.Style;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;

@Value
@EqualsAndHashCode(callSuper = false)
public class InlineNestedVisitorClass extends Recipe {
    private static final String RECIPE = "org.openrewrite.Recipe";
    private static final String TREE_VISITOR = "org.openrewrite.TreeVisitor";

    String displayName = "Inline nested visitor classes into the returning method";

    String description = "Recipes that return a named, private, static nested visitor class straight from " +
            "`getVisitor()` (or `getScanner()`) can declare that visitor anonymously instead, which keeps the " +
            "visitor next to the recipe metadata that configures it. Any `private static final` constants the " +
            "nested class declares are hoisted onto the recipe class, as anonymous classes can not declare them. " +
            "Only applied when the nested class is used exactly once, and when nothing would be lost by inlining it.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(RECIPE, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                List<Statement> statements = cd.getBody().getStatements();
                if (statements.stream().noneMatch(J.ClassDeclaration.class::isInstance) ||
                        !TypeUtils.isAssignableTo(RECIPE, cd.getType())) {
                    return cd;
                }

                boolean recipeIsTopLevel = getCursor().getParentTreeCursor().getValue() instanceof J.CompilationUnit;
                List<J.ClassDeclaration> inlined = new ArrayList<>();
                List<Statement> hoisted = new ArrayList<>();
                List<Statement> mapped = ListUtils.map(statements, statement -> {
                    if (!(statement instanceof J.MethodDeclaration)) {
                        return statement;
                    }
                    J.MethodDeclaration method = (J.MethodDeclaration) statement;
                    J.NewClass newClass = soleReturnedNewClass(method);
                    if (newClass == null) {
                        return statement;
                    }
                    J.ClassDeclaration nested = findNestedClass(statements, newClass);
                    if (nested == null || !canInline(nested)) {
                        return statement;
                    }
                    TypeTree supertype = supertypeOf(nested);
                    if (supertype == null) {
                        return statement;
                    }
                    // Anonymous classes can not declare constants, so they move up onto the recipe class instead
                    List<Statement> constants = constants(nested);
                    if (!constants.isEmpty() && (!recipeIsTopLevel || collidesWithExistingField(statements, constants))) {
                        return statement;
                    }
                    if (!isReferencedExactlyOnce(nested)) {
                        return statement;
                    }

                    Cursor cursor = getCursor();
                    J.Block body = nested.getBody();
                    if (!constants.isEmpty()) {
                        boolean leading = constants.contains(body.getStatements().get(0));
                        List<Statement> kept = ListUtils.filter(body.getStatements(), s -> !constants.contains(s));
                        if (leading) {
                            kept = ListUtils.mapFirst(kept, first -> first.withPrefix(withoutBlankLines(first.getPrefix())));
                        }
                        body = body.withStatements(kept);
                        for (Statement constant : constants) {
                            hoisted.add(shiftIndent(constant, cursor, -1));
                        }
                    }

                    inlined.add(nested);
                    J.Block anonymousBody = shiftIndent(body, cursor, 1);
                    return (Statement) new JavaIsoVisitor<Integer>() {
                        @Override
                        public J.NewClass visitNewClass(J.NewClass nc, Integer p) {
                            if (nc != newClass) {
                                return super.visitNewClass(nc, p);
                            }
                            return nc
                                    .withClazz(supertype.withPrefix(Space.SINGLE_SPACE))
                                    .withBody(anonymousBody);
                        }
                    }.visitNonNull(method, 0);
                });

                if (inlined.isEmpty()) {
                    return cd;
                }
                List<Statement> remaining = ListUtils.filter(mapped, statement -> !inlined.contains(statement));
                if (inlined.contains(statements.get(0))) {
                    Space prefix = statements.get(0).getPrefix();
                    remaining = ListUtils.mapFirst(remaining, first -> first.withPrefix(prefix));
                }
                if (!hoisted.isEmpty() && !remaining.isEmpty()) {
                    remaining = insertConstants(remaining, hoisted);
                }
                return cd.withBody(cd.getBody().withStatements(remaining));
            }

            private List<Statement> insertConstants(List<Statement> statements, List<Statement> constants) {
                // Constants must come after any existing field they refer to, or they forward reference it
                int at = insertionPoint(statements, constants);
                boolean append = at == statements.size();
                Space prefix = statements.get(append ? at - 1 : at).getPrefix();
                return ListUtils.insertAll(
                        append ? statements : ListUtils.map(statements, (i, statement) ->
                                i == at ? statement.withPrefix(blankLineBefore(prefix)) : statement),
                        at,
                        ListUtils.mapFirst(constants, first -> first.withPrefix(append ? blankLineBefore(prefix) : prefix)));
            }

            private int insertionPoint(List<Statement> statements, List<Statement> constants) {
                // Conservatively, any identifier sharing a name with a field counts as referring to it
                Set<String> referenced = new JavaIsoVisitor<Set<String>>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> names) {
                        names.add(identifier.getSimpleName());
                        return identifier;
                    }
                }.reduce(constants, new HashSet<>());
                for (int i = statements.size() - 1; i >= 0; i--) {
                    if (fieldNames(statements.get(i)).anyMatch(referenced::contains)) {
                        return i + 1;
                    }
                }
                return 0;
            }

            private Stream<String> fieldNames(Statement statement) {
                if (!(statement instanceof J.VariableDeclarations)) {
                    return Stream.empty();
                }
                return ((J.VariableDeclarations) statement).getVariables().stream()
                        .map(J.VariableDeclarations.NamedVariable::getSimpleName);
            }

            private J.@Nullable NewClass soleReturnedNewClass(J.MethodDeclaration method) {
                if (method.getBody() == null || method.getBody().getStatements().size() != 1) {
                    return null;
                }
                Statement statement = method.getBody().getStatements().get(0);
                if (!(statement instanceof J.Return)) {
                    return null;
                }
                Expression returned = ((J.Return) statement).getExpression();
                if (!(returned instanceof J.NewClass)) {
                    return null;
                }
                J.NewClass newClass = (J.NewClass) returned;
                List<Expression> arguments = newClass.getArguments();
                if (newClass.getBody() != null ||
                        !(arguments.isEmpty() || arguments.size() == 1 && arguments.get(0) instanceof J.Empty) ||
                        !TypeUtils.isAssignableTo(TREE_VISITOR, method.getMethodType() == null ? null : method.getMethodType().getReturnType())) {
                    return null;
                }
                return newClass;
            }

            private J.@Nullable ClassDeclaration findNestedClass(List<Statement> statements, J.NewClass newClass) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(newClass.getType());
                if (type == null && newClass.getClazz() != null) {
                    type = TypeUtils.asFullyQualified(newClass.getClazz().getType());
                }
                if (type == null) {
                    return null;
                }
                for (Statement statement : statements) {
                    if (statement instanceof J.ClassDeclaration &&
                            TypeUtils.isOfType(type, ((J.ClassDeclaration) statement).getType())) {
                        return (J.ClassDeclaration) statement;
                    }
                }
                return null;
            }

            private boolean canInline(J.ClassDeclaration nested) {
                if (!nested.hasModifier(J.Modifier.Type.Private) ||
                        !nested.hasModifier(J.Modifier.Type.Static) ||
                        nested.hasModifier(J.Modifier.Type.Abstract) ||
                        nested.getKind() != J.ClassDeclaration.Kind.Type.Class ||
                        nested.getTypeParameters() != null ||
                        !nested.getLeadingAnnotations().isEmpty() ||
                        !nested.getPrefix().getComments().isEmpty()) {
                    return false;
                }
                // Anonymous classes cannot declare constructors, and before Java 16 no static members either
                for (Statement statement : nested.getBody().getStatements()) {
                    if (statement instanceof J.Block || statement instanceof J.ClassDeclaration) {
                        return false;
                    }
                    if (statement instanceof J.MethodDeclaration) {
                        J.MethodDeclaration method = (J.MethodDeclaration) statement;
                        if (method.isConstructor() || method.hasModifier(J.Modifier.Type.Static)) {
                            return false;
                        }
                    } else if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations field = (J.VariableDeclarations) statement;
                        if (field.hasModifier(J.Modifier.Type.Static) && !isConstant(field)) {
                            return false;
                        }
                    }
                }
                return true;
            }

            private boolean isConstant(J.VariableDeclarations field) {
                return field.hasModifier(J.Modifier.Type.Private) &&
                        field.hasModifier(J.Modifier.Type.Static) &&
                        field.hasModifier(J.Modifier.Type.Final);
            }

            private List<Statement> constants(J.ClassDeclaration nested) {
                return ListUtils.filter(nested.getBody().getStatements(), statement ->
                        statement instanceof J.VariableDeclarations && isConstant((J.VariableDeclarations) statement));
            }

            private boolean collidesWithExistingField(List<Statement> statements, List<Statement> constants) {
                Set<String> existing = statements.stream().flatMap(this::fieldNames).collect(toSet());
                return constants.stream().flatMap(this::fieldNames).anyMatch(name -> !existing.add(name));
            }

            private boolean isReferencedExactlyOnce(J.ClassDeclaration nested) {
                JavaType.FullyQualified type = nested.getType();
                if (type == null) {
                    return false;
                }
                return new JavaIsoVisitor<AtomicInteger>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicInteger count) {
                        if (nested.getSimpleName().equals(identifier.getSimpleName()) &&
                                !identifier.getId().equals(nested.getName().getId()) &&
                                TypeUtils.isOfType(type, identifier.getType())) {
                            count.incrementAndGet();
                        }
                        return identifier;
                    }
                }.reduce(getCursor().firstEnclosingOrThrow(JavaSourceFile.class), new AtomicInteger()).get() == 1;
            }

            private <J2 extends J> J2 shiftIndent(J2 tree, Cursor cursor, int levels) {
                J2 shifted = ShiftFormat.indent(tree, cursor, levels);
                JavaSourceFile cu = cursor.firstEnclosingOrThrow(JavaSourceFile.class);
                TabsAndIndentsStyle style = Style.from(TabsAndIndentsStyle.class, cu, IntelliJ::tabsAndIndents);
                String indent = style.getUseTabCharacter() ? "\t" : StringUtils.repeat(" ", style.getIndentSize());
                //noinspection unchecked
                return (J2) new JavaIsoVisitor<Integer>() {
                    @Override
                    public Space visitSpace(Space space, Space.Location loc, Integer p) {
                        return space.withComments(ListUtils.map(space.getComments(), comment -> shiftComment(comment, indent, levels)));
                    }
                }.visitNonNull(shifted, 0);
            }

            private Comment shiftComment(Comment comment, String indent, int levels) {
                if (comment instanceof TextComment) {
                    TextComment textComment = (TextComment) comment;
                    return textComment.withText(levels > 0 ?
                            textComment.getText().replace("\n", "\n" + indent) :
                            textComment.getText().replace("\n" + indent, "\n"));
                }
                if (comment instanceof Javadoc.DocComment) {
                    Javadoc.DocComment docComment = (Javadoc.DocComment) comment;
                    return docComment.withBody(ListUtils.map(docComment.getBody(), doc -> shiftJavadoc(doc, indent, levels)));
                }
                return comment;
            }

            private Javadoc shiftJavadoc(Javadoc doc, String indent, int levels) {
                if (!(doc instanceof Javadoc.LineBreak)) {
                    return doc;
                }
                String margin = ((Javadoc.LineBreak) doc).getMargin();
                // The margin closing a doc comment is all whitespace, with no `*` after it
                int i = StringUtils.indexOfNonWhitespace(margin);
                String whitespace = i < 0 ? margin : margin.substring(0, i);
                if (levels > 0) {
                    whitespace += indent;
                } else if (whitespace.endsWith(indent)) {
                    whitespace = whitespace.substring(0, whitespace.length() - indent.length());
                }
                return ((Javadoc.LineBreak) doc).withMargin(whitespace + (i < 0 ? "" : margin.substring(i)));
            }

            private Space withoutBlankLines(Space prefix) {
                String whitespace = prefix.getWhitespace();
                int last = whitespace.lastIndexOf('\n');
                return last < 0 ? prefix : prefix.withWhitespace(whitespace.substring(last));
            }

            private Space blankLineBefore(Space prefix) {
                return prefix.withWhitespace(BlankLinesVisitor.minimumLines(prefix.getWhitespace(), 1));
            }

            private @Nullable TypeTree supertypeOf(J.ClassDeclaration nested) {
                // An anonymous class has exactly one supertype, so anything more would be lost
                List<TypeTree> implemented = nested.getImplements() == null ? emptyList() : nested.getImplements();
                if (nested.getExtends() != null) {
                    return implemented.isEmpty() ? nested.getExtends() : null;
                }
                return implemented.size() == 1 ? implemented.get(0) : null;
            }
        });
    }
}
