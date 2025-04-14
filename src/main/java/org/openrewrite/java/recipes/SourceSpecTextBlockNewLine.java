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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

import java.util.List;

public class SourceSpecTextBlockNewLine extends Recipe {
    @Override
    public String getDisplayName() {
        return "New line at the end of `SourceSpecs` text blocks";
    }

    @Override
    public String getDescription() {
        return "Text blocks that assert before and after source code should have a new line after it is closed.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (method.getMethodType() != null && TypeUtils.isOfClassType(method.getMethodType().getReturnType(),
                        "org.openrewrite.test.SourceSpecs")) {

                    J.MethodInvocation.Padding methodPadding = method.getPadding();
                    JContainer<Expression> arguments = methodPadding.getArguments();
                    JContainer.Padding<Expression> argumentsPadding = arguments.getPadding();
                    List<JRightPadded<Expression>> elements = argumentsPadding.getElements();

                    // Add new lines to the beginning of the text blocks and arguments that follow a text block
                    List<JRightPadded<Expression>> formattedElements = ListUtils.map(elements, (i, jrp) -> {
                        Expression argument = jrp.getElement();
                        boolean isCurrentLiteral = argument instanceof J.Literal;
                        boolean isPreviousLiteral = i > 0 && elements.get(i - 1).getElement() instanceof J.Literal;

                        if ((isCurrentLiteral || isPreviousLiteral) &&
                                argument.getPrefix().getComments().isEmpty() &&
                                !argument.getPrefix().getWhitespace().startsWith("\n")
                        ) {
                            Expression formatted = argument.withPrefix(Space.format("\n"));
                            formatted = maybeAutoFormat(argument, formatted, ctx);
                            return jrp.withElement(formatted);
                        }
                        return jrp;
                    });

                    // Add newline to the last element of a text block
                    formattedElements = ListUtils.mapLast(formattedElements, jrp -> {
                        Expression argument = jrp.getElement();
                        if (argument instanceof J.Literal && !jrp.getAfter().getWhitespace().startsWith("\n")) {
                            return jrp.withAfter(Space.format(method.getPrefix().getWhitespace()));
                        }
                        return jrp;
                    });

                    return methodPadding.withArguments(argumentsPadding.withElements(formattedElements));
                }
                return super.visitMethodInvocation(method, ctx);
            }
        };
    }
}
