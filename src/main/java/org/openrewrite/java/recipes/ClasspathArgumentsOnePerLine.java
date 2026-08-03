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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.java.style.TabsAndIndentsStyle;
import org.openrewrite.java.tree.*;
import org.openrewrite.style.Style;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class ClasspathArgumentsOnePerLine extends Recipe {

    private static final MethodMatcher CLASSPATH_METHODS =
            new MethodMatcher("org.openrewrite.java.JavaParser.Builder classpath*(..)", true);

    private static final int MINIMUM_ARGUMENTS = 4;

    @Getter
    final String displayName = "One classpath argument per line";

    @Getter
    final String description = "Put each argument of `classpath` and `classpathFromResources` on its own line when there are four or more arguments, " +
            "such that classpath entries are easier to read, and adding or removing an entry shows up as a single line change.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(CLASSPATH_METHODS), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (!CLASSPATH_METHODS.matches(m) || m.getArguments().size() < MINIMUM_ARGUMENTS) {
                    return m;
                }

                Space argumentPrefix = Space.format("\n" + argumentIndent(m));
                boolean keepExecutionContextInline = firstParameterIsExecutionContext(m);
                return m.withArguments(ListUtils.map(m.getArguments(), (i, argument) -> {
                    if (i == 0 && keepExecutionContextInline ||
                            argument.getPrefix().getWhitespace().contains("\n") ||
                            !argument.getPrefix().getComments().isEmpty()) {
                        return argument;
                    }
                    return argument.withPrefix(argumentPrefix);
                }));
            }

            private boolean firstParameterIsExecutionContext(J.MethodInvocation m) {
                JavaType.Method methodType = m.getMethodType();
                return methodType != null && !methodType.getParameterTypes().isEmpty() &&
                        TypeUtils.isAssignableTo("org.openrewrite.ExecutionContext", methodType.getParameterTypes().get(0));
            }

            /**
             * Prefer the indentation of arguments already on their own line, then the indentation of the line the method
             * name is on plus however much that line is itself indented over the line above it, as neither the styles nor
             * `autoFormat` reliably reproduce the indentation used for classpath arguments today.
             */
            private String argumentIndent(J.MethodInvocation m) {
                for (Expression argument : m.getArguments()) {
                    String argumentIndent = indentAfterNewLine(argument.getPrefix().getWhitespace());
                    if (argumentIndent != null) {
                        return argumentIndent;
                    }
                }

                List<String> lineIndents = enclosingLineIndents();
                if (lineIndents.isEmpty()) {
                    return continuationIndent();
                }
                String lineIndent = lineIndents.get(0);
                if (lineIndents.size() == 2 && lineIndent.startsWith(lineIndents.get(1)) && lineIndent.length() > lineIndents.get(1).length()) {
                    return lineIndent + lineIndent.substring(lineIndents.get(1).length());
                }
                return lineIndent + continuationIndent();
            }

            private List<String> enclosingLineIndents() {
                List<String> lineIndents = new ArrayList<>();
                J child = null;
                for (Cursor c = getCursor(); c.getValue() instanceof J && lineIndents.size() < 2; c = c.getParentTreeCursor()) {
                    J j = c.getValue();
                    for (String whitespace : precedingWhitespace(j, child)) {
                        String lineIndent = indentAfterNewLine(whitespace);
                        if (lineIndent != null && lineIndents.add(lineIndent) && lineIndents.size() == 2) {
                            break;
                        }
                    }
                    if (c.getParentTreeCursor().getValue() instanceof J.Block) {
                        // Beyond the enclosing statement indentation reflects nesting rather than a continuation
                        break;
                    }
                    child = j;
                }
                return lineIndents;
            }

            private List<String> precedingWhitespace(J j, @Nullable J child) {
                if (j instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) j;
                    JRightPadded<Expression> select = mi.getPadding().getSelect();
                    if (select != null && select.getElement() != child) {
                        return asList(
                                mi.getName().getPrefix().getWhitespace(),
                                select.getAfter().getWhitespace(),
                                mi.getPrefix().getWhitespace());
                    }
                }
                return singletonList(j.getPrefix().getWhitespace());
            }

            private @Nullable String indentAfterNewLine(String whitespace) {
                int newLine = whitespace.lastIndexOf('\n');
                return newLine == -1 ? null : whitespace.substring(newLine + 1);
            }

            private String continuationIndent() {
                TabsAndIndentsStyle style = Style.from(TabsAndIndentsStyle.class,
                        getCursor().firstEnclosingOrThrow(JavaSourceFile.class), IntelliJ::tabsAndIndents);
                return style.getUseTabCharacter() ? "\t" : StringUtils.repeat(" ", style.getContinuationIndent());
            }
        });
    }
}
