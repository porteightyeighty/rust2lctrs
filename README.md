# rust2lctrs

Translate a small fragment of Rust into a **Logically Constrained Term
Rewriting System (LCTRS)** that [Cora](https://github.com/hezzel/cora) can
analyse for termination.

You give it a Rust function; it gives you an `.lctrs` file. Feed that to Cora
and Cora tells you whether the program is guaranteed to terminate.

```rust
// sum.rs
fn sum(a: i8, b: i8) -> i8 {
    let x: i8 = a + b;
    x
}
```

```sh
rust2lctrs sum.rs -o sum.lctrs   # translate
cora sum.lctrs                   # → YES  (termination proved)
```

## What Rust it accepts

A deliberately small, integer/boolean fragment:

- Integer arithmetic (`i8`…`i64`) and booleans
- `let` bindings, `let mut` locals and assignment, variable shadowing
- `if`/`else`, `while`, `loop`/`break`
- Multiple top-level functions with by-value primitive arguments and return
  types, including recursion and mutual recursion

Anything outside that (`for` loops, floats, references/borrows, ownership,
generics, traits, closures, structs, enums, tuples, arrays, strings,
`println!`, macros, `Result`/`?`, non-trivial pattern matching) is **rejected
at parse time** with a diagnostic, not silently mistranslated. Input is
assumed to be valid, `rustc`-compilable Rust.

## Install

Requires **JDK 25**. Download the self-contained jar from the
[latest release](https://github.com/porteightyeighty/rust2lctrs/releases/latest)
and run it:

```sh
java -jar rust2lctrs-0.2.0.jar sum.rs -o sum.lctrs
```

Or build from source with the bundled Maven wrapper:

```sh
./mvnw package        # produces target/rust2lctrs-0.2.0.jar
```

To invoke `rust2lctrs` from any directory, define a shell alias:

```sh
alias rust2lctrs='java -jar /path/to/rust2lctrs-0.2.0.jar'
```

## Usage

```
rust2lctrs <input.rs> [-o <output.lctrs>] [--profile debug|release]
```

| Option | Effect |
|---|---|
| `<input.rs>` | Rust source file to translate (required). |
| `-o`, `--output <file>` | Write the LCTRS to a file. Default: **stdout**. |
| `--profile <debug\|release>` | Arithmetic-overflow semantics (below). Default: `debug`. |
| `--help`, `--version` | Usage / version. |

The LCTRS goes to **stdout** (logs go to stderr), so you can pipe straight
into Cora:

```sh
rust2lctrs input.rs | cora /dev/stdin
```

Exit codes: `0` success, `1` I/O error, `2` out-of-scope Rust, `3` malformed
(unparseable) Rust.

### Overflow profile

`--profile` mirrors rustc's two compilation modes, choosing how integer
overflow of `+ - *` and unary `-` is encoded:

| `--profile` | On overflow | Models |
|---|---|---|
| `debug` (default) | **panics**: rewrites to `err`, guarded by a width bound | `cargo build` |
| `release` | **wraps** two's-complement, `((t − MIN) % 2^w) + MIN` | `cargo build --release` |

This matters for termination: a counter that only terminates *because* it
wraps to negative is `MAYBE` under `debug` (it hits `err` first) but analysable
under `release`. `/` and `%` panic in **both** profiles (division by zero and
`MIN / -1`), so the flag doesn't change their encoding.

```sh
rust2lctrs input.rs --profile release -o out.lctrs
```

## Running Cora on the output

The translated `.lctrs` is Cora's input format. Install
[Cora](https://github.com/hezzel/cora) per its own instructions, then run it on
the output:

```sh
cora out.lctrs           # → YES (terminates) or MAYBE (inconclusive)
```

## Development

The translation pipeline is `Rust → ANTLR parser → AstBuilder → AST →
Translator → LCTRS`. Contributor notes, invariants, and the three-layer test
strategy (unit / snapshot golden files / Cora end-to-end) live in
[`CLAUDE.md`](CLAUDE.md).

```sh
./mvnw test      # unit + snapshot tests (fast)
./mvnw verify    # + formatting and Cora end-to-end (needs Cora on PATH)
```

Logging is SLF4J + Logback on **stderr**, root level `WARN`, overridable with
`LOG_LEVEL` (`TRACE`…`ERROR`). Raising it never corrupts the LCTRS on stdout.
