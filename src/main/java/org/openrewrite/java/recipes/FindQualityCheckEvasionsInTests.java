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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.IsLikelyTest;

import java.util.LinkedHashMap;
import java.util.Map;

public class FindQualityCheckEvasionsInTests extends Recipe {

    private static final Map<MethodMatcher, String> ADVICE = new LinkedHashMap<>();

    static {
        ADVICE.put(new MethodMatcher("org.openrewrite.test.RecipeSpec cycles(int)"),
                "Drop `cycles(..)`: `rewriteRun` already runs one cycle past `expectedCyclesThatMakeChanges`, " +
                        "and that extra cycle checks the recipe settles");
        ADVICE.put(new MethodMatcher("org.openrewrite.test.RecipeQualityValidation.RecipeQualityValidationBuilder *(boolean)"),
                "Fix what this quality check reports in the recipe, rather than turning the check off in the test");
        ADVICE.put(new MethodMatcher("org.openrewrite.test.TypeValidation none()"),
                "Fix the missing types, or relax only the type validation that fails, rather than turning all of it off");
    }

    @Getter
    final String displayName = "Find ways recipe tests get past `rewriteRun` quality checks";

    @Getter
    final String description = "Finds recipe tests that pin the cycles `rewriteRun` runs, which can skip the cycle that checks a recipe " +
            "settles, and tests that turn a recipe quality check off. Each finding says what to do instead.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsLikelyTest().getVisitor(), new FlagMethodCallsWithAdvice(ADVICE));
    }
}
