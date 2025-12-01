# Архитектура компилятора и виртуальной машины

## Обзор

Проект представляет собой полноценную систему компиляции и выполнения для собственного языка программирования с автоматическим управлением памятью и JIT-компилятором.

**Ключевые особенности:**

- Строгая статическая типизация
- 64-битные целые числа (`int`) для поддержки больших вычислений (факториал 20!)
- Стековая виртуальная машина с байткодом
- Автоматическая сборка мусора (Mark-and-Sweep GC)
- JIT-компиляция для оптимизации производительности

## Compilation Pipeline

```
┌─────────────────────────────────────────────────────────────────┐
│                     COMPILATION PIPELINE                         │
└─────────────────────────────────────────────────────────────────┘

Source Code (.lang)
      │
      │  "let x: int = 5 + 3;"
      │
      ▼
┌─────────────────────┐
│   1. LEXER          │  Токенизация исходного кода
│   (Tokenizer)       │  Вход:  String
│                     │  Выход: List<Token>
└─────────────────────┘
      │
      │  [LET, ID("x"), COLON, INT, ASSIGN, NUM(5), PLUS, NUM(3), SEMICOLON]
      │
      ▼
┌─────────────────────┐
│   2. PARSER         │  Синтаксический анализ
│   (Syntax Analyzer) │  Вход:  List<Token>
│                     │  Выход: AST (Abstract Syntax Tree)
└─────────────────────┘
      │
      │  VarDecl("x", IntType, BinaryExpr(5, PLUS, 3))
      │
      ▼
┌─────────────────────┐
│   3. SEMANTIC       │  Проверка типов и семантики
│   ANALYZER          │  Вход:  AST
│                     │  Выход: Validated AST + Symbol Table
└─────────────────────┘
      │
      │  Annotated AST с информацией о типах
      │
      ▼
┌─────────────────────┐
│   4. BYTECODE       │  Генерация низкоуровневого кода
│   GENERATOR         │  Вход:  Validated AST
│                     │  Выход: Bytecode Module
└─────────────────────┘
      │
      │  [PUSH 5, PUSH 3, ADD, STORE x]
      │
      ▼
┌─────────────────────┐
│   5. VIRTUAL        │  Выполнение программы
│   MACHINE           │  Вход:  Bytecode Module
│   • Interpreter     │  Выход: Result
│   • GC              │  + управление памятью
│   • JIT Compiler    │  + оптимизация
└─────────────────────┘
      │
      ▼
   Result
```

---

## Компоненты системы

### 1. Lexer (Лексический анализатор)

**Цель:** Преобразование текста в последовательность токенов

**Входные данные:**

```kotlin
let x: int = 42;
```

**Выходные данные:**

```
Token(LET), Token(IDENTIFIER, "x"), Token(COLON), 
Token(TYPE_INT), Token(ASSIGN), Token(NUMBER, 42), 
Token(SEMICOLON)
```

**Задачи:**

- Распознавание ключевых слов (`let`, `func`, `if`, `for`, etc.)
- Идентификация литералов (числа, `true`/`false`)
- Обработка операторов (`+`, `-`, `*`, `/`, `==`, etc.)
- Пропуск пробелов и комментариев
- Отслеживание позиций для сообщений об ошибках

**Интерфейс:**

```kotlin
class Lexer(private val source: String) {
    fun tokenize(): List<Token>
}
```

---

### 2. Parser (Синтаксический анализатор)

**Цель:** Построение абстрактного синтаксического дерева (AST)

**Входные данные:** Последовательность токенов

**Выходные данные:** AST - древовидная структура программы

**Метод:** Recursive Descent Parser (рекурсивный спуск)

**Пример AST:**

```
Program
├─ FunctionDecl: "factorial"
│  ├─ Parameters:
│  └─ n: int
│  ├─ ReturnType: int
│  └─ Body:
│     └─ Block
│        └─ IfStmt
│           ├─ Condition:
│           │  └─ BinaryExpr(LE)
│           │     ├─ Left:
│           │     │  ├─ Variable(n)
│           │     └─ Right:
│           │        └─ Literal(1)
│           ├─ Then:
│           │  ├─ Block
│           │  │  └─ Return
│           │  │     └─ Value:
│           │  │        └─ Literal(1)
│           └─ Else:
│              └─ Block
│                 └─ Return
│                    └─ Value:
│                       └─ BinaryExpr(STAR)
│                          ├─ Left:
│                          │  ├─ Variable(n)
│                          └─ Right:
│                             └─ Call
│                                ├─ Callee:
│                                │  ├─ Variable(factorial)
│                                └─ Args:
│                                   └─ BinaryExpr(MINUS)
│                                      ├─ Left:
│                                      │  ├─ Variable(n)
│                                      └─ Right:
│                                         └─ Literal(1)
└─ FunctionDecl: "main"
   ├─ ReturnType: void
   └─ Body:
      └─ Block
         └─ VarDecl: result: int
            └─ Initializer:
               └─ Call
                  ├─ Callee:
                  │  ├─ Variable(factorial)
                  └─ Args:
                     └─ Literal(20)
```

**Интерфейс:**

