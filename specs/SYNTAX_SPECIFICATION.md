# Спецификация синтаксиса языка программирования

## Основные принципы

- **Строгая типизация** - все переменные и функции должны иметь явно указанные типы
- **Обязательные точки с запятой** - все выражения должны заканчиваться точкой с запятой
- **Обязательные скобки** - все блоки кода должны быть заключены в фигурные скобки
- **Обязательные ключевые слова** - все конструкции языка должны начинаться с ключевых слов

## Лексические элементы

### Ключевые слова

- `let` - объявление переменной
- `func` - объявление функции
- `if` - условный оператор
- `else` - альтернативная ветка условия
- `for` - цикл
- `return` - возврат значения из функции
- `true`, `false` - булевы литералы

### Типы данных

**Примитивные типы:**

- `int` - 64-битное целое число со знаком (диапазон: -2⁶³ до 2⁶³-1)
- `float` - 64-битное число с плавающей точкой (IEEE 754 double precision)
- `bool` - булево значение (`true` или `false`)
- `void` - отсутствие возвращаемого значения (только для функций)

**Массивы:**

- `int[]` - массив целых чисел
- `float[]` - массив чисел с плавающей точкой
- `int[][]` - многомерные

**Важно:**

- `int` использует 64 бита для поддержки больших вычислений, таких как факториал 20! = 2,432,902,008,176,640,000
- `float` использует двойную точность для обеспечения достаточной точности вычислений
- Все типы должны указываться явно (нет автовывода типов)

### Операторы

- Арифметические: `+`, `-`, `*`, `/`, `%`
- Сравнения: `==`, `!=`, `<`, `>`, `<=`, `>=`
- Логические: `&&`, `||`, `!`
- Присваивания: `=`

### Литералы

**Целочисленные литералы (`int_literal`):**

- Десятичные: `0`, `42`, `1234567890`, `9223372036854775807` (максимальное значение)
- Отрицательные: `-1`, `-42`, `-9223372036854775808` (минимальное значение)

**Литералы с плавающей точкой (`float_literal`):**

- С дробной частью: `3.14`, `0.5`, `-2.71`
- С экспонентой: `1.0e10`, `3.14e-5`, `2e+8`

**Булевы литералы (`bool`):**

- `true` - истина
- `false` - ложь

**Массивные литералы:**

- `[1, 2, 3, 4, 5]` - массив целых чисел
- `[1.0, 2.5, 3.14]` - массив чисел с плавающей точкой
- `[]` - пустой массив

### Разделители

- `;` - точка с запятой (обязательна)
- `{}` - фигурные скобки для блоков кода
- `()` - круглые скобки для условий и вызовов функций
- `[]` - квадратные скобки для массивов
- `:` - двоеточие для указания типа
- `,` - запятая для разделения параметров

## Синтаксические конструкции

### 1. Объявление переменных

```
let <identifier>: <type> = <expression>;
```

**Примеры:**

```kotlin
let x: int = 42;
let y: float = 3.14;
let flag: bool = true;
let bigNumber: int = 2432902008176640000;  // Факториал 20!
let numbers: int[] = [1, 2, 3, 4, 5];
```

### 2. Арифметические выражения

```
<expression> <operator> <expression>
```

**Примеры:**

```kotlin
let a: int = 10 + 5;
let b: int = 20 - 3;
let c: int = 4 * 6;
let d: int = 15 / 3;
let e: int = 7 % 2;
```

### 3. Условные операторы

```
if (<condition>) {
    <statements>
} else {
    <statements>
}
```

**Пример:**

```kotlin
if (x > 10) {
    result = 1;
} else {
    result = 0;
}
```

### 4. Циклы

#### Классический цикл for

```
for (<initialization>; <condition>; <increment>) {
    <statements>
}
```

**Пример:**

```kotlin
for (let i: int = 0; i < 10; i = i + 1) {
    print(i);
}
```

### 5. Функции

