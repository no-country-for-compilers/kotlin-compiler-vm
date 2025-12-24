# Спецификация модуля памяти (Heap + Reference Counting GC)

## Обзор

Модуль памяти отвечает за:
- хранение динамически выделяемых объектов (куча / Heap)
- управление временем жизни объектов по алгоритму **Reference Counting**
- предоставление API для инструкций, работающих с кучей

**Ограничение:** Reference Counting не собирает циклы ссылок

## Термины

- **Value** - значение в VM (на operand stack или в locals)
- **ArrayRef(heapId)** - ссылочное значение на объект в куче
- **Heap** - хранилище объектов по `heapId`
- **HeapObject** - объект в куче с `refCount`
- **retain** - увеличить счётчик ссылок
- **release** - уменьшить счётчик ссылок и освободить объект при достижении 0

## Представление значений

Для памяти важен только ссылочный вариант:
- `ArrayRef(heapId: Int)` — ссылка на объект в куче

Примитивы (`IntValue`, `FloatValue`, `BoolValue`, `VoidValue`) не участвуют в GC.

## Heap (куча)

### Назначение

Heap хранит все динамически выделяемые объекты и выдаёт им идентификаторы `heapId`.

### Инварианты heapId

- `heapId` — целое положительное число
- `heapId` уникален в рамках одного `Heap`
- `ArrayRef(heapId)` считается валидным только если объект существует в heap

### Типы объектов в куче

Поддерживаются только массивы примитивов:
- `int[]` (хранит `LongArray`)
- `float[]` (хранит `DoubleArray`)
- `bool[]` (хранит `BooleanArray`)

Каждый объект содержит:
- `refCount: Int` — счётчик ссылок
- данные массива

### Семантика аллокации

- `allocIntArray(size)` - создаёт массив длины `size`, заполняет нулями
- `allocFloatArray(size)` - аналогично
- `allocBoolArray(size)` - создаёт массив, заполняет `false`

После `alloc*` объект существует в heap с `refCount = 0`. Поднятие `refCount` — ответственность GC/MemoryManager через `retain`.

### Семантика освобождения

- `free(heapId)` - удаляет объект из heap
- Повторный `free` или `free` несуществующего id → ошибка выполнения

## Reference Counting GC

### Операции

- **retain(value):**
  - если `value` = `ArrayRef(id)` → `heap[id].refCount++`
  - иначе no-op

- **release(value):**
  - если `value` = `ArrayRef(id)` → `heap[id].refCount--`
  - если `refCount` стал 0 → `free(id)`
  - если `refCount` стал отрицательным → ошибка выполнения
  - если `id` не существует → ошибка выполнения

### Инвариант времени жизни

- Объект может существовать в heap только если `refCount > 0`
- Освобождение происходит немедленно при переходе `refCount: 1 -> 0`

## MemoryManager

### Назначение

MemoryManager объединяет Heap и GC и предоставляет высокоуровневые операции для интерпретатора.

### API

- `newIntArray(size: Int): ArrayRef` - создаёт массив, возвращает `ArrayRef(id)` с **refCount = 1**
- `newFloatArray(size: Int): ArrayRef` - аналогично
- `newBoolArray(size: Int): ArrayRef` - аналогично
- `intArrayLoad(ref: ArrayRef, index: Int): Long`
- `intArrayStore(ref: ArrayRef, index: Int, value: Long)`
- `floatArrayLoad(ref: ArrayRef, index: Int): Double`
- `floatArrayStore(ref: ArrayRef, index: Int, value: Double)`
- `boolArrayLoad(ref: ArrayRef, index: Int): Boolean`
- `boolArrayStore(ref: ArrayRef, index: Int, value: Boolean)`
- `retain(value: Value)`
- `release(value: Value)`

### Bounds-check для массивов

Выход за границы массива приводит к падению VM:
- `index < 0` или `index >= size` → ошибка выполнения

## Правила владения (ownership) для корректного RC

### Где хранятся "корни" (root owners)

Владельцами ссылок считаются:
- operand stack
- locals в call frame

### Copy vs Move

- **Copy** (копирование ссылки): появляется новый владелец → нужно `retain`
- **Move** (перемещение владения): владелец меняется, но общее число владельцев не растёт → `retain` не нужен

## Интеграция с инструкциями байткода

### NEW_ARRAY_INT (0x90)

1. Снять `int_size` со стека (move)
2. `array_ref = memory.newIntArray(size)` (возвращает refCount=1)
3. Положить `array_ref` на стек (move)

### ARRAY_LOAD (0x92)

Для массивов примитивов: никаких retain/release не происходит.

### ARRAY_STORE (0x93)

Для массивов примитивов: никаких retain/release не происходит.

## Правила RC для базовых VM-операций

### LOAD_LOCAL (0x10)

Семантика: COPY из locals в стек → если значение `ArrayRef`, нужно `retain` перед помещением на стек.

### STORE_LOCAL (0x11)

Семантика: MOVE из стека в locals → `retain` не нужен, но если в `locals[i]` было старое значение, его нужно `release` перед заменой.

### POP (0x04)

Семантика: DROP значение → `release` значения.

### CALL (0x80)

Семантика: MOVE аргументов → retain не нужен, стек теряет владение, locals получают владение.

### RETURN (0x81)

Семантика: MOVE возвращаемого значения → retain не нужен.

### Завершение фрейма

При выходе из функции (RETURN/RETURN_VOID) VM должна освободить все локалы текущего фрейма:
- Пройти по `locals[]` и выполнить `release` для всех `ArrayRef` значений.

## Ошибки выполнения

Модуль памяти приводит к падению VM при:
- `release` несуществующего `heapId`
- `release`, приводящий к `refCount < 0`
- двойной `free`
- выход за границы массива при `ARRAY_LOAD/ARRAY_STORE`
- отрицательный размер массива при `NEW_ARRAY_*`
