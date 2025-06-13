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
package org.openrewrite.java.recipes.migrate;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;

@RecipeDescriptor(
        name = "Replace static `Traits` methods with constructor calls",
        description = "Replace the usage of static `Traits` methods with the corresponding constructor calls, " +
                "as the `Traits` classes were an early abstraction with undesirable import conflicts."
)
public class RemoveTraitsUsage {

    @RecipeDescriptor(
            name = "Remove `org.openrewrite.java.trait.Traits` usage",
            description = "Removes the usage of static `org.openrewrite.java.trait.Traits` class and replace with corresponding constructor calls."
    )
    public static class Java {
        @RecipeDescriptor(
                name = "Remove `org.openrewrite.java.trait.Traits.literal()` usage",
                description = "Removes the usage of static `org.openrewrite.java.trait.Traits.literal()`."
        )
        public static class Literal {
            @BeforeTemplate
            org.openrewrite.java.trait.Literal.Matcher before() {
                return org.openrewrite.java.trait.Traits.literal();
            }

            @AfterTemplate
            org.openrewrite.java.trait.Literal.Matcher after() {
                return new org.openrewrite.java.trait.Literal.Matcher();
            }
        }

        @RecipeDescriptor(
                name = "Remove `org.openrewrite.java.trait.Traits.variableAccess()` usage",
                description = "Removes the usage of static `org.openrewrite.java.trait.Traits.variableAccess()`."
        )
        public static class VariableAccess {
            @BeforeTemplate
            org.openrewrite.java.trait.VariableAccess.Matcher before() {
                return org.openrewrite.java.trait.Traits.variableAccess();
            }

            @AfterTemplate
            org.openrewrite.java.trait.VariableAccess.Matcher after() {
                return new org.openrewrite.java.trait.VariableAccess.Matcher();
            }
        }

        @RecipeDescriptor(
                name = "Remove `org.openrewrite.java.trait.Traits.methodAccess(*)` usage",
                description = "Removes the usage of static `org.openrewrite.java.trait.Traits.methodAccess(*)`."
        )
        public static class MethodAccess {
            @RecipeDescriptor(
                    name = "Remove `org.openrewrite.java.trait.Traits.methodAccess(MethodMatcher)` usage",
                    description = "Removes the usage of static `org.openrewrite.java.trait.Traits.methodAccess(MethodMatcher)`."
            )
            public static class MethodMatcher {
                @BeforeTemplate
                org.openrewrite.java.trait.MethodAccess.Matcher before(org.openrewrite.java.MethodMatcher matcher) {
                    return org.openrewrite.java.trait.Traits.methodAccess(matcher);
                }

                @AfterTemplate
                org.openrewrite.java.trait.MethodAccess.Matcher after(org.openrewrite.java.MethodMatcher matcher) {
                    return new org.openrewrite.java.trait.MethodAccess.Matcher(matcher);
                }
            }

            @RecipeDescriptor(
                    name = "Remove `org.openrewrite.java.trait.Traits.methodAccess(String)` usage",
                    description = "Removes the usage of static `org.openrewrite.java.trait.Traits.methodAccess(String)`."
            )
            public static class StringSignature {
                @BeforeTemplate
                org.openrewrite.java.trait.MethodAccess.Matcher before(String signature) {
                    return org.openrewrite.java.trait.Traits.methodAccess(signature);
                }

                @AfterTemplate
                org.openrewrite.java.trait.MethodAccess.Matcher after(String signature) {
                    return new org.openrewrite.java.trait.MethodAccess.Matcher(signature);
                }
            }
        }

        @RecipeDescriptor(
                name = "Remove `org.openrewrite.java.trait.Traits.annotated(*)` usage",
                description = "Removes the usage of static `org.openrewrite.java.trait.Traits.annotated(*)`."
        )
        public static class Annotated {

            @RecipeDescriptor(
                    name = "Remove `org.openrewrite.java.trait.Traits.annotated(AnnotationMatcher)` usage",
                    description = "Removes the usage of static `org.openrewrite.java.trait.Traits.annotated(AnnotationMatcher)`."
            )
            public static class AnnotationMatcher {
                @BeforeTemplate
                org.openrewrite.java.trait.Annotated.Matcher before(org.openrewrite.java.AnnotationMatcher matcher) {
                    return org.openrewrite.java.trait.Traits.annotated(matcher);
                }

                @AfterTemplate
                org.openrewrite.java.trait.Annotated.Matcher after(org.openrewrite.java.AnnotationMatcher matcher) {
                    return new org.openrewrite.java.trait.Annotated.Matcher(matcher);
                }
            }