```kotlin
class Parser(private val tokens: List<Token>) {
    fun parse(): Program
}

sealed class ASTNode
data class Program(val statements: List<Statement>) : ASTNode()
```

**Преимущества Recursive Descent:**

- Простота реализации
- Понятный код, легко отлаживать
- Естественное соответствие грамматике языка
- Хорошие сообщения об ошибках

---

### 3. Semantic Analyzer (Семантический анализатор)

**Цель:** Проверка семантической корректности программы

**Основные задачи:**

#### 3.1 Type Checking (Проверка типов)

```kotlin
// ✅ Корректно
let x: int = 5 + 3;

// ❌ Ошибка типа
let x: int = 3.14;  // Expected int, got float
```

#### 3.2 Name Resolution (Разрешение имен)

```kotlin
// ❌ Неопределенная переменная
let y: int = z + 1;  // Error: undefined variable 'z'
```

#### 3.3 Scope Management (Управление областями видимости)

```kotlin
func foo(): void {
    let x: int = 10;
}

func bar(): void {
    let y: int = x;  // ❌ Error: 'x' not in scope
}
```

#### 3.4 Return Type Validation

```kotlin
func getNumber(): int {
    return true;  // ❌ Error: expected int, got bool
}
```

**Выходные данные:**

- Annotated AST (узлы AST аннотированы информацией о типах)
- Symbol Table (таблица всех переменных и функций)
- Список ошибок (если есть)

**Интерфейс:**

```kotlin
interface SemanticAnalyzer {
    fun analyze(ast: Program): AnalysisResult
}

data class AnalysisResult(
    val annotatedAST: Program,
    val symbolTable: SymbolTable,
    val errors: List<SemanticError>
)
```

---

### 4. Bytecode Generator (Генератор байткода)

**Цель:** Преобразование AST в низкоуровневый байткод

**Входные данные:** Validated AST + Symbol Table

**Выходные данные:** Bytecode Module

**Тип VM:** Stack-based (стековая машина)

- Проще в реализации
- Компактный код
- Независимость от архитектуры процессора

**Концептуальный пример:**

```kotlin
// Исходный код:
let x: int = 5 + 3;

// Генерируемый байткод (концептуально):
PUSH 5         // Положить 5 на стек
PUSH 3         // Положить 3 на стек
ADD            // Сложить два верхних значения
STORE x        // Сохранить результат в x
```

**Структура модуля:**

```kotlin
data class BytecodeModule(
    val constants: ConstantPool,
    val functions: List<CompiledFunction>,
    val entryPoint: String  // "main"
)
```

---

### 5. Virtual Machine (Виртуальная машина)

Состоит из трех основных компонентов:

#### 5.1 Interpreter (Интерпретатор)

**Архитектура:** Stack-based

**Основные элементы:**

```kotlin
class VirtualMachine {
    private val operandStack: Stack<Value>      // Стек для вычислений
    private val callStack: Stack<CallFrame>     // Стек вызовов функций
    private val heap: Heap                      // Куча для объектов (массивов)
    
    fun execute(module: BytecodeModule)
}
```

**Execution Model:**

```
┌──────────────────────┐
│   Operand Stack      │  ← Промежуточные результаты вычислений
│   [42, 10, 5]        │
└──────────────────────┘

┌──────────────────────┐
│   Call Stack         │  ← Фреймы вызовов функций
│   Frame: main()      │
│   Frame: factorial() │
└──────────────────────┘

┌──────────────────────┐
│   Heap               │  ← Динамически аллоцируемые объекты
│   [Array1, Array2]   │
└──────────────────────┘
```

#### 5.2 Garbage Collector (Сборщик мусора)

**Алгоритм:** Mark-and-Sweep (маркировка и очистка)

**Фазы работы:**

**Phase 1 - Mark (Маркировка):**

- Начать с "корней" (stack, call frames, globals)
- Рекурсивно пометить все достижимые объекты

**Phase 2 - Sweep (Очистка):**

- Пройти по всем объектам в куче
- Удалить непомеченные объекты
- Освободить память

**Триггеры запуска:**

- Достижение порога памяти (например, heap > 1MB)
- Явный запрос при аллокации нового объекта

```kotlin
class GarbageCollector {
    fun collect(roots: List<Value>) {
        // Phase 1: Mark
        val liveObjects = markPhase(roots)
        
        // Phase 2: Sweep
        sweepPhase(liveObjects)
    }
}
```

**Преимущества Mark-and-Sweep:**

- ✅ Простота реализации
- ✅ Не требует перемещения объектов
- ✅ Работает с циклическими ссылками

#### 5.3 JIT Compiler (Just-In-Time компилятор)

**Стратегия:** Method-based JIT

**Принцип работы:**

1. **Profiling (Профилирование)**
   - Отслеживание количества вызовов каждой функции
   - Сбор статистики времени выполнения

2. **Hot Spot Detection (Обнаружение "горячих" участков)**
   - Функция считается "горячей" после N вызовов
   - Например: threshold = 1000 вызовов

3. **Compilation (Компиляция)**
   - "Горячая" функция компилируется в нативный код
   - Или в оптимизированную версию байткода

