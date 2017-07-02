/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.kotlinpoet

import java.util.UUID
import javax.lang.model.SourceVersion

/**
 * Assigns Kotlin identifier names to avoid collisions, keywords, and invalid characters. To use,
 * first create an instance and allocate all of the names that you need. Typically this is a
 * mix of user-supplied names and constants:
 *
 * ```
 *   NameAllocator nameAllocator = new NameAllocator();
 *   for (MyProperty property : properties) {
 *     nameAllocator.newName(property.name(), property);
 *   }
 *   nameAllocator.newName("sb", "string builder");
 * ```
 *
 * Pass a unique tag object to each allocation. The tag scopes the name, and can be used to look up
 * the allocated name later. Typically the tag is the object that is being named. In the above
 * example we use `property` for the user-supplied property names, and `"string builder"` for our
 * constant string builder.
 *
 * Once we've allocated names we can use them when generating code:
 *
 * ```
 *   FunSpec.Builder builder = FunSpec.builder("toString")
 *       .addAnnotation(Override.class)
 *       .addModifiers(Modifier.PUBLIC)
 *       .returns(String.class);
 *
 *   builder.addStatement("%1T %2N = new %1T()",
 *       StringBuilder.class, nameAllocator.get("string builder"));
 *
 *   for (MyProperty property : properties) {
 *     builder.addStatement("%N.append(%N)",
 *         nameAllocator.get("string builder"), nameAllocator.get(property));
 *   }
 *   builder.addStatement("return %N", nameAllocator.get("string builder"));
 *   return builder.build();
 * ```
 *
 * The above code generates unique names if presented with conflicts. Given user-supplied properties
 * with names `ab` and `sb` this generates the following:
 *
 * ```
 * @Override public String toString() {
 *   StringBuilder sb_ = new StringBuilder();
 *   sb_.append(ab);
 *   sb_.append(sb);
 *   return sb_.toString();
 * }
 * ```
 *
 * The underscore is appended to `sb` to avoid conflicting with the user-supplied `sb` property.
 * Underscores are also prefixed for names that start with a digit, and used to replace name-unsafe
 * characters like space or dash.
 *
 * When dealing with multiple independent inner scopes, use a [clone][NameAllocator.clone] of the
 * NameAllocator used for the outer scope to further refine name allocation for a specific inner
 * scope.
 */
class NameAllocator private constructor(
    private val allocatedNames: MutableSet<String>,
    private val tagToName: MutableMap<Any, String>) : Cloneable {

  constructor() : this(mutableSetOf(), mutableMapOf())

  /**
   * Return a new name using `suggestion` that will not be a Java identifier or clash with other
   * names. The returned value can be queried multiple times by passing `tag` to
   * [NameAllocator.fromType].
   */
  @JvmOverloads fun newName(suggestion: String, tag: Any = UUID.randomUUID().toString()): String {
    var result = toJavaIdentifier(suggestion)
    while (SourceVersion.isKeyword(result) || !allocatedNames.add(result)) {
      result += "_"
    }

    val replaced = tagToName.put(tag, result)
    if (replaced != null) {
      tagToName.put(tag, replaced) // Put things back as they were!
      throw IllegalArgumentException("tag $tag cannot be used for both '$replaced' and '$result'")
    }

    return result
  }

  /** Retrieve a name created with [NameAllocator.newName]. */
  fun get(tag: Any): String {
    return tagToName[tag] ?: throw IllegalArgumentException("unknown tag: $tag")
  }

  /**
   * Create a deep copy of this NameAllocator. Useful to create multiple independent refinements
   * of a NameAllocator to be used in the respective definition of multiples, independently-scoped,
   * inner code blocks.
   *
   * @return A deep copy of this NameAllocator.
   */
  public override fun clone(): NameAllocator {
    return NameAllocator(allocatedNames.toMutableSet(), tagToName.toMutableMap())
  }
}

internal fun toJavaIdentifier(suggestion: String): String {
  val result = StringBuilder()
  var i = 0
  while (i < suggestion.length) {
    val codePoint = suggestion.codePointAt(i)
    if (i == 0
        && !Character.isJavaIdentifierStart(codePoint)
        && Character.isJavaIdentifierPart(codePoint)) {
      result.append("_")
    }

    val validCodePoint: Int = if (Character.isJavaIdentifierPart(codePoint))
      codePoint else
      '_'.toInt()
    result.appendCodePoint(validCodePoint)
    i += Character.charCount(codePoint)
  }
  return result.toString()
}
