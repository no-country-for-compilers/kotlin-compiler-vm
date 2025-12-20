# Спецификация семантического анализатора

## Обзор

Семантический анализатор проверяет корректность программы с точки зрения семантики языка: типы, области видимости, разрешение имен, корректность вызовов функций и т.д.

## Архитектура

### Входные данные

- `Program` - AST программы, полученный от парсера

### Выходные данные

- `AnalysisResult` - результат анализа, содержащий:
  - `program: Program` - исходная программа
  - `globalScope: Scope` - глобальная таблица символов
  - `error: SemanticError?` - ошибка (null при успешном анализе)

### Обработка ошибок

При обнаружении **первой** семантической ошибки анализатор немедленно выбрасывает `SemanticException`, прерывая дальнейший анализ. Это позволяет быстро сообщить пользователю о проблеме.

```kotlin
class SemanticException(
    val pos: SourcePos?,
    message: String
) : RuntimeException(...)
```

## Таблица символов (Symbol Table)

### Структура Scope

Каждая область видимости содержит:
- Таблицу переменных (`variables: Map<String, VariableSymbol>`)
- Таблицу функций (`functions: Map<String, FunctionSymbol>`)
- Ссылку на родительскую область (`parent: Scope?`)

**Примечание:** В языке не поддерживается перегрузка функций - каждая функция должна иметь уникальное имя в своей области видимости.

### Иерархия областей видимости

```
Global Scope (содержит встроенные функции)
  ├─ Function Scope (factorial)
  │    └─ Block Scope (if then)
  └─ Function Scope (main)
       └─ Block Scope (for loop)
```

### Операции с областями видимости

#### defineVariable(symbol: VariableSymbol): Boolean
Добавляет переменную в текущую область. Возвращает `false`, если переменная уже определена в этой области.

#### defineFunction(symbol: FunctionSymbol): Boolean
Добавляет функцию в текущую область. Возвращает `false`, если функция с таким именем уже определена в этой области.

#### resolveVariable(name: String): VariableSymbol?
Ищет переменную, начиная с текущей области и поднимаясь по цепочке родительских областей.

#### resolveFunction(name: String): FunctionSymbol?
Ищет функцию по имени, начиная с текущей области и поднимаясь по цепочке родительских областей.

## Типы данных

### Семантические типы

```kotlin
sealed class Type {
    object Int : Type()        // 64-bit целое число
    object Float : Type()      // 64-bit число с плавающей точкой
    object Bool : Type()       // Булево значение
    object Void : Type()       // Отсутствие значения
    data class Array(val elementType: Type) : Type()  // Массив
    object Unknown : Type()    // Неизвестный тип (при ошибках)
    
    // Специальные типы для встроенных функций (не используются в пользовательском коде)
    object Primitive : Type()  // Любой примитивный тип (int, float, bool)
    object AnyArray : Type()  // Любой массив (int[], float[], bool[])
}
```

**Примечание:** Типы `Primitive` и `AnyArray` используются только для определения встроенных функций (например, `print`) и не могут быть использованы в пользовательском коде. Они позволяют определить одну функцию для всех примитивных типов и одну функцию для всех массивов, вместо создания отдельных перегрузок для каждого типа.

### Символы

```kotlin
sealed class Symbol {
    abstract val name: String
}

data class VariableSymbol(
    override val name: String,
    val type: Type
) : Symbol()

data class FunctionSymbol(
    override val name: String,
    val parameters: List<VariableSymbol>,
    val returnType: Type
) : Symbol()
```

## Проверки семантического анализатора

### 1. Проверка типов (Type Checking)

#### Правила присваивания

Тип правой части должен **точно совпадать** с типом левой части:

```kotlin
// ✅ Корректно
let x: int = 5;           // int = int
let y: float = 3.14;      // float = float
let z: bool = true;       // bool = bool

// ❌ Ошибки
let x: int = 3.14;        // int ≠ float
let y: float = 5;         // float ≠ int
let z: bool = 1;          // bool ≠ int
```

#### Арифметические операции

Оба операнда должны иметь **одинаковый числовой тип**:

