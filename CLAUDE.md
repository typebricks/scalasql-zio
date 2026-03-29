## Project Overview

ZIO interop layer for [scalasql](https://github.com/com-lihaoyi/scalasql). Wraps scalasql's JDBC API with ZIO effects, providing type-safe database operations and transactions with savepoints. Scala 3 only (cross-published for 3.6.x and 3.8.x).

## Build & Test Commands

```bash
sbt compile                        # Compile
sbt test                           # Run all tests
sbt "testOnly *SyntaxSpec"        # Run a single test suite
sbt "++3.6.4; compile; test"      # Cross-compile/test against Scala 3.6 LTS
```

Tests use an in-memory SQLite database (no external services needed).

## Project Structure

Multi-module sbt build. Sources live under `modules/core/`.

## Architecture

Two packages: `typebricks.scalasql` (core types) and `typebricks.scalasql.dbzio` (ZIO integration).

### Core (`typebricks.scalasql`)

- **`TableGivens.scala`** — `PrimaryKey.Is[Table, Pk]` opaque type for zero-cost typed primary keys. `IsCovariantHK` proof for higher-kinded table covariance, plus `widenK` extension.
- **`DbLookup.scala`** — `DbLookupException` sealed hierarchy (`NotFound`, `MultipleResults`) with `DbEntityName[T]` opaque type for context-aware error messages.

### ZIO Layer (`typebricks.scalasql.dbzio`)

- **`ZDbApi.scala`** — Wraps scalasql `DbApi`, runs queries as ZIO effects with automatic `DbException` wrapping and SQL logging. `ZDbTxnApi` extends it with `rollback()` and `savepoint()`. `ZDbClient` provides `transaction{}` and `withAutoCommits{}` scopes. Also defines `DbOp.run`, `DbOp.runHeadOption`, `DbOp.runSingle`, and `DbOp.updateSql` helpers.
- **`Syntax.scala`** — ZIO extensions: `atMostOne` (assert <=1 result from `Seq`), `orNotFound` (unwrap `Option` or fail with `NotFound`).

### Key Type Aliases (from `ZDbApi`)

- `DbOp[+A]` = `ZIO[ZDbTxnApi, DbException, A]` — standard DB operation
- `DbZIO[-R, +E, +A]` = `ZIO[ZDbTxnApi & R, DbException | E, A]` — DB op with extra environment/error
- `DbRun[+A]` = `ZIO[Any, DbException, A]` — blocking IO that wraps JDBC errors in `DbException`

### Design Patterns

- **Opaque types** — `PrimaryKey.Is`, `DbEntityName` compile away to zero-cost wrappers
- **Higher-kinded tables** — `Table[_[_]]` with `Table[Expr]` for queries, `Table[Sc]` for rows, `Table[Column]` for schema
- **`DbOpLocation`** — Captures source file/line at call sites for SQL debug logging

## Compiler Flags

Strict warnings-as-errors: `-Wunused:all`, `-Wvalue-discard`, `-Werror`. Unused imports/variables will fail compilation.
