/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;

import java.util.LinkedHashMap;
import java.util.Map;

public class FindQualityCheckEvasionsInRecipes extends Recipe {

    private static final String EXECUTION_CONTEXT_STATE = "Keep state across source files in a `ScanningRecipe` accumulator, " +
            "and within one source file in cursor messages, rather than on the `ExecutionContext` a run shares";
    private static final String SOURCE_SET_REBUILT = "Derive the new `JavaSourceSet` from the existing marker with its `with` methods, " +
            "so it keeps the classpath and type information it already carries";

    private static final Map<MethodMatcher, String> ADVICE = new LinkedHashMap<>();

    static {
        ADVICE.put(new MethodMatcher("org.openrewrite.ExecutionContext computeMessageIfAbsent(..)", true), EXECUTION_CONTEXT_STATE);
        ADVICE.put(new MethodMatcher("org.openrewrite.ExecutionContext getMessages()", true), EXECUTION_CONTEXT_STATE);
        ADVICE.put(new MethodMatcher("org.openrewrite.marker.AlreadyReplaced <constructor>(..)"),
                "Check the tree for the change already made, rather than reusing the marker rewrite's own `FindAndReplace` uses to carry information");
        ADVICE.put(new MethodMatcher("org.openrewrite.java.marker.JavaSourceSet build(..)"), SOURCE_SET_REBUILT);
        ADVICE.put(new MethodMatcher("org.openrewrite.java.marker.JavaSourceSet <constructor>(..)"), SOURCE_SET_REBUILT);
        ADVICE.put(new MethodMatcher("org.openrewrite.scheduling.RecipeRunCycle getMadeChangesInThisCycle()"),
                "Finish the change within the cycle, for instance with `doAfterVisit`, rather than forcing another cycle through scheduler internals");
    }

    @Getter
    final String displayName = "Find ways recipe code gets past `rewriteRun` quality checks";

    @Getter
    final String description = "Finds recipe code that keeps state on the `ExecutionContext`, reuses a marker of rewrite's own recipes " +
            "to carry information, rebuilds a `JavaSourceSet` from a few of its fields, or adds to the recipes that made changes in " +
            "a cycle to force another. Each finding says what to do instead.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new FlagMethodCallsWithAdvice(ADVICE);
    }
}