```kotlin
// ✅ Корректно
let a: int = 5 + 3;       // int + int → int
let b: float = 1.0 + 2.0; // float + float → float

// ❌ Ошибки
let c: int = 5 + 3.0;     // int + float → ошибка
let d: float = 1.0 + 2;   // float + int → ошибка
```

**Допустимые операции:**
- `int ∘ int → int` (где ∘ ∈ {+, -, *, /, %})
- `float ∘ float → float` (где ∘ ∈ {+, -, *, /})
- `int % int → int` (только для остатка от деления)

#### Оператор остатка от деления (%)

Работает **только** с целыми числами:

```kotlin
// ✅ Корректно
let x: int = 10 % 3;      // int % int → int

// ❌ Ошибки
let y: int = 10.0 % 3.0;  // float % float → ошибка
let z: int = 10 % 3.0;    // int % float → ошибка
```

#### Операции сравнения

Оба операнда должны иметь **одинаковый числовой тип**:

```kotlin
// ✅ Корректно
let a: bool = 5 < 10;           // int < int → bool
let b: bool = 3.14 > 2.71;      // float > float → bool

// ❌ Ошибки
let c: bool = 5 < 3.14;         // int < float → ошибка
```

**Допустимые операции:**
- `<, <=, >, >=`: `int ∘ int → bool`, `float ∘ float → bool`

#### Операции равенства

Работают с **сравнимыми типами** (одинаковые типы или один из них Unknown):

```kotlin
// ✅ Корректно
let a: bool = 5 == 5;           // int == int → bool
let b: bool = 3.14 == 2.71;    // float == float → bool
let c: bool = true == false;   // bool == bool → bool

// ❌ Ошибки
let d: bool = 5 == 3.14;       // int == float → ошибка
```

**Допустимые операции:**
- `==, !=`: любые одинаковые типы → `bool`

#### Логические операции

Оба операнда должны быть типа `bool`:

```kotlin
// ✅ Корректно
let a: bool = true && false;   // bool && bool → bool
let b: bool = true || false;   // bool || bool → bool

// ❌ Ошибки
let c: bool = 5 && 10;         // int && int → ошибка
let d: bool = true && 5;       // bool && int → ошибка
```

#### Унарные операции

**Унарный минус/плюс:**
- Применимы к `int` и `float`
- Результат имеет тот же тип, что и операнд

**Логическое отрицание (`!`):**
- Применимо только к `bool`
- Результат: `bool`

```kotlin
// ✅ Корректно
let a: int = -5;              // -int → int
let b: float = -3.14;        // -float → float
let c: bool = !true;         // !bool → bool

// ❌ Ошибки
let d: int = -true;          // -bool → ошибка
let e: bool = !5;            // !int → ошибка
```

### 2. Разрешение имен (Name Resolution)

#### Проверка неопределенных переменных

```kotlin
// ❌ Ошибка
let x: int = y + 1;  // Error: Undefined variable 'y'
```

Анализатор проверяет, что все используемые переменные определены в текущей или родительской области видимости.

#### Проверка неопределенных функций

```kotlin
// ❌ Ошибка
let x: int = foo(5);  // Error: Undefined function 'foo'
```

#### Различение переменных и функций

Переменная и функция с одинаковым именем могут сосуществовать в одной области, но:
- Переменная не может использоваться как функция
- Функция не может использоваться как переменная

```kotlin
// ❌ Ошибки
func foo(): int { return 5; }
let x: int = foo;        // Error: Cannot use function 'foo' as a value

let bar: int = 10;
let y: int = bar();      // Error: Cannot call non-function 'bar'
```

### 3. Управление областями видимости (Scope Management)

#### Shadowing (перекрытие идентификаторов)

Разрешено переопределение переменных во вложенных областях:

```kotlin
// ✅ Корректно
let x: int = 10;
{
    let x: int = 20;  // Перекрывает внешнюю x
    // здесь x = 20
}
// здесь x = 10
```

#### Запрет дублирования в одной области

