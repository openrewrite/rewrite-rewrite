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

class RefasterTemplateReturnTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RefasterTemplateReturn())
            .parser(JavaParser.fromJavaVersion()
              //language=java
                .dependsOn(
                    """
                    package com.google.errorprone.refaster.annotation;
                    import java.lang.annotation.*;
                    @Target(ElementType.METHOD)
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface BeforeTemplate {}
                    """,
                    """
                    package com.google.errorprone.refaster.annotation;
                    import java.lang.annotation.*;
                    @Target(ElementType.METHOD)
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface AfterTemplate {}
                    """
                ));
    }

    @DocumentExample
    @Test
    void fixVoidReturnWithExpressionStatement() {
        rewriteRun(
            java(
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;

                class Example {
                    @BeforeTemplate
                    void before(String s) {
                        s.isEmpty();
                    }
                }
                """,
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;

                class Example {
                    @BeforeTemplate
                    boolean before(String s) {
                        return s.isEmpty();
                    }
                }
                """
            )
        );
    }

    @Test
    void fixVoidReturnWithPrimitiveTypes() {
        rewriteRun(
            java(
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;

                class Example {
                    @BeforeTemplate
                    void intExample() {
                        return 42;
                    }
                }
                """,
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;

                class Example {
                    @BeforeTemplate
                    int intExample() {
                        return 42;
                    }
                }
                """
            )
        );
    }

    @Test
    void fixVoidReturnWithObjectType() {
        rewriteRun(
            java(
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;

                class Example {
                    @BeforeTemplate
                    void objectExample() {
                        new Object();
                    }
                }
                """,
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;

                class Example {
                    @BeforeTemplate
                    java.lang.Object objectExample() {
                        return new Object();
                    }
                }
                """
            )
        );
    }

    @Test
    void preserveExistingReturnStatement() {
        rewriteRun(
            java(
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;

                class Example {
                    @BeforeTemplate
                    void before(String s) {
                        return s.isEmpty();
                    }
                }
                """,
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;

                class Example {
                    @BeforeTemplate
                    boolean before(String s) {
                        return s.isEmpty();
                    }
                }
                """
            )
        );
    }

    @Test
    void doNotChangeNonVoidReturnType() {
        rewriteRun(
            java(
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;

                class Example {
                    @BeforeTemplate
                    boolean before(String s) {
                        return s.isEmpty();
                    }

                    @BeforeTemplate
                    String getString() {
                        return "test";
                    }
                }
                """
            )
        );
    }

    @Test
    void doNotChangeMethodsWithoutTemplateAnnotations() {
        rewriteRun(
            java(
                """
                class Example {
                    void notATemplate() {
                        return;
                    }

                    void regularMethod(String s) {
                        s.isEmpty();
                    }
                }
                """
            )
        );
    }

    @Test
    void doNotChangeMethodsWithMultipleStatements() {
        rewriteRun(
          java(
            """
            import com.google.errorprone.refaster.annotation.BeforeTemplate;

            class Example {
                @BeforeTemplate
                void multipleStatements(String s) {
                    System.out.println(s);
                    s.isEmpty();
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeMethodsWithStatements() {
        rewriteRun(
          java(
            """
            import com.google.errorprone.refaster.annotation.BeforeTemplate;

            class Example {
                @BeforeTemplate
                void multipleStatements(String s) {
                    System.out.println(s);
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeMethodsWithEmptyBody() {
        rewriteRun(
            java(
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;

                class Example {
                    @BeforeTemplate
                    void emptyMethod() {
                    }
                }
                """
            )
        );
    }

    @Test
    void handleGenericReturnType() {
        rewriteRun(
            java(
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;
                import java.util.List;
                import java.util.ArrayList;

                class Example {
                    @BeforeTemplate
                    void listExample() {
                        new ArrayList<String>();
                    }
                }
                """,
                """
                import com.google.errorprone.refaster.annotation.BeforeTemplate;
                import java.util.List;
                import java.util.ArrayList;

                class Example {
                    @BeforeTemplate
                    java.util.ArrayList listExample() {
                        return new ArrayList<String>();
                    }
                }
                """
            )
        );
    }
}
