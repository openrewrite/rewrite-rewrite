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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class CorrectlySpacedDescriptions extends Recipe {

    private static final String ENDS_WITH_LINEBREAK = "[\\s\\S]*\\n+\\s*$";
    private static final String IS_ONLY_WHITESPACE = "^\\s*$";
    private static final String IS_MAYBE_MD_LIST = "^\\s?[-*]\\s\\S[\\s\\S]*";
    private static final String IS_MAYBE_END_OF_MD_LINK = ".*]$";

    private static final MethodMatcher GET_DESCRIPTION_MATCHER = new MethodMatcher("org.openrewrite.Recipe getDescription()", true);

    @Getter
    final String displayName = "Correctly spaced descriptions";

    @Getter
    final String description = "Recipe descriptions should be cleanly formatted. This recipe forces correct spacing in multiline descriptions. " +
            "In a multi line description the lines should not start with whitespace and end with a single space " +
            "except for the last line which should end with a \".\" " +
            "(e.g.\n```\n  return \"This is a correct \" + \n   \"multi line description\";\n```\n).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (GET_DESCRIPTION_MATCHER.matches(method.getMethodType())) {
                    return (J.MethodDeclaration) new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
                            J.Binary b = super.visitBinary(binary, ctx);
                            // The left prefix is only modified when it's the first element of the J.Binary
                            if (b.getLeft() instanceof J.Literal) {
                                b = maybeFormatLeftPrefix(b);
                            }
                            b = maybeFormatLeftSuffix(b);
                            b = maybeFormatRightPrefix(b);
                            // The right suffix is only modified when it's tha last element of the J.Binary
                            if (getCursor().getParentTreeCursor().getValue() instanceof J.Return) {
                                b = maybeFormatRightSuffix(b);
                            }
                            return b;
                        }
                    }.visitNonNull(method, ctx, getCursor().getParentOrThrow());
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            private J.Binary maybeFormatLeftPrefix(J.Binary b) {
                return b.withLeft(maybeFormatLiteralPrefix((J.Literal) b.getLeft()));
            }

            private J.Binary maybeFormatLeftSuffix(J.Binary b) {
                if (b.getLeft() instanceof J.Literal && b.getRight() instanceof J.Literal) {
                    return b.withLeft(maybeFormatLiteralSuffix((J.Literal) b.getLeft()));
                }
                if (b.getLeft() instanceof J.Binary && b.getRight() instanceof J.Literal) {
                    J.Binary left = (J.Binary) b.getLeft();
                    Expression right = left.getRight();
                    if (right instanceof J.Literal) {
                        return b.withLeft(left.withRight(maybeFormatLiteralSuffix((J.Literal) right)));
                    }
                }
                return b;
            }

            private J.Binary maybeFormatRightPrefix(J.Binary b) {
                boolean leftElementIsLiteral = b.getLeft() instanceof J.Literal ||
                      (b.getLeft() instanceof J.Binary &&
                            ((J.Binary) b.getLeft()).getRight() instanceof J.Literal);
                if (leftElementIsLiteral && b.getRight() instanceof J.Literal) {
                    return b.withRight(maybeFormatLiteralPrefix((J.Literal) b.getRight()));
                }
                return b;
            }

            private J.Binary maybeFormatRightSuffix(J.Binary b) {
                if (b.getRight() instanceof J.Literal) {
                    return b.withRight(maybeFormatLiteralSuffix((J.Literal) b.getRight(), true));
                }
                return b;
            }

            private J.Literal maybeFormatLiteralPrefix(J.Literal l) {
                if (l.getValue() instanceof String) {
                    String value = (String) l.getValue();
                    if (value.matches(IS_ONLY_WHITESPACE)) {
                        return l;
                    }
                    value = value.replaceAll("^\\h+", "");
                    if (value.matches(IS_MAYBE_MD_LIST)) {
                        value = " " + value;
                    }
                    if (!value.equals(l.getValue())) {
                        String valueSource = value.replace("\"", "\\\"");
                        valueSource = valueSource.replaceAll("\\n", "\\\\n");
                        return l.withValue(value).withValueSource("\"" + valueSource + "\"");
                    }
                }
                return l;
            }

            private J.Literal maybeFormatLiteralSuffix(J.Literal l) {
                return maybeFormatLiteralSuffix(l, false);
            }

            private J.Literal maybeFormatLiteralSuffix(J.Literal l, boolean isLastLine) {
                if (l.getValue() instanceof String) {
                    String value = (String) l.getValue();
                    if (value.matches(IS_ONLY_WHITESPACE) || value.matches(IS_MAYBE_END_OF_MD_LINK)) {
                        return l;
                    }
                    if (value.matches(IS_MAYBE_MD_LIST)) {
                        if (value.matches(ENDS_WITH_LINEBREAK)) {
                            value = value.substring(0, value.lastIndexOf("\n") + 1);
                        } else {
                            value = value.replaceAll("\\h*$", "");
                            value += isLastLine ? "" : "\n";
                        }
                    } else {
                        if (value.matches(ENDS_WITH_LINEBREAK)) {
                            value = value.substring(0, value.lastIndexOf("\n") + 1);
                        } else {
                            value = value.replaceAll("\\h*$", "");
                            value += isLastLine ? "" : " ";
                        }
                    }
                    if (!value.equals(l.getValue())) {
                        String valueSource = value.replace("\"", "\\\"");
                        valueSource = valueSource.replaceAll("\\n", "\\\\n");
                        return l.withValue(value).withValueSource("\"" + valueSource + "\"");
                    }
                }
                return l;
            }
        };
        return Preconditions.check(new DeclaresMethod<>(GET_DESCRIPTION_MATCHER), visitor);
    }
}
