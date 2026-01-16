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
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.template.RecipeDescriptor;


@RecipeDescriptor(
        name = "Use `StringUtils` utility methods",
        description = "Replaces common string null and empty checks with `org.openrewrite.internal.StringUtils` utility methods."
)
public class UseStringUtils {

    @RecipeDescriptor(
            name = "Use `StringUtils#isNotEmpty(String)`",
            description = "Replace `str != null && !str.isEmpty()` with `StringUtils.isNotEmpty(str)`."
    )
    public static class StringIsNotEmpty {
        @BeforeTemplate
        boolean before(String str) {
            return str != null && !str.isEmpty();
        }

        @AfterTemplate
        boolean after(String str) {
            return StringUtils.isNotEmpty(str);
        }
    }
}
