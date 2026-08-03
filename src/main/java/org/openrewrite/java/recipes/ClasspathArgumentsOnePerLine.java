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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.style.Style;

import java.util.Iterator;

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

                // Not `autoFormat`, as that aligns arguments with the method call itself, and leaves partly split calls ragged
                Space argumentPrefix = Space.format("\n" + indentOfMethodName(m) + continuationIndent());
                boolean keepExecutionContextInline = TypeUtils.isAssignableTo("org.openrewrite.ExecutionContext",
                        m.getArguments().get(0).getType());
                return m.withArguments(ListUtils.map(m.getArguments(), (i, argument) -> {
                    if (i == 0 && keepExecutionContextInline ||
                            argument.getPrefix().getWhitespace().contains("\n") ||
                            !argument.getPrefix().getComments().isEmpty()) {
                        return argument;
                    }
                    return argument.withPrefix(argumentPrefix);
                }));
            }

            private String indentOfMethodName(J.MethodInvocation m) {
                String indent = indentAfterNewLine(m.getName().getPrefix().getWhitespace());
                if (indent == null && m.getPadding().getSelect() != null) {
                    indent = indentAfterNewLine(m.getPadding().getSelect().getAfter().getWhitespace());
                }
                for (Iterator<Object> path = getCursor().getPath(J.class::isInstance); indent == null && path.hasNext(); ) {
                    indent = indentAfterNewLine(((J) path.next()).getPrefix().getWhitespace());
                }
                return indent == null ? "" : indent;
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
