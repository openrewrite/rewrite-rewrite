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
import org.openrewrite.java.format.ShiftFormat;
import org.openrewrite.java.search.DeclaresType;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Value
@EqualsAndHashCode(callSuper = false)
public class InlineNestedVisitorClass extends Recipe {
    private static final String RECIPE = "org.openrewrite.Recipe";
    private static final String TREE_VISITOR = "org.openrewrite.TreeVisitor";

    String displayName = "Inline nested visitor classes into the returning method";

    String description = "Recipes that return a named, private, static nested visitor class straight from " +
            "`getVisitor()` (or `getScanner()`) can declare that visitor anonymously instead, which keeps the " +
            "visitor next to the recipe metadata that configures it. Only applied when the nested class is used " +
            "exactly once, and when nothing would be lost by inlining it.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new DeclaresType<>(RECIPE, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                List<Statement> statements = cd.getBody().getStatements();
                if (!declaresNestedClass(statements) || !TypeUtils.isAssignableTo(RECIPE, cd.getType())) {
                    return cd;
                }

                List<J.ClassDeclaration> inlined = new ArrayList<>();
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
                    inlined.add(nested);
                    Cursor cursor = getCursor();
                    return (Statement) new JavaIsoVisitor<Integer>() {
                        @Override
                        public J.NewClass visitNewClass(J.NewClass nc, Integer p) {
                            if (nc != newClass) {
                                return super.visitNewClass(nc, p);
                            }
                            return nc
                                    .withClazz(supertype.withPrefix(Space.SINGLE_SPACE))
                                    .withBody(indentOneLevel(nested.getBody(), cursor));
                        }
                    }.visitNonNull(method, 0);
                });

                if (inlined.isEmpty()) {
                    return cd;
                }
                List<Statement> remaining = ListUtils.map(mapped, statement -> inlined.contains(statement) ? null : statement);
                if (inlined.contains(statements.get(0))) {
                    Space prefix = statements.get(0).getPrefix();
                    remaining = ListUtils.mapFirst(remaining, first -> first.withPrefix(prefix));
                }
                return cd.withBody(cd.getBody().withStatements(remaining));
            }

            private boolean declaresNestedClass(List<Statement> statements) {
                for (Statement statement : statements) {
                    if (statement instanceof J.ClassDeclaration) {
                        return true;
                    }
                }
                return false;
            }

            private J.@Nullable NewClass soleReturnedNewClass(J.MethodDeclaration method) {
                if (method.getBody() == null ||
                        method.getBody().getStatements().size() != 1 ||
                        !TypeUtils.isAssignableTo(TREE_VISITOR, method.getMethodType() == null ? null : method.getMethodType().getReturnType())) {
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
                        !(arguments.isEmpty() || arguments.size() == 1 && arguments.get(0) instanceof J.Empty)) {
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
                    if (statement instanceof J.Block ||
                            statement instanceof J.ClassDeclaration ||
                            statement instanceof J.MethodDeclaration && ((J.MethodDeclaration) statement).isConstructor()) {
                        return false;
                    }
                    if (statement instanceof J.MethodDeclaration &&
                            ((J.MethodDeclaration) statement).hasModifier(J.Modifier.Type.Static)) {
                        return false;
                    }
                    if (statement instanceof J.VariableDeclarations &&
                            ((J.VariableDeclarations) statement).hasModifier(J.Modifier.Type.Static)) {
                        return false;
                    }
                }
                return countReferences(nested) == 1;
            }

            private int countReferences(J.ClassDeclaration nested) {
                JavaType.FullyQualified type = nested.getType();
                if (type == null) {
                    return -1;
                }
                AtomicInteger references = new AtomicInteger();
                new JavaIsoVisitor<AtomicInteger>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicInteger count) {
                        if (!identifier.getId().equals(nested.getName().getId()) &&
                                TypeUtils.isOfType(type, identifier.getType())) {
                            count.incrementAndGet();
                        }
                        return identifier;
                    }
                }.visit(getCursor().firstEnclosingOrThrow(JavaSourceFile.class), references);
                return references.get();
            }

            private J.Block indentOneLevel(J.Block body, Cursor cursor) {
                J.Block shifted = ShiftFormat.indent(body, cursor, 1);
                JavaSourceFile cu = cursor.firstEnclosingOrThrow(JavaSourceFile.class);
                TabsAndIndentsStyle style = Style.from(TabsAndIndentsStyle.class, cu, IntelliJ::tabsAndIndents);
                String indent = style.getUseTabCharacter() ? "\t" : StringUtils.repeat(" ", style.getIndentSize());
                return (J.Block) new JavaIsoVisitor<Integer>() {
                    @Override
                    public Space visitSpace(Space space, Space.Location loc, Integer p) {
                        return space.withComments(ListUtils.map(space.getComments(), comment -> indentComment(comment, indent)));
                    }
                }.visitNonNull(shifted, 0);
            }

            private Comment indentComment(Comment comment, String indent) {
                if (comment instanceof TextComment) {
                    TextComment textComment = (TextComment) comment;
                    if (textComment.getText().contains("\n")) {
                        return textComment.withText(textComment.getText().replace("\n", "\n" + indent));
                    }
                } else if (comment instanceof Javadoc.DocComment) {
                    Javadoc.DocComment docComment = (Javadoc.DocComment) comment;
                    return docComment.withBody(ListUtils.map(docComment.getBody(), doc -> indentJavadoc(doc, indent)));
                }
                return comment;
            }

            private Javadoc indentJavadoc(Javadoc doc, String indent) {
                if (!(doc instanceof Javadoc.LineBreak)) {
                    return doc;
                }
                String margin = ((Javadoc.LineBreak) doc).getMargin();
                int i = 0;
                while (i < margin.length() && Character.isWhitespace(margin.charAt(i))) {
                    i++;
                }
                return ((Javadoc.LineBreak) doc).withMargin(margin.substring(0, i) + indent + margin.substring(i));
            }

            private @Nullable TypeTree supertypeOf(J.ClassDeclaration nested) {
                if (nested.getExtends() != null) {
                    return nested.getExtends();
                }
                if (nested.getImplements() != null && nested.getImplements().size() == 1) {
                    return nested.getImplements().get(0);
                }
                return null;
            }
        });
    }
}