```kotlin
// ❌ Ошибка
let x: int = 10;
let x: int = 20;  // Error: Variable 'x' is already defined in this scope
```

#### Области видимости функций

Параметры функции доступны только внутри тела функции:

```kotlin
func foo(x: int): void {
    // x доступен здесь
}
// x недоступен здесь
```

### 4. Проверка функций

#### Дублирование параметров

```kotlin
// ❌ Ошибка
func foo(x: int, x: int): void { }  
// Error: Duplicate parameter name 'x' in function 'foo'
```

#### Дублирование функций

Функции с одинаковым именем не допускаются, даже если типы параметров разные:

```kotlin
// ❌ Ошибка
func foo(): void { }
func foo(): void { }  
// Error: Function 'foo' is already defined in this scope

// ❌ Ошибка - перегрузка не поддерживается
func foo(x: int): void { }
func foo(x: float): void { }  
// Error: Function 'foo' is already defined in this scope
```

#### Проверка количества аргументов

```kotlin
func foo(x: int, y: int): int { return x + y; }

// ✅ Корректно
let a: int = foo(1, 2);

// ❌ Ошибка
let b: int = foo(1);  
// Error: Function 'foo' expects 2 arguments but got 1
```

#### Проверка типов аргументов

```kotlin
func foo(x: int): int { return x; }

// ✅ Корректно
let a: int = foo(5);

// ❌ Ошибка
let b: int = foo(3.14);  
// Error: Type mismatch for argument 1 of function 'foo': expected 'int', got 'float'
```

### 5. Встроенные функции

Язык предоставляет встроенную функцию `print` для вывода значений в стандартный поток вывода.

#### Встроенные функции print и printArray

Язык предоставляет две встроенные функции для вывода значений:

```kotlin
// Печать примитивных типов (int, float, bool)
print(value: primitive): void

// Печать массивов (int[], float[], bool[])
printArray(value: array): void
```

**Примечание:** Типы `primitive` и `array` являются специальными типами для встроенных функций. Они не могут быть использованы в пользовательском коде, но позволяют функциям принимать любой примитивный тип или любой массив соответственно.

**Примеры использования:**

```kotlin
// ✅ Корректно
func main(): void {
    print(42);              // Печать int
    print(3.14);            // Печать float
    print(true);            // Печать bool
    
    let arr: int[] = int[5];
    printArray(arr);        // Печать массива int[]
    
    let floatArr: float[] = float[3];
    printArray(floatArr);   // Печать массива float[]
}
```

**Поведение при анализе:**

- Функция `print` автоматически добавляется в глобальную область видимости при инициализации анализатора
- При вызове `print(...)` анализатор находит подходящую перегрузку по типу аргумента
- Если тип аргумента не совпадает ни с одной перегрузкой, выдается ошибка типа

```kotlin
// ❌ Ошибка
func main(): void {
    print();  // Error: Function 'print' expects 1 arguments but got 0
    print(1, 2);  // Error: Function 'print' expects 1 arguments but got 2
}
```

### 6. Проверка оператора return

#### Return вне функции

```kotlin
// ❌ Ошибка
return 5;  // Error: Return statement is not allowed outside of a function
```

#### Return в void-функции

```kotlin
// ✅ Корректно
func foo(): void {
    return;  // Без значения
}

// ❌ Ошибка
func bar(): void {
    return 42;  
    // Error: Return statement in function 'bar' of void type must not return a value
}
```

#### Return в не-void функции

```kotlin
// ✅ Корректно
func foo(): int {
    return 42;  // С значением типа int
}

// ❌ Ошибка
func bar(): int {
    return;  
    // Error: Missing return value in function 'bar' of non-void type 'int'
}
```

#### Проверка типа возвращаемого значения

```kotlin
func foo(): int {
    return true;  
    // Error: Return type mismatch in function 'foo': expected 'int', got 'bool'
}
```

### 7. Проверка массивов

#### Типы элементов массива

Все элементы массива должны иметь **одинаковый тип**:

```kotlin
// ✅ Корректно
let arr: int[] = [1, 2, 3, 4, 5];

// ❌ Ошибка
let arr: int[] = [1, 2.0, 3];  
// Error: Array literal elements must have the same type, but found 'int' and 'float'
```

