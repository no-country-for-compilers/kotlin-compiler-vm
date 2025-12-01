# Quick Start Guide

## Требования

- JDK 17 или выше
- Gradle (встроен в проект через wrapper)

## Сборка проекта

```bash
./gradlew build
```

## Запуск тестов

```bash
# Запуск всех тестов
./gradlew test

# Запуск только тестов лексера
./gradlew test --tests "com.compiler.lexer.LexerTest"

# Запуск только тестов парсера
./gradlew test --tests "com.compiler.lexer.ParserTest"

# Запуск с подробным выводом
./gradlew test --info
```

## Использование

### Токенизация файла (только Lexer)

```bash
./gradlew run --args="--lex examples/factorial.lang"
```

или после сборки:

```bash
java -jar build/libs/kotlin-compiler-vm-1.0.0.jar --lex examples/factorial.lang
```

### Синтаксический анализ и построение AST (Lexer + Parser)

```bash
./gradlew run --args="--parse examples/factorial.lang"
```

или после сборки:

```bash
java -jar build/libs/kotlin-compiler-vm-1.0.0.jar --parse examples/factorial.lang
```

### Примеры файлов

- `examples/factorial.lang` - рекурсивная функция факториала
- `examples/mergesort.lang` - сортировка слиянием
- `examples/prime.lang` - проверка простого числа
- `examples/simple.lang` - простая программа с переменными

## Структура проекта

```
kotlin-compiler-vm/
├── src/
│   ├── main/kotlin/com/compiler/
│   │   ├── lexer/           # Лексический анализатор
│   │   │   ├── Token.kt
│   │   │   ├── TokenType.kt
│   │   │   └── Lexer.kt
│   │   ├── parser/          # Синтаксический анализатор
│   │   │   ├── Ast.kt
│   │   │   ├── ParseException.kt
│   │   │   └── Parser.kt
│   │   └── Main.kt          # Точка входа
│   └── test/kotlin/com/compiler/
│       ├── lexer/
│       │   └──  LexerTest.kt # Тесты лексера
│       └── parser/
│           └── ParserTest.kt # Тесты парсера
├── specs/
│   └── QUICKSTART.md        # Этот файл
├── examples/                # Примеры программ
└── build.gradle.kts         # Конфигурация сборки
```

## Статус разработки

- ✅ Lexer (Лексический анализатор)
- ✅ Parser (Синтаксический анализатор)
- ⏳ Semantic Analyzer - планируется
- ⏳ Bytecode Generator - планируется
- ⏳ Virtual Machine - планируется
- ⏳ Garbage Collector - планируется
- ⏳ JIT Compiler - планируется
