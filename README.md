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

- Integer arithmetic (`i8`…`i128`, `u8`…`u128`) and booleans
- `let` bindings, `let mut` locals and assignment, variable shadowing
- `if`/`else`, `while`, `loop`/`break`, `continue`
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
java -jar rust2lctrs-0.4.0.jar sum.rs -o sum.lctrs
```

Or build from source with the bundled Maven wrapper:

```sh
./mvnw package        # produces target/rust2lctrs-0.4.0.jar
```

To invoke `rust2lctrs` from any directory, define a shell alias:

```sh
alias rust2lctrs='java -jar /path/to/rust2lctrs-<version>.jar'
```

## Usage

```
rust2lctrs <input.rs> [-o <output.lctrs>] [--profile debug|release | --unbounded] [-r]
```

| Option | Effect |
|---|---|
| `<input.rs>` | Rust source file to translate (required). |
| `-o`, `--output <file>` | Write the LCTRS to a file. Default: **stdout**. |
| `--profile <debug\|release>` | Arithmetic-overflow semantics (below). Default: `debug`. |
| `--unbounded` | Idealise integers to `Z`: no widths, no overflow (below). Not combinable with `--profile`. |
| `-r`, `--raw` | Emit the rules as the statement-by-statement translation produces them, skipping the simplification passes. |
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

### Unbounded integers

`--unbounded` steps outside rustc's profiles altogether: integers become the
unbounded sort `Z`, so there are no width bounds, no overflow guards and no
wrapping. `/` and `%` still guard `divisor ≠ 0`, which is partiality rather
than width.

It keeps an example readable when overflow isn't its point, at a cost: a
verdict under `--unbounded` describes the idealised model, not the Rust
program. A loop that terminates under `debug` only by overflowing into `err`
may well diverge over `Z`.

```sh
rust2lctrs input.rs --unbounded -o out.lctrs
```

## Running Cora on the output

The translated `.lctrs` is Cora's input format. Install
[Cora](https://github.com/hezzel/cora) per its own instructions, then run it on
the output:

```sh
cora --strategy cbv out.lctrs   # → YES (terminates) or MAYBE (inconclusive)
```

Always pass `--strategy cbv`. Rewriting in this encoding only ever happens at
the root of a configuration, once every argument is already a value, so
call-by-value is the strategy the translation is built for. 

## Results

The corpus in `src/test/resources/benchmarks` has 32 short programs (3–18 lines)
that between them cover every construct in the fragment. Each one is translated
under both profiles and analysed by Cora. A comment marker in each source file
records the expected verdict, and `./mvnw verify` checks it against a local Cora
install.

Cora answers `YES` (termination proved) or `MAYBE` (not proved). It has no
procedure for proving non-termination, so the useful number is how many of the
programs that *do* terminate get proved:

| Profile | Terminating programs | Proved |
|---|---|---|
| `debug` | 29 of 32 | 28 |
| `release` | 26 of 32 | 21 |

| Family | Programs | Terminate (debug / release) | Proved (debug / release) |
|---|---|---|---|
| Arithmetic and conditionals | 8 | 8 / 8 | 8 / 8 |
| Shadowing | 3 | 3 / 3 | 3 / 3 |
| Division and modulo | 5 | 5 / 4 | 5 / 4 |
| Loops and control flow | 7 | 6 / 6 | 6 / 4 |
| Overflow and wrapping | 4 | 4 / 2 | 4 / 1 |
| Calls and recursion | 3 | 3 / 3 | 2 / 1 |
| Divergence | 2 | 0 / 0 | 0 / 0 |
| **Total** | **32** | **29 / 26** | **28 / 21** |

**The overflow profile changes the answer.** Seven programs are proved under
`debug` but not under `release`:

- `while_div`, `wrap_negate` and `wrap_parity` no longer terminate. With
  wrapping arithmetic they can run forever, so `MAYBE` is correct.
- `count_up`, `sum_skip`, `wrap_count` and `even_odd` still terminate but aren't
  proved. Under `debug`, a value that leaves its type's range panics (rewrites
  to `err`), and that bound gives Cora a decreasing measure for free. Under
  `release` the bound is gone, so the loop's own encoding has to carry the
  argument.

So the `debug` figure partly reflects the overflow modelling, and `release` is
the harder test of the encoding. The only terminating program not proved under
`debug` is `mccarthy` (the McCarthy 91 function), whose termination argument
needs a ranking over nested recursive calls. That is a limit of Cora's search,
not of the translation.

## Web demo

A small HTTP API wraps the same pipeline, for the [rust2lctrs-web](https://github.com/porteightyeighty/rust2lctrs-web) demo. The CLI remains the primary artifact.

Build the jar and run the server on `PORT` (default `8080`):

```sh
./mvnw package
java -cp target/rust2lctrs-0.4.0.jar project.web.Demo
```

| Route | Response |
|---|---|
| `GET /health` | `200` `ok` |
| `POST /v1/translate?semantics=debug\|release\|unbounded&raw=1` | body is Rust source; `200` LCTRS, `422` diagnostics, `400` syntax error, `413` body over 64 KiB |

`ALLOWED_ORIGIN` sets `Access-Control-Allow-Origin` (default `*`). The server binds `0.0.0.0`.

## Development

The translation pipeline is `Rust → ANTLR parser → AstBuilder → AST →
Translator → LCTRS`. Tests run in three layers: unit tests per translation
rule, snapshot golden files, and Cora end-to-end.

```sh
./mvnw test      # unit + snapshot tests (fast)
./mvnw verify    # + formatting and Cora end-to-end (needs Cora installed)
```

The Cora layer skips itself when no binary is found, so `verify` passes
without one. Point `CORA_BIN` at an install (or put `cora` on `PATH`) to
actually run it:

```sh
CORA_BIN=/path/to/cora/app/build/install/app/bin/app ./mvnw verify
```

Logging is SLF4J + Logback on **stderr**, root level `WARN`, overridable with
`LOG_LEVEL` (`TRACE`…`ERROR`). Raising it never corrupts the LCTRS on stdout.
