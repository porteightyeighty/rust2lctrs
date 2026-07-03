# rust2lctrs

A Java tool that translates a restricted fragment of Rust into Logically
Constrained Term Rewriting Systems (LCTRSs), so that
[Cora](https://github.com/hezzel/cora) can analyse termination.

## Pipeline

```
Rust source → ANTLR parser → AstBuilder → custom AST → Translator → LCTRS → Cora input
```

## Build & run

Uses the maven wrapper (`./mvnw`).

| Command | What it does |
|---|---|
| `./mvnw compile` | Regenerate parser + compile |
| `./mvnw test` | Unit + snapshot tests |
| `./mvnw verify` | The above plus formatting and e2e |
| `./mvnw package` | Build a self-contained jar under `target/` |

The CLI takes a Rust source file and writes the serialised LCTRS to **stdout**,
or to a file with `-o`. Logs go to **stderr**, so stdout stays a clean LCTRS:

```sh
# via the packaged jar
java -jar target/rust2lctrs-1.0-SNAPSHOT.jar input.rs > out.lctrs

# or straight from Maven
./mvnw -q exec:java -Dexec.mainClass=project.Main -Dexec.args="input.rs" > out.lctrs

# write to a file instead of stdout
java -jar target/rust2lctrs-1.0-SNAPSHOT.jar input.rs -o out.lctrs
```

Run with `--help` for the option list. Out-of-scope Rust exits non-zero with a
diagnostic on stderr.

### Overflow profile

`--profile` picks the arithmetic-overflow semantics to encode, mirroring
rustc's two compilation modes:

| `--profile` | Overflow of `+ - * ` and unary `-` | Models |
|---|---|---|
| `debug` (default) | **panics** — rewrites to `err`, guarded by a width bound | `cargo build` |
| `release` | **wraps** two's-complement — `((t − MIN) % 2^w) + MIN` | `cargo build --release` |

```sh
java -jar target/rust2lctrs-1.0-SNAPSHOT.jar input.rs --profile release > out.lctrs
```

`/` and `%` panic in **both** profiles (division by zero and `MIN / -1`), so
their encoding is unchanged by the flag. The default is `debug`, so output is
byte-for-byte identical to omitting the flag.

## Benchmark corpus & snapshot tests

A single corpus under `src/test/resources/benchmarks/` drives two of the three
test layers. Each benchmark is a pair of sibling files plus a one-line marker:

```
src/test/resources/benchmarks/
  sum.rs        # input; a // cora: line declares the expected Cora verdict
  sum.lctrs     # committed golden — the serialised LCTRS, diff-reviewed
```

`sum.rs`:

```rust
// cora: YES
fn sum(a: i8, b: i8) -> i8 {
    let x: i8 = a + b;
    x
}
```

- **Snapshot layer** (`project.snapshot.SnapshotTest`, runs in `./mvnw test`):
  translates each `.rs` and asserts it equals the committed `.lctrs` golden.
  This catches whole-program ripple effects that the per-rule unit tests miss.
- **E2E layer** (`project.e2e.CoraE2EIT`, runs in `./mvnw verify`): feeds the
  same committed golden to Cora and asserts the `// cora:` verdict. Because the
  *golden* is analysed — not a fresh translation — the snapshot layer pins the
  artifact and the e2e layer proves that pinned artifact is sound.

A benchmark can also carry a `// profile: release` line to translate it under
the wrapping overflow semantics (see [Overflow profile](#overflow-profile));
omitting it defaults to `debug`. For example `wrap_count.rs` — an `i16` counter
that terminates *only because* it wraps negative, where the debug translation
would instead reach `err`:

```rust
// profile: release
// cora: MAYBE
fn wrap_count(x: i16) -> i16 {
    let mut y: i16 = x;
    while y > 0 {
        y = y + 1;
    }
    y
}
```

### Adding a benchmark

1. Drop `name.rs` into `src/test/resources/benchmarks/`. Add a `// cora: YES`
   (or `MAYBE` / `NO`) line if you want it checked end-to-end; omit the marker
   to make it snapshot-only. Add a `// profile: release` line to translate it
   under wrapping overflow (default is `debug`).
2. Generate its golden and **review the diff** before committing:

   ```sh
   ./mvnw test -Dsnapshot.update=true   # rewrites *.lctrs goldens from current output
   git diff src/test/resources/benchmarks
   ```

   The `-Dsnapshot.update=true` flag is the only way goldens change — a plain
   `./mvnw test` never rewrites them, it fails on drift. Regenerating is a
   deliberate, reviewed act, not a blind update.
3. `./mvnw verify` (with Cora installed) then exercises the verdict.

## Running Cora locally

The end-to-end tests (and any manual checking) need a working
[Cora](https://github.com/hezzel/cora) install. Cora is a separate Gradle
project; it is **not** vendored here, so install it once alongside this repo.

### Prerequisites

| Dependency | Needed | Notes |
|---|---|---|
| JDK ≥ 22.0.1 | yes | The JDK 25 this project uses is fine. |
| Z3 ≥ 4.13.0 | yes | `z3` must be on `PATH`. `brew install z3` (macOS) or your package manager. |

### Install

```sh
# somewhere outside this repo, e.g. a sibling directory
git clone https://github.com/hezzel/cora.git
cd cora
./gradlew build          # compiles + runs Cora's own tests
make install             # unpacks the dist to ~/.cora, launcher at ~/.cora/bin/cora
```

Then put it on `PATH` (zsh):

```sh
echo 'export PATH="$HOME/.cora/bin:$PATH"' >> ~/.zshrc
exec zsh
cora ./benchmarks/lcstrs/esop2024/factunit.lcstrs   # sanity check → prints YES
```

`make install` is enough after `./gradlew build`; the bare `make` target just
re-runs the build.

### Manual run against this tool's output

```sh
java -jar target/rust2lctrs-1.0-SNAPSHOT.jar src/test/resources/benchmarks/sum.rs > out.lctrs
cora out.lctrs
```

Cora prints `YES` (termination proved) or `MAYBE` (inconclusive) on its first
line.

### How the e2e layer finds Cora

The e2e tests locate the binary via the **`CORA_BIN`** environment variable —
an absolute path, or a bare command name resolved on `PATH` — falling back to
`cora` on `PATH`. Nothing is hard-coded, so CI and local shells can differ:

```sh
CORA_BIN=/opt/cora/bin/cora ./mvnw verify
```

If Cora can't be found, the e2e tests **skip** (they do not fail), so a machine
without Cora still gets a green `verify`. The e2e tests run only in the
`verify` phase (Maven failsafe, `*IT` classes); `./mvnw test` is the fast
unit + snapshot layer and never shells out to Cora.

## Logging

Logging is SLF4J + Logback. The root level defaults to `WARN` and is overridable
via the `LOG_LEVEL` environment variable:

```
LOG_LEVEL=DEBUG ./mvnw exec:java -Dexec.mainClass=project.Main -Dexec.args="input.rs"
```

Accepts any Logback level (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`). Because
logs are on stderr, raising the level never corrupts the LCTRS on stdout.
