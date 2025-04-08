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

    @Test
    void lineBreaks() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description\\n\\n" +
                      "   even with lines \\n subject to change \\n " +
                      "can deliberately include\\nnew lines. \\n ";
                  }
              }
              """,
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description\\n\\n" +
                      "even with lines \\n subject to change \\n" +
                      "can deliberately include\\nnew lines.";
                  }
              }
              """
          )
        );
    }

    @Test
    void markdownList() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description:\\n" +
                      "- with  \\n" +
                      " - a  " +
                      "- list " +
                      " * dash\\n" +
                      "* or  " +
                      " * dot\\n\\n";
                  }
              }
              """,
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "A description:\\n" +
                      " - with\\n" +
                      " - a\\n" +
                      " - list\\n" +
                      " * dash\\n" +
                      " * or\\n" +
                      " * dot\\n\\n";
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyWhitespace() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "            " +
                      " \\n \\n   " +
                      " \\n \\t \\t   " +
                      "      ";
                  }
              }
              """
          )
        );
    }

    @Test
    void mdlink() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {
                  @Override
                  public String getDescription() {
                    return "It should leave " +
                      "[md links which are long alone]" +
                      "(www.example.com)";
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyHandleStringConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.Recipe;

              class Test extends Recipe {

                  private static final String SOME_CONST = "quoted";

                  @Override
                  public String getDescription() {
                    return "It should " +
                      "leave `" + SOME_CONST + "` " +
                      "constants and " + SOME_CONST + " other " +
                      "non literal " + SOME_CONST + " strings.";
                  }
              }
              """
          )
        );
    }
}
