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

### Примеры файлов

- `examples/simple.lang` - простая программа с переменными
- `examples/factorial.lang` - рекурсивная функция факториала

## Структура проекта

```
kotlin-compiler-vm/
├── src/
│   ├── main/kotlin/com/compiler/
│   │   ├── lexer/           # Лексический анализатор
│   │   │   ├── Token.kt
│   │   │   ├── TokenType.kt
│   │   │   └── Lexer.kt
│   │   └── Main.kt          # Точка входа
│   └── test/kotlin/com/compiler/
│       └── lexer/
│           └── LexerTest.kt # Тесты лексера
├── examples/                # Примеры программ
├── build.gradle.kts         # Конфигурация сборки
└── QUICKSTART.md           # Этот файл
```

## Статус разработки

- ✅ Lexer (Лексический анализатор)
- ⏳ Parser (Синтаксический анализатор) - в разработке
- ⏳ Semantic Analyzer - планируется
- ⏳ Bytecode Generator - планируется
- ⏳ Virtual Machine - планируется
- ⏳ Garbage Collector - планируется
- ⏳ JIT Compiler - планируется

## Создание своей программы

Создайте файл с расширением `.lang`:

```kotlin
// myprogram.lang
func main(): void {
    let x: int = 42;
    let y: int = x + 10;
}
```

Запустите токенизацию:

```bash
./gradlew run --args="--lex myprogram.lang"
```

## Отладка

Для детального вывода токенов используйте флаг `--lex`:

```bash
./gradlew run --args="--lex examples/factorial.lang"
```

Вывод покажет все токены с их типами, позициями и значениями.
