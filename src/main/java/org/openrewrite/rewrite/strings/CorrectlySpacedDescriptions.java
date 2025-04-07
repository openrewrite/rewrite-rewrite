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
package org.openrewrite.rewrite.strings;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class CorrectlySpacedDescriptions extends Recipe {

  private static final String STARTS_AND_ENDS_WITH_NON_WHITESPACE_CHAR = "^[^\\s].*[^\\s]$";
  private static final String STARTS_WITH_NON_WHITESPACE_CHAR_ENDS_WITH_SPACE = "^[^\\s].*[^\\s] $";

  @Override
  public String getDisplayName() {
    return "Correctly spaced descriptions";
  }

  @Override
  public String getDescription() {
    return "When a description is defined over several lines this recipe finds and corrects missing trailing spaces.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override
      public J. MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        if (new MethodMatcher("org.openrewrite.Recipe getDescription()", true).matches(method.getMethodType())) {
          J md = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Binary visitBinary(J.Binary b, ExecutionContext ctx) {
              return handle(b).withRight(updateExpression((J.Literal)b.getRight(), false));
            }

            @Override
            public J.Literal visitLiteral(J.Literal l, ExecutionContext ctx) {
              return updateExpression(l, false);
            }

          }.visit(method, ctx, getCursor().getParentOrThrow());
          if(md != null) {
            return (J.MethodDeclaration) md;
          }
        }
        return super.visitMethodDeclaration(method, ctx);
      }

      private J.Binary handle(J.Binary b) {
        Expression l = b.getLeft();
        if (l instanceof J.Binary) {
          J.Binary lb = handle((J.Binary) l);
          J.Literal r = (J.Literal) lb.getRight();
          lb = lb.withRight(updateExpression(r, true));
          return b.withLeft(lb);
        } else if (l instanceof J.Literal) {
          return b.withLeft(updateExpression((J.Literal)l, true));
        } else {
          return b;
        }
      }

      private J.Literal updateExpression(J.Literal expression, boolean endWithSpace) {
        if(expression.getValue() instanceof String) {
          String value = (String) expression.getValue();
          if ((endWithSpace && !value.matches(STARTS_WITH_NON_WHITESPACE_CHAR_ENDS_WITH_SPACE)) ||
            (!endWithSpace && !value.matches(STARTS_AND_ENDS_WITH_NON_WHITESPACE_CHAR))) {
            value = value.replaceAll("^\\s+", "");
            value = value.replaceAll("\\h*$", "");
            value += endWithSpace ? " " : "";
            expression = expression.withValue(value).withValueSource("\"" + value.replace("\"", "\\\"") + "\"");
          }
        }
        return expression;
      }
    };
  }
}
