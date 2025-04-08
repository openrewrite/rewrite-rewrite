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
package org.openrewrite.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class CorrectlySpacedDescriptions extends Recipe {

  private static final String STARTS_AND_ENDS_WITH_NON_WHITESPACE_CHAR = "^\\S.*\\S$";
  private static final String STARTS_WITH_NON_WHITESPACE_CHAR_ENDS_WITH_SPACE = "^\\S.*\\S $";
  private static final String STARTS_WITH_NON_WHITESPACE_CHAR_ENDS_WITH_LINEBREAK = "^\\S.*\\s*\\n+\\s*$";
  private static final String ENDS_WITH_LINEBREAK = "[\\s\\S]*\\n+\\s*$";
  private static final String IS_ONLY_WHITESPACE = "^\\s*$";
  private static final String IS_CORRECTLY_SPACED_MAYBE_MD_LIST = "^\\s[-*]\\s\\S*\\n$";
  private static final String IS_MAYBE_MD_LIST = "^\\s?[-*]\\s\\S[\\s\\S]*";
  private static final String IS_MAYBE_END_OF_MD_LINK = ".*]$";

  private static final MethodMatcher GET_DESCRIPTION_MATCHER = new MethodMatcher("org.openrewrite.Recipe getDescription()", true);

  @Override
  public String getDisplayName() {
    return "Correctly spaced descriptions";
  }

  @Override
  public String getDescription() {
    return "Recipe descriptions should be cleanly formatted. This recipe forces correct spacing in multiline descriptions. " +
      "In a multi line description the lines should not start with whitespace and end with a single space " +
      "except for the last line which should end with a \".\" " +
      "(e.g.\n  ```return \"This is a correct \" + \n   \"multi line description\";```).";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
      @Override
      public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        if (GET_DESCRIPTION_MATCHER.matches(method.getMethodType())) {
          return (J.MethodDeclaration) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Binary visitBinary(J.Binary b, ExecutionContext ctx) {
              if (isLiteralString(b.getLeft())) {
                return handle(b).withRight(updateLiteral((J.Literal) b.getRight(), false));
              } else {
                return handle(b);
              }
            }
          }.visitNonNull(method, ctx, getCursor().getParentOrThrow());
        }
        return super.visitMethodDeclaration(method, ctx);
      }

      private J.Binary handle(J.Binary b) {
        Expression l = b.getLeft();
        if (l instanceof J.Binary && isLiteralString(b.getRight())) {
          J.Binary lb = handle((J.Binary) l);
          if (lb.getRight() instanceof J.Literal && isLiteralString(lb.getLeft())) {
            J.Literal lr = (J.Literal) lb.getRight();
            lb = lb.withRight(updateLiteral(lr, true));
          }
          return b.withLeft(lb);
        } else if (l instanceof J.Literal && isLiteralString(b.getRight())) {
          return b.withLeft(updateLiteral((J.Literal) l, true));
        } else {
          return b;
        }
      }

      private boolean isLiteralString(Expression exp) {
        if (exp instanceof J.Literal) {
          return ((J.Literal) exp).getValue() instanceof String;
        }
        if (exp instanceof J.Binary) {
          return isLiteralString(((J.Binary) exp).getRight());
        }
        return false;
      }

      private J.Literal updateLiteral(J.Literal expression, boolean endWithWhiteSpace) {
        if (expression.getValue() instanceof String) {
          String value = (String) expression.getValue();
          boolean matchesEndsWithWhitespaceTemplate = value.matches(STARTS_WITH_NON_WHITESPACE_CHAR_ENDS_WITH_SPACE) ||
            value.matches(STARTS_WITH_NON_WHITESPACE_CHAR_ENDS_WITH_LINEBREAK);
          if (value.matches(IS_ONLY_WHITESPACE) || value.matches(IS_MAYBE_END_OF_MD_LINK) || value.matches(IS_CORRECTLY_SPACED_MAYBE_MD_LIST)) {
            return expression;
          } else if (value.matches(IS_MAYBE_MD_LIST)) {
            value = formatMDList(value);
          } else if ((endWithWhiteSpace && !matchesEndsWithWhitespaceTemplate) ||
            (!endWithWhiteSpace && !value.matches(STARTS_AND_ENDS_WITH_NON_WHITESPACE_CHAR))) {
            value = formatLine(endWithWhiteSpace, value);
          }
          if (!value.equals(expression.getValue())) {
            String valueSource = value.replace("\"", "\\\"");
            valueSource = valueSource.replaceAll("\\n", "\\\\n");
            return expression.withValue(value).withValueSource("\"" + valueSource + "\"");
          }
        }
        return expression;
      }

      private String formatLine(boolean endWithWhiteSpace, String value) {
        value = value.replaceAll("^\\s+", "");
        if (endWithWhiteSpace && !value.matches(ENDS_WITH_LINEBREAK)) {
          value = value.replaceAll("\\h*$", "");
          value += " ";
        } else if (endWithWhiteSpace && value.matches(ENDS_WITH_LINEBREAK)) {
          value = value.substring(0, value.lastIndexOf("\n") + 1);
        } else if (!endWithWhiteSpace) {
          value = value.replaceAll("\\s*$", "");
        }
        return value;
      }

      private String formatMDList(String value) {
        value = value.replaceAll("^\\h*", "");
        value = value.replaceAll("\\h*$", "");
        if (!value.matches(ENDS_WITH_LINEBREAK)) {
          value += "\n";
        } else {
          value = value.substring(0, value.lastIndexOf("\n") + 1);
        }
        value = " " + value;
        return value;
      }
    };
    return Preconditions.check(new DeclaresMethod<>(GET_DESCRIPTION_MATCHER), visitor);
  }
}
