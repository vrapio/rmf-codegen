package io.vrap.codegen.languages.php.extensions

import io.vrap.rmf.codegen.doc.toHtml
import io.vrap.rmf.raml.model.types.DescriptionFacet

fun DescriptionFacet.toPhpComment() = this.toHtml()?.let { it.lines().map { "* "+it }.joinToString(separator = "\n") }?:"*"
