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

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

@RecipeDescriptor(
        name = "Use `J.Literal.isLiteralValue(expression, null)`",
        description = "Replace `expression instanceof J.Literal && ((J.Literal) expression).getValue() == null` with `J.Literal.isLiteralValue(expression, null)`."
)
public class IsLiteralNull {
    @BeforeTemplate
    boolean before(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == null;
    }

    @AfterTemplate
    boolean after(Expression expression) {
        return J.Literal.isLiteralValue(expression, null);
    }
}