            @RecipeDescriptor(
                    name = "Remove `org.openrewrite.java.trait.Traits.annotated(String)` usage",
                    description = "Removes the usage of static `org.openrewrite.java.trait.Traits.annotated(String)`."
            )
            public static class StringSignature {
                @BeforeTemplate
                org.openrewrite.java.trait.Annotated.Matcher before(String signature) {
                    return org.openrewrite.java.trait.Traits.annotated(signature);
                }

                @AfterTemplate
                org.openrewrite.java.trait.Annotated.Matcher after(String signature) {
                    return new org.openrewrite.java.trait.Annotated.Matcher(signature);
                }
            }

            @RecipeDescriptor(
                    name = "Remove `org.openrewrite.java.trait.Traits.annotated(Class<?>)` usage",
                    description = "Removes the usage of static `org.openrewrite.java.trait.Traits.annotated(Class<?>)`."
            )
            public static class ClassType {
                @BeforeTemplate
                org.openrewrite.java.trait.Annotated.Matcher before(Class<?> annotationType) {
                    return org.openrewrite.java.trait.Traits.annotated(annotationType);
                }

                @AfterTemplate
                org.openrewrite.java.trait.Annotated.Matcher after(Class<?> annotationType) {
                    return new org.openrewrite.java.trait.Annotated.Matcher(annotationType);
                }
            }
        }
    }

    @RecipeDescriptor(
            name = "Remove `org.openrewrite.maven.trait.Traits` usage",
            description = "Removes the usage of static `org.openrewrite.maven.trait.Traits` class and replace with corresponding constructor calls."
    )
    public static class Maven {
        @RecipeDescriptor(
                name = "Remove `org.openrewrite.maven.trait.Traits.mavenDependency()` usage",
                description = "Removes the usage of static `org.openrewrite.maven.trait.Traits.mavenDependency()`."
        )
        public static class Dependency {
            @BeforeTemplate
            org.openrewrite.maven.trait.MavenDependency.Matcher before() {
                return org.openrewrite.maven.trait.Traits.mavenDependency();
            }

            @AfterTemplate
            org.openrewrite.maven.trait.MavenDependency.Matcher after() {
                return new org.openrewrite.maven.trait.MavenDependency.Matcher();
            }
        }

        @RecipeDescriptor(
                name = "Remove `org.openrewrite.maven.trait.Traits.mavenPlugin()` usage",
                description = "Removes the usage of static `org.openrewrite.maven.trait.Traits.mavenPlugin()`."
        )
        public static class Plugin {
            @BeforeTemplate
            org.openrewrite.maven.trait.MavenPlugin.Matcher before() {
                return org.openrewrite.maven.trait.Traits.mavenPlugin();
            }

            @AfterTemplate
            org.openrewrite.maven.trait.MavenPlugin.Matcher after() {
                return new org.openrewrite.maven.trait.MavenPlugin.Matcher();
            }
        }
    }

    @RecipeDescriptor(
            name = "Remove `org.openrewrite.gradle.trait.Traits` usage",
            description = "Removes the usage of static `org.openrewrite.java.gradle.Traits` class and replace with corresponding constructor calls."
    )
    public static class Gradle {
        @RecipeDescriptor(
                name = "Remove `org.openrewrite.gradle.trait.Traits.gradleDependency()` usage",
                description = "Removes the usage of static `org.openrewrite.gradle.trait.Traits.gradleDependency()`."
        )
        public static class Dependency {
            @BeforeTemplate
            org.openrewrite.gradle.trait.GradleDependency.Matcher before() {
                return org.openrewrite.gradle.trait.Traits.gradleDependency();
            }

            @AfterTemplate
            org.openrewrite.gradle.trait.GradleDependency.Matcher after() {
                return new org.openrewrite.gradle.trait.GradleDependency.Matcher();
            }
        }

        @RecipeDescriptor(
                name = "Remove `org.openrewrite.gradle.trait.Traits.jvmTestSuite()` usage",
                description = "Removes the usage of static `org.openrewrite.gradle.trait.Traits.jvmTestSuite()`."
        )
        public static class JvmTestSuite {
            @BeforeTemplate
            org.openrewrite.gradle.trait.JvmTestSuite.Matcher before() {
                return org.openrewrite.gradle.trait.Traits.jvmTestSuite();
            }

            @AfterTemplate
            org.openrewrite.gradle.trait.JvmTestSuite.Matcher after() {
                return new org.openrewrite.gradle.trait.JvmTestSuite.Matcher();
            }
        }
    }
}
