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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CorrectlySpacedDescriptionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new CorrectlySpacedDescriptions());
    }

    @DocumentExample
    @Test
    void document() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description." +
                       " which might" +
                       "consist    " +
                       "   of several lines.";
                  }
              }
              """,
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description. " +
                       "which might " +
                       "consist " +
                       "of several lines.";
                  }
              }
              """
          )
        );
    }

    @Test
    void correctSingleLineDescription() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description.";
                  }
              }
              """
          )
        );
    }

    @Test
    void incorrectLeadingSpaceSingleLineDescription() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "   A description.";
                  }
              }
              """,
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description.";
                  }
              }
              """
          )
        );
    }

    @Test
    void incorrectTrailingSpaceSingleLineDescription() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description.   ";
                  }
              }
              """,
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description.";
                  }
              }
              """
          )
        );
    }

    @Test
    void correctMultiLineDescription() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description " +
                       "that is " +
                       "correctly spaced.";
                  }
              }
              """
          )
        );
    }

    @Test
    void incorrectMultiLineDescription() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description    " +
                       "     that is " +
                       "    correctly spaced.     ";
                  }
              }
              """,
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description " +
                       "that is " +
                       "correctly spaced.";
                  }
              }
              """
          )
        );
    }

    @Test
    void escapedQuotes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A \\"description\\"    " +
                       "     should \\"keep\\" all the \\"escaped\\" " +
                       "    \\"quotes\\" in it.     ";
                  }
              }
              """,
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A \\"description\\" " +
                       "should \\"keep\\" all the \\"escaped\\" " +
                       "\\"quotes\\" in it.";
                  }
              }
              """
          )
        );
    }
}
