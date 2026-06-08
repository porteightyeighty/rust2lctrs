# rust2lctrs

A Java tool that translates a restricted fragment of Rust into Logically
Constrained Term Rewriting Systems (LCTRSs), so that
[Cora](https://github.com/hezzel/cora) can analyse termination and equivalence.

## Pipeline

```
Rust source → ANTLR parser → AstBuilder → custom AST → Translator → LCTRS → Cora input
```

## Build & run

Uses the Maven wrapper (`./mvnw`); no system Maven needed.

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

## Logging

Logging is SLF4J + Logback. The root level defaults to `WARN` and is overridable
via the `LOG_LEVEL` environment variable:

```
LOG_LEVEL=DEBUG ./mvnw exec:java -Dexec.mainClass=project.Main -Dexec.args="input.rs"
```

Accepts any Logback level (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`). Because
logs are on stderr, raising the level never corrupts the LCTRS on stdout.
