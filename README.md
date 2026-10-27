<p align="center"><img src="docs/logo.svg" width="96" alt="" /></p>

# jvmsig

jvmsig is an offline Kotlin/JVM command-line tool for reading JVM type descriptors and generic signatures—the strings printed by tools such as `javap -s -v`, and found in bytecode, stack traces, JNI headers, reflection dumps, and Kotlin metadata. It is for people working with bytecode and native-method code who want to see what a signature means. It provides Java-style or Kotlin-like display, signature-file checking, generic erasure, method argument-slot tables, and JNI native-method names. It uses only the Kotlin and Java standard libraries and never accesses the network.

[![CI](https://github.com/Gulliby-development/jvmsig/actions/workflows/ci.yml/badge.svg)](https://github.com/Gulliby-development/jvmsig/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## Build and install

Build and install the command-line distribution with:

```sh
gradle installDist
```

The installed launcher is `build/install/jvmsig/bin/jvmsig` on Unix-like systems and `build/install/jvmsig/bin/jvmsig.bat` on Windows. In the examples below, `jvmsig` means the installed launcher; run it from its installation directory or add that directory to `PATH`.

To run the tests during development, use:

```sh
gradle test
```

The command transcripts below use POSIX shell syntax. In particular, `printf` and single-quoted signature arguments work in a POSIX-compatible shell; on Windows, use Git Bash for these fixture commands, or invoke `jvmsig.bat` from Command Prompt or PowerShell. The program's text streams are UTF-8 and its output lines end in LF on every platform.

## Commands

```text
jvmsig show [--kotlin] [--short] [--name NAME] SIGNATURE...
jvmsig check [--var NAME]... FILE...
jvmsig erase SIGNATURE...
jvmsig slots [--static] SIGNATURE...
jvmsig jni CLASS METHOD [DESCRIPTOR]
jvmsig --version
```

- **`show`** reads each field, method, or class signature and displays Java-style text by default. `--kotlin` selects a Kotlin-like display form; it is for reading signatures, not necessarily compilable Kotlin. `--short` drops the package path from the first class name, and `--name NAME` supplies a method name in the display.
- **`check`** reads signatures from files, one signature per non-empty, non-comment line, and reports syntax and semantic problems. A line beginning with `#` is a comment. Repeat `--var NAME` to declare enclosing type variables that are not declared by the signature itself.
- **`erase`** prints the JVM descriptor after generic type erasure. Type variables use the first bound declared in the same signature, or `java/lang/Object` when no usable bound is available.
- **`slots`** accepts method signatures and prints the local-variable slots used by the receiver and arguments, along with erased types and the return type. The receiver occupies slot 0 unless `--static` is given; `long` and `double` arguments occupy two slots.
- **`jni`** prints the JNI C symbol for `CLASS` and `METHOD`. Supplying the optional method `DESCRIPTOR` adds the overloaded-name suffix derived from its erased parameter types.

`jvmsig --version` (or `-V`), as the first argument, prints `jvmsig` and the project version on one line and exits with status `0`:

```text
$ jvmsig --version
jvmsig 0.1.0
```

The version is the `version` of `build.gradle.kts`; the build copies it into the `version.properties` resource, from which the program reads it.

Exit status is `0` when the requested work succeeds, `1` when one or more signatures have problems, and `2` for command-line errors or unreadable/undecodable input. Signature errors include the operand or file line, a one-based column, and a diagnostic code.

### Examples

Java display:

```text
$ jvmsig show I
int
```

Kotlin-like display:

```text
$ jvmsig show --kotlin '[Ljava/lang/String;'
Array<java.lang.String>
```

Check a file created with `printf`:

```text
$ printf 'I\n' > ok.txt
$ jvmsig check ok.txt
checked 1 signature, 0 problems
```

Erase an undeclared type variable:

```text
$ jvmsig erase 'TT;'
Ljava/lang/Object;
```

Show the slots of a static method with no arguments:

```text
$ jvmsig slots --static '()V'
SLOT  ARG  TYPE
total 0 slots
returns void
```

Print a JNI symbol:

```text
$ jvmsig jni com.acme.Foo run
Java_com_acme_Foo_run
```

## Diagnostics

Columns are one-based Unicode code-point positions, so a supplementary character counts as one column. `CHAR` stands for the unexpected code point: it is shown in single quotes unless it is a space or control character, which is written as uppercase `U+` hexadecimal with at least four digits. `WHAT` is the expected grammar item, using the fixed descriptions in the table. `NAME` is the affected type-variable or type-parameter name. Syntax diagnostics stop at the first reading problem; `check` additionally applies the semantic diagnostics JS012, JS013, and JS015.

| Code | Message template | Column and trigger |
| --- | --- | --- |
| JS001 | `unexpected end, expected WHAT` | One past the last code point when the signature ends where more input is required. |
| JS002 | `unexpected CHAR, expected WHAT` | At the wrong code point where one specific character is required. |
| JS003 | `not a type: CHAR` | At a code point that cannot start a type where a type is required. |
| JS004 | `void is only allowed as a return type` | At `V` in any position other than a method return type. |
| JS005 | `empty name` | Where a name is required but a name-ending delimiter appears; end of input instead gives JS001. |
| JS006 | `empty type argument list` | At `>` in an empty class type-argument list, `<>`. |
| JS007 | `more than 255 array dimensions` | At the 256th `[` in an array type. |
| JS008 | `unexpected text after the signature` | At the first code point after a complete signature. |
| JS009 | `empty type parameter list` | At `>` in an empty leading type-parameter list, `<>`. |
| JS010 | `a wildcard bound must be a reference type` | At a primitive type following `+` or `-` in a wildcard bound. |
| JS011 | `a type argument must be a reference type` | At a primitive used as a type argument. |
| JS012 | `type variable NAME is not declared` | At the `T` starting an undeclared type-variable use; `check` only. |
| JS013 | `type parameter NAME is declared twice` | At the name of the second or later declaration; `check` only. |
| JS014 | `a thrown type must be a class or a type variable` | After `^` when the next type is neither a class nor a type variable (`V` instead gives JS004). |
| JS015 | `type parameter NAME has a cyclic bound` | At the name of a type parameter whose first-bound chain returns to itself; `check` only. |
| JS016 | `a method signature is required` | At column 1 when `slots`, or `jni` with a descriptor, receives a non-method signature. |
| JS017 | `more than 255 argument slots` | At column 1 when `slots` would use more than 255 slots in total. |

For JS001 and JS002, `WHAT` is one of: `a name`, `a type`, `';'`, `'>'`, `':'`, `')'`, `a return type`, `a class or type variable`, or `'(' or the superclass`, as appropriate to the grammar position. A required character appears in single quotes. `CHAR` uses `U+XXXX` notation for code points at or below U+0020 and U+007F through U+009F; other code points appear in single quotes.

## Development

Run the test suite with `gradle test`. The project is designed for Windows, macOS, and Linux; the installed launcher and UTF-8/LF protocol are provided for each platform.

## License

MIT, see [LICENSE](LICENSE).

---

Built with BAKED