4. **Execution (Выполнение)**
   - Последующие вызовы используют скомпилированную версию
   - Значительное ускорение (2-10x)

```kotlin
class JITCompiler {
    private val callCounts = mutableMapOf<String, Int>()
    private val compiledFunctions = mutableMapOf<String, CompiledCode>()
    
    fun recordCall(functionName: String) {
        val count = callCounts.increment(functionName)
        
        if (count >= COMPILATION_THRESHOLD) {
            compile(functionName)
        }
    }
}
```

**Подходы к реализации JIT:**

- **Вариант 1:** Оптимизированный интерпретатор (специализация для конкретной функции)
- **Вариант 2:** Компиляция в JVM байткод (используя возможности Kotlin/JVM)
- **Вариант 3:** Генерация нативного кода (сложнее, но быстрее)

---

## Представление данных

### Типы данных языка

| Тип | Размер | Диапазон | Описание |
|-----|--------|----------|----------|
| `int` | 64 бита | -2⁶³ до 2⁶³-1 | Целое число со знаком (достаточно для факториала 20!) |
| `float` | 64 бита | IEEE 754 double | Число с плавающей точкой двойной точности |
| `bool` | 1 бит | true/false | Булево значение |
| `void` | 0 | - | Отсутствие значения (только для return type) |
| `int[]` | - | - | Массив целых чисел (в куче) |
| `float[]` | - | - | Массив чисел с плавающей точкой (в куче) |

**Обоснование размеров:**

- **int как 64-bit:** Необходимо для вычисления факториала 20! = 2,432,902,008,176,640,000
- **float как double:** Обеспечивает достаточную точность для научных вычислений
- **Массивы:** Поддержка больших массивов (до 10000+ элементов) для бенчмарков

### Value Types (представление в VM)

```kotlin
sealed class Value {
    // Примитивные типы (на стеке)
    data class IntValue(val value: Long) : Value()      // 64-bit signed integer
    data class FloatValue(val value: Double) : Value()  // 64-bit IEEE 754 double
    data class BoolValue(val value: Boolean) : Value()  // 1 bit (stored as byte)
    
    // Ссылочные типы (в куче)
    data class ArrayRef(val heapId: Int) : Value()      // Reference to heap object
    
    // Специальные
    object VoidValue : Value()                           // No value
}
```

### Memory Layout

```
┌─────────────────────────────────┐
│         STACK                   │  Быстрый доступ
│  • Локальные переменные         │  Автоматическое управление
│  • Параметры функций            │
│  • Промежуточные результаты     │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│         HEAP                    │  Динамическая память
│  • Массивы                      │  Управление через GC
│  • Будущие объекты              │
└─────────────────────────────────┘
```

---

## Технологический стек

### Язык: Kotlin

- ✅ Null-safety
- ✅ Sealed classes (идеально для AST)
- ✅ Data classes
- ✅ Extension functions
- ✅ Хорошая производительность
- ✅ Межплатформенность

### Инструменты

- **Gradle** - система сборки
- **JUnit 5** - тестирование
- **Detekt** - статический анализ
- **JMH** - бенчмарки производительности

### Структура проекта

```
kotlin-compiler-vm/
├── src/
│   ├── main/kotlin/
│   │   ├── lexer/          # Лексический анализатор
│   │   ├── parser/         # Синтаксический анализатор
│   │   ├── semantic/       # Семантический анализатор
│   │   ├── codegen/        # Генератор байткода
│   │   ├── vm/             # Виртуальная машина
│   │   │   ├── interpreter/
│   │   │   ├── gc/
│   │   │   └── jit/
│   │   └── Main.kt         # Точка входа
│   └── test/kotlin/        # Тесты
│       ├── lexer/
│       ├── parser/
│       └── integration/
├── examples/               # Тестовые программы
│   ├── factorial.lang
│   ├── mergesort.lang
│   └── primes.lang
├── docs/                   # Документация
└── build.gradle.kts
```

---

## Критерии успеха

### Функциональные требования

- ✅ Корректное выполнение факториала
- ✅ Корректная сортировка массива
- ✅ Корректная генерация простых чисел
- ✅ Отсутствие утечек памяти (GC работает)
- ✅ Ускорение от JIT (минимум 2x)

### Требования к производительности

- ✅ Факториал(20) - выполнение за разумное время
- ✅ Сортировка 10000 элементов - < 1 секунды с JIT
- ✅ Простые числа до 100000 - < 2 секунд с JIT

### Требования к качеству

- ✅ Unit-тесты для всех компонентов
- ✅ Integration тесты для полного pipeline
- ✅ Понятные сообщения об ошибках
- ✅ Документация API

---

## Заключение

Данная архитектура обеспечивает:

- ✅ Четкое разделение ответственности между компонентами
- ✅ Возможность параллельной разработки
- ✅ Тестируемость каждого этапа
- ✅ Расширяемость для будущих улучшений
- ✅ Достижение всех требований проекта

Pipeline из 5 этапов является классическим подходом, используемым в реальных компиляторах (GCC, LLVM, V8), что делает проект не только учебным, но и практически значимым.
