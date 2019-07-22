package io.vrap.codegen.languages.php.extensions

fun String.forcedLiteralEscape() = this.forcedLiteralEscape("!")
fun String.forcedLiteralEscape(escapeLiteral: String) = replace("$$escapeLiteral", "$")