```
func <identifier>(<parameters>): <return_type> {
    <statements>
    return <expression>;
}
```

**Пример:**

```kotlin
func factorial(n: int): int {
    if (n <= 1) {
        return 1;
    } else {
        return n * factorial(n - 1);
    }
}

func main(): void {
    let result: int = factorial(20);
    print(result);
}
```

### 6. Массивы

#### Объявление массива

```
let <identifier>: <type>[] = [<elements>];
```

#### Доступ к элементам

```
<array_identifier>[<index>]
```

#### Длина массива

```
<array_identifier>.length
```

**Примеры:**

```kotlin
let numbers: int[] = [1, 2, 3, 4, 5];
let first: int = numbers[0];
let len: int = numbers.length;
```

## Особенности синтаксиса

### Обязательные элементы

1. **Точки с запятой** - все выражения должны заканчиваться `;`
2. **Скобки** - все блоки кода должны быть в `{}`
3. **Типы** - все переменные и функции должны иметь явные типы
4. **Ключевые слова** - все конструкции должны начинаться с ключевых слов

### Ограничение левой части присваивания

Присваивание разрешено только в формах:

```
identifier = expr;
identifier[expr] = expr;
```

Запрещено:

- `obj.field = ...`
- `(arrExpr)[i] = ...`
- цепочки вида `a = b = c`

### Пустые списки аргументов и массивы

Синтаксис допускает:

- пустой список аргументов: `f()`
- пустой массив: `[]`

### Другие ограничения

1. **Нет строк** - язык работает только с числами и булевыми значениями
2. **Нет break/continue** - упрощение для парсера
3. **Нет range циклов** - только for с тремя частями или условием
4. **Нет автовывода типов** - все типы должны быть явными
5. **Нет while** - используйте `for (condition)` вместо `while (condition)`
6. **Нет классов и пользовательских методов** - обращения типа `obj.method(...)` или `obj.field` разрешены только для встроенных методов или полей

## Грамматика (EBNF)

```
program ::= { statement }

statement ::= variable_declaration_statement
            | function_declaration
            | expression_statement
            | if_statement
            | for_statement
            | return_statement

variable_declaration ::= "let" identifier ":" type "=" expression
variable_declaration_statement ::= variable_declaration ";"

function_declaration ::= "func" identifier "(" parameters ")" ":" type "{" { statement } "}"

expression_statement ::= expression ";"

if_statement ::= "if" "(" expression ")" "{" statement* "}" ["else" "{" { statement } "}"]

for_statement ::= "for" "(" [variable_declaration] ";" [expression] ";" [expression] ")" "{" { statement } "}"

return_statement ::= "return" [expression] ";"

expression ::= assignment_expression

assignment_expression ::= [ lvalue "=" ] logical_or_expression
logical_or_expression ::= logical_and_expression {"||" logical_and_expression }
logical_and_expression ::= equality_expression { "&&" equality_expression }
equality_expression ::= relational_expression { ("==" | "!=") relational_expression }
relational_expression ::= additive_expression { ("<" | ">" | "<=" | ">=") additive_expression }
additive_expression ::= multiplicative_expression { ("+" | "-") multiplicative_expression }
multiplicative_expression ::= unary_expression { ("*" | "/" | "%") unary_expression }
unary_expression ::= { "!" | "-" | "+" } postfix_expression
postfix_expression ::= primary_expression { 
                          "(" arg_list ")"
                        | "[" expression "]"
                        | "." identifier [ "(" arg_list ")" ]
                      }

lvalue ::= identifier [ "[" expression "]" ]

arg_list ::= [ expression { "," expression } ]

primary_expression ::= int_literal
                     | float_literal
                     | boolean_literal
                     | identifier
                     | "(" expression ")"
                     | array_literal

array_literal ::= "[" arg_list "]"

type ::= base_type { "[]" }
base_type ::= "int" | "float" | "bool" | "void"

parameters ::= [ parameter { "," parameter } ]
parameter ::= identifier ":" type
```
