# Спецификация парсера

## Обзор

Парсер преобразует последовательность токенов, полученных от лексера, в абстрактное синтаксическое дерево (AST). Реализован как рекурсивный нисходящий парсер (Recursive Descent Parser).

## Архитектура

### Метод парсинга

**Recursive Descent Parser** - метод синтаксического анализа, при котором каждая грамматическая конструкция соответствует отдельной функции-методу парсера.

**Преимущества:**
- Простота реализации и понимания
- Прямое соответствие грамматике языка
- Легкость отладки и модификации
- Хорошие сообщения об ошибках

### Входные данные

- `List<Token>` - последовательность токенов от лексера
- Каждый токен содержит: тип, лексему, значение (если применимо), позицию в исходном коде

### Выходные данные

- `Program` - корневой узел AST, содержащий список верхнеуровневых операторов

## Структура AST

### Базовые классы

```kotlin
sealed class ASTNode  // Базовый класс для всех узлов AST

sealed class Statement : ASTNode()  // Операторы
sealed class Expression : ASTNode()  // Выражения
sealed class TypeNode : ASTNode()  // Типы
```

### Узлы операторов (Statements)

#### Program
Корневой узел программы.

```kotlin
data class Program(val statements: List<Statement>)
```

#### VarDecl
Объявление переменной: `let <identifier>: <type> = <expression>;`

```kotlin
data class VarDecl(
    val identifier: String,
    val type: TypeNode,
    val expression: Expression,
    val pos: SourcePos
)
```

#### FunctionDecl
Объявление функции: `func <identifier>(<parameters>): <returnType> { <body> }`

```kotlin
data class FunctionDecl(
    val identifier: String,
    val parameters: List<Parameter>,
    val returnType: TypeNode,
    val body: BlockStmt,
    val pos: SourcePos
)

data class Parameter(
    val identifier: String,
    val type: TypeNode,
    val pos: SourcePos
)
```

#### BlockStmt
Блок операторов: `{ <statements> }`

```kotlin
data class BlockStmt(val statements: List<Statement>)
```

#### IfStmt
Условный оператор: `if (<condition>) { <thenBranch> } [ else { <elseBranch> } ]`

```kotlin
data class IfStmt(
    val condition: Expression,
    val thenBranch: BlockStmt,
    val elseBranch: BlockStmt? = null
)
```

#### ForStmt
Цикл for: `for (<initializer>?; <condition>?; <increment>?) { <body> }`

```kotlin
data class ForStmt(
    val initializer: ForInitializer,
    val condition: Expression?,
    val increment: Expression?,
    val body: BlockStmt
)

sealed class ForInitializer {
    object ForNoInit : ForInitializer()
    data class ForVarInit(val decl: VarDecl) : ForInitializer()
    data class ForExprInit(val expr: Expression) : ForInitializer()
}
```

#### ReturnStmt
Оператор возврата: `return [expression];`

```kotlin
data class ReturnStmt(
    val value: Expression?,
    val pos: SourcePos
)
```

#### ExprStmt
Оператор-выражение: `<expression>;`

```kotlin
data class ExprStmt(val expr: Expression)
```

### Узлы выражений (Expressions)

#### LiteralExpr
Литеральные значения: числа, булевы значения

```kotlin
data class LiteralExpr(
    val value: Any?,  // Long, Double, Boolean, или null
    val pos: SourcePos
)
```

#### VariableExpr
Идентификатор переменной

```kotlin
data class VariableExpr(
    val name: String,
    val pos: SourcePos
)
```

#### BinaryExpr
Бинарное выражение: `<left> <operator> <right>`

```kotlin
data class BinaryExpr(
    val left: Expression,
    val operator: TokenType,
    val right: Expression,
    val pos: SourcePos
)
```

#### UnaryExpr
Унарное выражение: `<operator> <right>`

```kotlin
data class UnaryExpr(
    val operator: TokenType,
    val right: Expression,
    val pos: SourcePos
)
```

#### AssignExpr
Присваивание: `<lvalue> = <value>`

```kotlin
data class AssignExpr(
    val target: LValue,
    val value: Expression,
    val pos: SourcePos
)

sealed interface LValue  // VariableExpr или ArrayAccessExpr
```

#### CallExpr
Вызов функции: `<callee>(<args>)`

```kotlin
data class CallExpr(
    val callee: Expression,
    val args: List<Expression>,
    val pos: SourcePos
)
```

#### ArrayAccessExpr
Доступ к элементу массива: `<array>[<index>]`

```kotlin
data class ArrayAccessExpr(
    val array: Expression,
    val index: Expression,
    val pos: SourcePos
)
```

#### ArrayLiteralExpr
Массивный литерал: `[<elements>]`

```kotlin
data class ArrayLiteralExpr(
    val elements: List<Expression>,
    val pos: SourcePos
)
```

#### PropertyAccessExpr
Доступ к свойству: `<receiver>.<property>`

```kotlin
data class PropertyAccessExpr(
    val receiver: Expression,
    val property: String,
    val pos: SourcePos
)
```

#### GroupingExpr
Группировка: `(<expression>)`

```kotlin
data class GroupingExpr(
    val expression: Expression,
    val pos: SourcePos
)
```

### Узлы типов (TypeNode)

```kotlin
sealed class TypeNode : ASTNode() {
    object IntType : TypeNode()
    object FloatType : TypeNode()
    object BoolType : TypeNode()
    object VoidType : TypeNode()
    data class ArrayType(val elementType: TypeNode) : TypeNode()
}
```

