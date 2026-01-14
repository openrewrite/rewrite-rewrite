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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class ReplaceNullWithDoesNotExist extends Recipe {

    @Getter
    final String displayName = "Replace null with RewriteTest.doesNotExist()";

    @Getter
    final String description = "Replace the first or second `null` argument in OpenRewrite Assertions class methods with `RewriteTest.doesNotExist()`.";

    // Match any static method from Assertions classes
    private static final MethodMatcher ASSERTIONS_MATCHER = new MethodMatcher("org.openrewrite.*.Assertions *(String, String, ..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(ASSERTIONS_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (ASSERTIONS_MATCHER.matches(mi)) {
                            return mi.withArguments(ListUtils.map(method.getArguments(), (index, arg) -> {
                                if (J.Literal.isLiteralValue(arg, null) && (index == 0 || index == 1)) {
                                    J.MethodInvocation doesNotExist = JavaTemplate.builder("doesNotExist()")
                                            .contextSensitive()
                                            .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                                    "package org.openrewrite.test;\n" +
                                                            "public interface RewriteTest {\n" +
                                                            "    default String doesNotExist() {\n" +
                                                            "        return null;\n" +
                                                            "    }\n" +
                                                            "}"
                                            ))
                                            .build()
                                            .apply(new Cursor(getCursor(), arg), arg.getCoordinates().replace());
                                    return doesNotExist.withPrefix(arg.getPrefix());
                                }
                                return arg;
                            }));
                        }
                        return mi;
                    }
                }
        );
    }
}
