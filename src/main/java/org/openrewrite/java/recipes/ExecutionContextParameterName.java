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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RenameVariable;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Optional.ofNullable;

@Value
@EqualsAndHashCode(callSuper = false)
public class ExecutionContextParameterName extends Recipe {

    @Option(displayName = "Parameter name",
            description = "The name or prefix to use for the `ExecutionContext` parameter.",
            example = "ctx",
            required = false)
    @Nullable
    String parameterName;

    @Override
    public String getDisplayName() {
        return "Use a standard name for `ExecutionContext`";
    }

    @Override
    public String getDescription() {
        return "Visitors that are parameterized with `ExecutionContext` should use the parameter name `ctx`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String prefix = ofNullable(parameterName).orElse("ctx");
        return Preconditions.check(Preconditions.or(
                        new UsesType<>("org.openrewrite.Recipe", false),
                        new UsesType<>("org.openrewrite.Visitor", false)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                        for (Statement parameter : m.getParameters()) {
                            if (parameter instanceof J.VariableDeclarations) {
                                J.VariableDeclarations param = (J.VariableDeclarations) parameter;
                                if (TypeUtils.isOfClassType(param.getType(), "org.openrewrite.ExecutionContext") &&
                                        !param.getVariables().get(0).getSimpleName().startsWith(prefix)) {
                                    m = (J.MethodDeclaration) new RenameVariable<ExecutionContext>(param.getVariables().get(0), prefix)
                                            .visitNonNull(m, ctx);
                                }
                            }
                        }
                        return m;
                    }
                });
    }
}
