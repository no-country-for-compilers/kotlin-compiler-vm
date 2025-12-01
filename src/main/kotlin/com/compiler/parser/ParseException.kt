package com.compiler.parser

import com.compiler.domain.SourcePos

/**
 * Исключение, выбрасываемое парсером при обнаружении некорректного синтаксиса.
 *
 * @property pos позиция в исходном коде, где произошла ошибка
 * @param message описание ошибки синтаксиса
 */
class ParseException(val pos: SourcePos, message: String) : RuntimeException("Parse error at $pos.line:$pos.column: $message")
