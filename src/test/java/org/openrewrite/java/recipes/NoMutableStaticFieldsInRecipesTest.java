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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NoMutableStaticFieldsInRecipesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new NoMutableStaticFieldsInRecipes())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void removeNonFinalStaticFields() {
        rewriteRun(
          java(
            """
              import org.openrewrite.Recipe;

              public class A extends Recipe {
                  static final int immutable = 0;
                  static int mutable = 0;
              }
              """,
            """
              import org.openrewrite.Recipe;

              public class A extends Recipe {
                  static final int immutable = 0;
              }
              """
          )
        );
    }

    @Test
    void retainFieldsOutsideRecipes() {
        rewriteRun(
          java(
            """
              import org.openrewrite.Recipe;

              public class A {
                  static final int immutable = 0;
                  static int mutable = 0;
              }
              """
          )
        );
    }

    @Test
    void retainWhenWarningsSuppressed() {
        rewriteRun(
          java(
            """
              import org.openrewrite.Recipe;

              public class A extends Recipe {
                  static final int immutable = 0;
                  @SuppressWarnings("unused")
                  static int mutable = 0;
              }
              """
          )
        );
    }
}
