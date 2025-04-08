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

import org.openrewrite.*;
import org.openrewrite.Recipe;
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
            public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
              J.Binary b = super.visitBinary(binary, ctx);
              b = maybeFormatLeft(b);
              if (getCursor().getParentTreeCursor().getValue() instanceof J.Return) {
                b = maybeFormatRight(b);
              }
              return b;
            }
          }.visitNonNull(method, ctx, getCursor().getParentOrThrow());
        }
        return super.visitMethodDeclaration(method, ctx);
      }

      private J.Binary maybeFormatLeft(J.Binary b) {
        Expression left = b.getLeft();
        Expression right = b.getRight();
        if (left instanceof J.Binary) {
          J.Binary updatedLeft = maybeFormatLeftNeighbour((J.Binary) left, right);
          if (updatedLeft != left) {
            return b.withLeft(updatedLeft);
          }
        } else if (isLiteralString(left)) {
          J.Literal updatedLeft = maybeFormatLiteralPrefix((J.Literal) left);
          if (isLiteralString(right)) {
            updatedLeft = maybeFormatLiteralSuffix(updatedLeft);
          }
          if (updatedLeft != left) {
            return b.withLeft(updatedLeft);
          }
        }
        return b;
      }

      private J.Binary maybeFormatLeftNeighbour(J.Binary left, Expression right) {
        J.Literal result = null;
        if (isLiteralString(left.getRight())) {
          if (isLiteralString(right)) {
            result = maybeFormatLiteralSuffix((J.Literal) left.getRight());
          }
          if (isLiteralString(left.getLeft())) {
            result = maybeFormatLiteralPrefix(result != null ? result : (J.Literal) left.getRight());
          }
          if(result != null) {
            return left.withRight(result);
          }
        }
        return left;
      }

      private J.Binary maybeFormatRight(J.Binary b) {
        Expression left = b.getLeft();
        Expression right = b.getRight();
        J.Literal updatedRight = maybeFormatLiteralSuffix((J.Literal) right, true);
        if (isLiteralString(left)) {
          updatedRight = maybeFormatLiteralPrefix(updatedRight);
        }
        if (updatedRight != right) {
          return b.withRight(updatedRight);
        }

        return b;
      }

      private J.Literal maybeFormatLiteralPrefix(J.Literal l) {
        if (l.getValue() instanceof String) {
          String value = (String) l.getValue();
          if (value.matches(IS_ONLY_WHITESPACE)) {
            return l;
          }
          if (value.matches(IS_MAYBE_MD_LIST)) {
            value = " " + value.replaceAll("^\\h*", "");
          } else {
            value = value.replaceAll("^\\s+", "");
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
            value = value.replaceAll("\\s*$", "");
            value += isLastLine ? "" : "\n";
          } else if (!isLastLine && value.matches(ENDS_WITH_LINEBREAK)) {
            value = value.substring(0, value.lastIndexOf("\n") + 1);
          } else {
            value = value.replaceAll("\\s*$", "");
            value += isLastLine ? "" : " ";
          }
          if (!value.equals(l.getValue())) {
            String valueSource = value.replace("\"", "\\\"");
            valueSource = valueSource.replaceAll("\\n", "\\\\n");
            return l.withValue(value).withValueSource("\"" + valueSource + "\"");
          }
        }
        return l;
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
    };
    return Preconditions.check(new DeclaresMethod<>(GET_DESCRIPTION_MATCHER), visitor);
  }
}