## Алгоритм парсинга

### Приоритет операторов

Парсер реализует следующую иерархию приоритетов (от низшего к высшему):

1. **Assignment** (`=`)
2. **Logical OR** (`||`)
3. **Logical AND** (`&&`)
4. **Equality** (`==`, `!=`)
5. **Comparison** (`<`, `<=`, `>`, `>=`)
6. **Term** (`+`, `-`)
7. **Factor** (`*`, `/`, `%`)
8. **Unary** (`!`, `-`, `+`)
9. **Postfix** (вызовы функций, доступ к массивам, свойствам)

### Ассоциативность

Все бинарные операторы **левоассоциативны**:
- `a + b + c` → `((a + b) + c)`
- `a || b || c` → `((a || b) || c)`

Унарные операторы **правоассоциативны**:
- `!!x` → `!(!x)`
- `- -x` → `-(-x)`

### Процесс парсинга

#### 1. Парсинг программы

```kotlin
program ::= declaration* EOF
```

Парсер последовательно разбирает все декларации до достижения конца файла.

#### 2. Парсинг деклараций

```kotlin
declaration ::= functionDecl | varDecl | statement
```

Парсер определяет тип декларации по первому токену:
- `func` → объявление функции
- `let` → объявление переменной
- Иначе → обычный оператор

#### 3. Парсинг выражений

Выражения парсятся с учетом приоритета операторов, начиная с самого низкого уровня (assignment) и поднимаясь к первичным выражениям (primary).

**Цепочка вызовов:**
```
expression() → assignment() → logicOr() → logicAnd() → 
equality() → comparison() → term() → factor() → 
unary() → call() → primary()
```

#### 4. Парсинг первичных выражений

```kotlin
primary ::= literal
          | identifier
          | arrayLiteral
          | "(" expression ")"
```

## Обработка ошибок

### ParseException

При обнаружении синтаксической ошибки парсер выбрасывает `ParseException`:

```kotlin
class ParseException(
    val pos: SourcePos,
    message: String
) : RuntimeException("Parse error at $pos.line:$pos.column: $message")
```

### Типы ошибок

1. **Неожиданный токен** - когда ожидается определенный токен, но встречается другой
2. **Отсутствующий токен** - когда обязательный токен отсутствует (например, `;`, `)`, `}`)
3. **Некорректный литерал** - когда значение литерала не может быть корректно интерпретировано
4. **Некорректная цель присваивания** - когда левая часть присваивания не является допустимым lvalue

### Синхронизация после ошибок

Парсер реализует базовую синхронизацию для восстановления после ошибок:
- Пропуск токенов до следующего оператора (`;`)
- Ожидание ключевых слов начала операторов (`func`, `let`, `if`, `for`, `return`)

## Особенности реализации

### Обработка блоков в if/for

Если после `if` или `for` следует одиночный оператор (не блок), парсер автоматически оборачивает его в `BlockStmt` для единообразия AST.

### Парсинг типов массивов

Типы массивов парсятся рекурсивно:
- `int[]` → `ArrayType(IntType)`
- `int[][]` → `ArrayType(ArrayType(IntType))`

### Обработка пустых списков

Парсер корректно обрабатывает:
- Пустые списки параметров: `func foo(): void`
- Пустые списки аргументов: `foo()`
- Пустые массивы: `[]`

### Обработка циклов for

Парсер поддерживает все части цикла как опциональные:
- `for (;;)` - все части пусты
- `for (let i: int = 0; i < 10; i = i + 1)` - полный цикл
- `for (; i < 10;)` - только условие

## Интерфейс

```kotlin
class Parser(private val tokens: List<Token>) {
    fun parse(): Program
}
```

## Примеры

### Пример 1: Простое объявление переменной

**Вход:**
```
let x: int = 42;
```

**AST:**
```
Program(
  statements = [
    VarDecl(
      identifier = "x",
      type = IntType,
      expression = LiteralExpr(42),
      pos = SourcePos(1, 1)
    )
  ]
)
```

### Пример 2: Функция с условием

**Вход:**
```
func factorial(n: int): int {
    if (n <= 1) {
        return 1;
    } else {
        return n * factorial(n - 1);
    }
}
```

**AST:**
```
Program(
  statements = [
    FunctionDecl(
      identifier = "factorial",
      parameters = [Parameter("n", IntType, ...)],
      returnType = IntType,
      body = BlockStmt([
        IfStmt(
          condition = BinaryExpr(
            left = VariableExpr("n"),
            operator = LE,
            right = LiteralExpr(1)
          ),
          thenBranch = BlockStmt([ReturnStmt(LiteralExpr(1))]),
          elseBranch = BlockStmt([
            ReturnStmt(
              BinaryExpr(
                left = VariableExpr("n"),
                operator = STAR,
                right = CallExpr(
                  callee = VariableExpr("factorial"),
                  args = [BinaryExpr(VariableExpr("n"), MINUS, LiteralExpr(1))]
                )
              )
            )
          ])
        )
      ])
    )
  ]
)
```

## Ограничения

1. **Нет поддержки строк** - только числовые и булевы литералы
2. **Нет break/continue** - упрощение для парсера
3. **Строгие типы** - все типы должны быть явно указаны
4. **Обязательные точки с запятой** - все выражения должны заканчиваться `;`
5. **Обязательные скобки** - все блоки должны быть в `{}`