#### Индексация массивов

Индекс должен быть типа `int`:

```kotlin
// ✅ Корректно
let arr: int[] = [1, 2, 3];
let x: int = arr[0];

// ❌ Ошибка
let y: int = arr[3.14];  
// Error: Index expression must be of type 'int', got 'float'
```

#### Индексация не-массивов

```kotlin
// ❌ Ошибка
let x: int = 5;
let y: int = x[0];  
// Error: Cannot index value of non-array type 'int'
```

#### Присваивание в массивы

```kotlin
// ✅ Корректно
let arr: int[] = [1, 2, 3];
arr[0] = 10;

// ❌ Ошибка
let arr: int[] = [1, 2, 3];
arr[0] = 3.14;  
// Error: Type mismatch in assignment: cannot assign value of type 'float' to target of type 'int'
```

### 8. Проверка условий

#### Условие в if

Должно быть типа `bool`:

```kotlin
// ✅ Корректно
if (true) { }
if (x > 5) { }

// ❌ Ошибка
if (5) { }  
// Error: If condition must be of type 'bool', got 'int'
```

#### Условие в for

Должно быть типа `bool` (если указано):

```kotlin
// ✅ Корректно
for (let i: int = 0; i < 10; i = i + 1) { }

// ❌ Ошибка
for (let i: int = 0; i; i = i + 1) { }  
// Error: For-loop condition must be of type 'bool', got 'int'
```

### 9. Проверка свойств

Доступ к свойствам через точку пока не поддерживается:

```kotlin
// ❌ Ошибка
let x: int = arr.length;  
// Error: Property access is not supported yet (found '.length')
```

## Алгоритм анализа

### Обход AST

Анализатор обходит AST в порядке объявлений:

1. **Глобальные декларации** - функции и переменные на верхнем уровне
2. **Тела функций** - проверка параметров, локальных переменных, операторов
3. **Выражения** - проверка типов и разрешение имен

### Последовательность проверок

Для каждого узла AST выполняются проверки в следующем порядке:

1. **Разрешение имен** - проверка, что все идентификаторы определены
2. **Проверка типов** - проверка совместимости типов
3. **Проверка областей видимости** - проверка правильности использования переменных
4. **Специфичные проверки** - проверка return, условий, массивов и т.д.

## Интерфейс

```kotlin
interface SemanticAnalyzer {
    fun analyze(program: Program): AnalysisResult
}

data class AnalysisResult(
    val program: Program,
    val globalScope: Scope,
    val error: SemanticError?  // null при успешном анализе
)
```

## Примеры проверок

### Пример 1: Корректная программа

```kotlin
func factorial(n: int): int {
    if (n <= 1) {
        return 1;
    } else {
        return n * factorial(n - 1);
    }
}

func main(): void {
    let x: int = factorial(5);
}
```

**Результат:** Анализ успешен, `error = null`

### Пример 2: Ошибка типа

```kotlin
func main(): void {
    let x: int = 3.14;  // Ошибка: int ≠ float
}
```

**Результат:** `SemanticException("Type mismatch: cannot assign value of type 'float' to variable 'x' of type 'int'")`

### Пример 3: Неопределенная переменная

```kotlin
func main(): void {
    let x: int = y + 1;  // Ошибка: 'y' не определена
}
```

**Результат:** `SemanticException("Undefined variable 'y'")`

### Пример 4: Неправильный return

```kotlin
func foo(): int {
    return;  // Ошибка: отсутствует значение
}
```

**Результат:** `SemanticException("Missing return value in function 'foo' of non-void type 'int'")`

## Ограничения

1. **Нет автовывода типов** - все типы должны быть явно указаны
2. **Строгая типизация** - нет неявных преобразований типов
3. **Нет перегрузки функций** - функции с одинаковым именем не допускаются
4. **Нет рекурсивных типов** - массивы не могут содержать сами себя
5. **Property access не поддерживается** - доступ к свойствам через точку пока не реализован


