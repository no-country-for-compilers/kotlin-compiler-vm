# Архитектура компилятора и виртуальной машины

## Обзор

Проект представляет собой систему компиляции и выполнения для собственного языка программирования с автоматическим управлением памятью и JIT-компилятором.

**Ключевые особенности:**
- Строгая статическая типизация
- 64-битные целые числа (`int`) для поддержки больших вычислений
- Стековая виртуальная машина с байткодом
- Автоматическая сборка мусора (Reference Counting GC)
- JIT-компиляция для оптимизации производительности

## Compilation Pipeline

```
Source Code (.lang)
      │
      ▼
┌─────────────────────┐
│   1. LEXER          │  Токенизация исходного кода
│   (Tokenizer)       │  Вход:  String
│                     │  Выход: List<Token>
└─────────────────────┘
      │
      ▼
┌─────────────────────┐
│   2. PARSER         │  Синтаксический анализ
│   (Syntax Analyzer) │  Вход:  List<Token>
│                     │  Выход: AST
└─────────────────────┘
      │
      ▼
┌─────────────────────┐
│   3. OPTIMIZER      │  Оптимизации AST
│   • Constant Folding│  Вход:  AST
│   • Dead Code Elim. │  Выход: Optimized AST
└─────────────────────┘
      │
      ▼
┌─────────────────────┐
│   4. SEMANTIC       │  Проверка типов и семантики
│   ANALYZER          │  Вход:  AST
│                     │  Выход: Validated AST + Symbol Table
└─────────────────────┘
      │
      ▼
┌─────────────────────┐
│   5. BYTECODE       │  Генерация низкоуровневого кода
│   GENERATOR         │  Вход:  Validated AST
│                     │  Выход: Bytecode Module
└─────────────────────┘
      │
      ▼
┌─────────────────────┐
│   6. VIRTUAL        │  Выполнение программы
│   MACHINE           │  Вход:  Bytecode Module
│   • Interpreter     │  Выход: Result
│   • GC (RC)         │  + управление памятью
│   • JIT Compiler    │  + оптимизация
└─────────────────────┘
```

## Компоненты системы

### 1. Lexer (Лексический анализатор)

Преобразует текст в последовательность токенов.

**Интерфейс:**
```kotlin
class Lexer(private val source: String) {
    fun tokenize(): List<Token>
}
```

### 2. Parser (Синтаксический анализатор)

Построение абстрактного синтаксического дерева (AST).

**Метод:** Recursive Descent Parser

**Интерфейс:**
```kotlin
class Parser(private val tokens: List<Token>) {
    fun parse(): Program
}
```

### 3. Optimizer (Оптимизатор AST)

**ConstantFolder** - свёртка константных выражений
**DeadCodeEliminator** - удаление мёртвого кода

### 4. Semantic Analyzer (Семантический анализатор)

Проверка семантической корректности программы:
- Проверка типов
- Разрешение имен
- Управление областями видимости
- Проверка функций

**Интерфейс:**
```kotlin
interface SemanticAnalyzer {
    fun analyze(ast: Program): AnalysisResult
}
```

### 5. Bytecode Generator (Генератор байткода)

Преобразование AST в низкоуровневый байткод.

**Тип VM:** Stack-based (стековая машина)

### 6. Virtual Machine (Виртуальная машина)

#### 6.1 Interpreter (Интерпретатор)

**Архитектура:** Stack-based

```kotlin
class VirtualMachine {
    private val operandStack: RcOperandStack
    private val callStack: ArrayDeque<CallFrame>
    private val memoryManager: MemoryManager
}
```

#### 6.2 Garbage Collector (Сборщик мусора)

**Алгоритм:** Reference Counting

**Принцип работы:**
- Каждый объект в куче имеет счётчик ссылок (`refCount`)
- При создании ссылки → `refCount++` (retain)
- При удалении ссылки → `refCount--` (release)
- Когда `refCount` достигает 0 → объект освобождается немедленно

**Преимущества:**
- ✅ Простота реализации
- ✅ Немедленное освобождение памяти
- ✅ Предсказуемое поведение

**Ограничение:** Не собирает циклы ссылок (в текущей версии допустимо, т.к. только массивы примитивов).

#### 6.3 JIT Compiler (Just-In-Time компилятор)

**Стратегия:** Method-based JIT

**Принцип работы:**
1. **Profiling** - отслеживание количества вызовов функций
2. **Hot Spot Detection** - функция считается "горячей" после N вызовов (threshold)
3. **Compilation** - "горячая" функция компилируется в JVM bytecode
4. **Execution** - последующие вызовы используют скомпилированную версию

## Представление данных

### Типы данных языка

| Тип | Размер | Описание |
|-----|--------|----------|
| `int` | 64 бита | Целое число со знаком |
| `float` | 64 бита | IEEE 754 double |
| `bool` | 1 бит | Булево значение |
| `void` | 0 | Отсутствие значения |
| `int[]`, `float[]`, `bool[]` | - | Массивы (в куче) |

### Value Types (представление в VM)

```kotlin
sealed class Value {
    data class IntValue(val value: Long) : Value()
    data class FloatValue(val value: Double) : Value()
    data class BoolValue(val value: Boolean) : Value()
    data class ArrayRef(val heapId: Int) : Value()
    object VoidValue : Value()
}
```

## Технологический стек

- **Язык:** Kotlin
- **Инструменты:** Gradle, JUnit 5
