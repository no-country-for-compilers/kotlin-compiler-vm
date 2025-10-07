# Спецификация синтаксиса языка программирования

## Обзор

Данный документ описывает синтаксис языка программирования, разработанного для учебного проекта компилятора. Язык поддерживает базовые арифметические операции, условные операторы, циклы, рекурсию и строгую типизацию.

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
- `int` - целые числа
- `float` - числа с плавающей точкой
- `bool` - булевы значения
- `string` - строки
- `void` - отсутствие возвращаемого значения
- `int[]` - массив целых чисел
- `float[]` - массив чисел с плавающей точкой
- `string[]` - массив строк

### Операторы
- Арифметические: `+`, `-`, `*`, `/`, `%`
- Сравнения: `==`, `!=`, `<`, `>`, `<=`, `>=`
- Логические: `&&`, `||`, `!`
- Присваивания: `=`

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
let name: string = "Hello";
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
    print("Больше 10");
} else {
    print("Меньше или равно 10");
}
```

### 4. Циклы

#### Простой цикл for
```
for (<initialization>; <condition>; <increment>) {
    <statements>
}
```

#### Цикл for с условием (аналог while)
```
for (<condition>) {
    <statements>
}
```

**Примеры:**
```kotlin
for (let i: int = 0; i < 10; i = i + 1) {
    print(i);
}

let x: int = 10;
for (x > 0) {
    print(x);
    x = x - 1;
}
```

### 5. Функции

```
func <identifier>(<parameters>): <return_type> {
    <statements>
    return <expression>;
}
```

**Примеры:**
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

## Тестовые программы

### 1. Факториал
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

### 2. Сортировка массива (Merge Sort)
```kotlin
func mergeSort(arr: int[]): int[] {
    if (arr.length <= 1) {
        return arr;
    }
    
    let mid: int = arr.length / 2;
    let left: int[] = arr.slice(0, mid);
    let right: int[] = arr.slice(mid, arr.length);
    
    return merge(mergeSort(left), mergeSort(right));
}

func merge(left: int[], right: int[]): int[] {
    let result: int[] = [];
    let i: int = 0;
    let j: int = 0;
    
    while (i < left.length && j < right.length) {
        if (left[i] <= right[j]) {
            result.append(left[i]);
            i = i + 1;
        } else {
            result.append(right[j]);
            j = j + 1;
        }
    }
    
    while (i < left.length) {
        result.append(left[i]);
        i = i + 1;
    }
    
    while (j < right.length) {
        result.append(right[j]);
        j = j + 1;
    }
    
    return result;
}
```

### 3. Простые числа
```kotlin
func isPrime(n: int): bool {
    if (n < 2) {
        return false;
    }
    for (let i: int = 2; i * i <= n; i = i + 1) {
        if (n % i == 0) {
            return false;
        }
    }
    return true;
}

func findPrimes(limit: int): int[] {
    let primes: int[] = [];
    for (let i: int = 2; i <= limit; i = i + 1) {
        if (isPrime(i)) {
            primes.append(i);
        }
    }
    return primes;
}
```

## Особенности синтаксиса

### Обязательные элементы
1. **Точки с запятой** - все выражения должны заканчиваться `;`
2. **Скобки** - все блоки кода должны быть в `{}`
3. **Типы** - все переменные и функции должны иметь явные типы
4. **Ключевые слова** - все конструкции должны начинаться с ключевых слов

### Ограничения
1. **Нет вложенных циклов** - поддерживаются только простые циклы
2. **Нет break/continue** - упрощение для парсера
3. **Нет range циклов** - только for с тремя частями
4. **Нет автовывода типов** - все типы должны быть явными

## Грамматика (BNF)

```
program ::= statement*

statement ::= variable_declaration
            | function_declaration
            | expression_statement
            | if_statement
            | for_statement
            | return_statement

variable_declaration ::= "let" identifier ":" type "=" expression ";"

function_declaration ::= "func" identifier "(" parameters ")" ":" type "{" statement* "}"

expression_statement ::= expression ";"

if_statement ::= "if" "(" expression ")" "{" statement* "}" ["else" "{" statement* "}"]

for_statement ::= "for" "(" [variable_declaration] ";" [expression] ";" [expression] ")" "{" statement* "}"
                | "for" "(" expression ")" "{" statement* "}"

return_statement ::= "return" [expression] ";"

expression ::= assignment_expression

assignment_expression ::= logical_or_expression ["=" assignment_expression]

logical_or_expression ::= logical_and_expression ["||" logical_or_expression]

logical_and_expression ::= equality_expression ["&&" logical_and_expression]

equality_expression ::= relational_expression [("==" | "!=") equality_expression]

relational_expression ::= additive_expression [("<" | ">" | "<=" | ">=") relational_expression]

additive_expression ::= multiplicative_expression [("+" | "-") additive_expression]

multiplicative_expression ::= unary_expression [("*" | "/" | "%") multiplicative_expression]

unary_expression ::= ["!"] primary_expression

primary_expression ::= number
                    | string
                    | boolean
                    | identifier
                    | "(" expression ")"
                    | function_call
                    | array_access

function_call ::= identifier "(" [expression ("," expression)*] ")"

array_access ::= identifier "[" expression "]"

type ::= "int" | "float" | "bool" | "string" | "void" | type "[]"

parameters ::= [parameter ("," parameter)*]

parameter ::= identifier ":" type
```

## Заключение

Данный синтаксис разработан с учетом простоты парсинга и покрывает все требования проекта:
- Базовые арифметические операции
- Условные операторы (if-else)
- Циклы (for)
- Рекурсия (функции)
- Строгая типизация
- Поддержка массивов для тестовых программ
