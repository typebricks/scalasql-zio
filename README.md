# scalasql-zio

ZIO interop layer for [scalasql](https://github.com/com-lihaoyi/scalasql).

Wraps scalasql's JDBC API with ZIO effects — type-safe database operations, transactions with savepoints.

## Features

- **`ZDbTxnApi` / `ZDbClient`** — ZIO wrappers around scalasql's `DbApi` / `DbClient` with automatic error wrapping in `DbException`
- **Transaction support** with savepoints and rollback — all `DbOp` helpers run within a transaction context
- **Lookup helpers** — `atMostOne`, `orNotFound`
- **Type aliases** — `DbOp[+A]` for standard DB operations (`ZIO[ZDbTxnApi, DbException, A]`), `DbZIO[-R, +E, +A]` for operations with extra environment/errors, `DbRun[+A]` for low-level blocking JDBC calls

## Quick start

```scala
libraryDependencies += "org.typebricks" %% "scalasql-zio" % "put version here"
```

### Table definition

> See [TestTable.scala](modules/core/src/test/scala/typebricks/scalasql/dbzio/tests/TestTable.scala) for the full working example.

```scala
case class User[+F[_]](
    id: F[Long],
    email: F[Email],
    name: F[UserName]
)

object User extends scalasql.query.Table[User]
```

### Running queries

```scala
ZDbClient.transaction:
  for
    _    <- DbOp.run(User.insert.columns(_.email := email, _.name := name))
    _    <- ZIO.logDebug("Other side effects within a transaction")
    user <- DbOp.runSingle(User.select.filter(_.email `=` email))
  yield user
```

### Direct vs environment access

API classes (`ZDbClient`, `ZDbTxnApi`) have instance methods, while their companion objects mirror the same API through ZIO's environment — pulling the service instance via `ZIO.service` before delegating. This lets you choose between passing the client explicitly or requiring it in your effect's environment:

```scala
// Direct — you have a ZDbClient instance
dbClient.transaction:
  DbOp.run(query)

// Environment — ZDbClient is pulled from ZIO's R
ZDbClient.transaction:
  DbOp.run(query)
```

### Transactions

> See [ZDbApiPlainSpec.scala](modules/core/src/test/scala/typebricks/scalasql/dbzio/tests/ZDbApiPlainSpec.scala) for transaction commit/rollback examples and tests.

Transactions commit on success and roll back on failure.
Here `transaction` is imported from either `ZDbClient.transaction` (environment service style) or `dbClientInstance.transaction` (direct):

```scala
// Commits
transaction:
  DbOp.run(User.insert.columns(cols*))

// Rolls back — the insert is undone
transaction:
  for
    _ <- DbOp.run(User.insert.columns(cols*))
    _ <- ZIO.fail(new RuntimeException("boom"))
  yield ()
```

A money transfer between two accounts — lock rows with `SELECT ... FOR UPDATE`, compute new balances, then update atomically:

```scala

def transfer(fromUserId: User.Id, toUserId: User.Id, amount: BigDecimal): DbOp[Unit] =
  transaction:
    for
      // Single query locks both rows; sortBy puts "from" account first
      Seq(from, to) <- DbOp.run(
        Account.select
          .filter(a => (a.userId `=` fromUserId) || (a.userId `=` toUserId))
          .sortBy(_.userId `=` toUserId) // false < true, so fromUserId's row comes first
          .forUpdate
      )
      _ <- DbOp.run(Account.update(_.userId `=` fromUserId).set(_.balance := from.balance - amount))
      _ <- DbOp.run(Account.update(_.userId `=` toUserId).set(_.balance := to.balance + amount))
    yield ()
```

If anything fails mid-transaction, both sides roll back.

### Savepoints

Use savepoints to partially roll back a transaction. Since `DbOp` provides `ZDbTxnApi`, savepoints are available directly inside any `transaction` block via `DbZIO(_.savepoint(...))`.

For example, log a transfer attempt that survives even when the transfer itself fails:

```scala
transaction:
  for
    // Log attempt (outside savepoint — persists regardless)
    _ <- DbOp.run(TransferAudit.insert.columns(
      _.fromUserId := fromId, _.toUserId := toId, _.amount := amount, _.event := "attempt"
    ))
    // Transfer — inside savepoint, rolled back on failure
    result <- DbZIO: txn =>
      txn.savepoint: _ =>
        for
          _ <- DbOp.run(Account.update(_.userId `=` fromId).set(_.balance := from.balance - amount))
          _ <- DbOp.run(Account.update(_.userId `=` toId).set(_.balance := to.balance + amount))
        yield ()
    .either
    // Log outcome
    _ <- DbOp.run(TransferAudit.insert.columns(
      _.fromUserId := fromId, _.toUserId := toId, _.amount := amount,
      _.event := (if result.isRight then "success" else "failure")
    ))
  yield ()
```

> See [ZDbApiPlainSpec.scala](modules/core/src/test/scala/typebricks/scalasql/dbzio/tests/ZDbApiPlainSpec.scala) for the full savepoint test.

## Coming soon

- **Db Store/Repo API** — type-safe CRUD operations (get, create, update, delete) for tables with primary keys, built on top of `ZDbApi`.

## Contributions welcome

- **ZStream support** — wrap scalasql's `DbApi` streaming (`Generator`) results as `ZStream` for backpressure-aware, chunked query processing. Needs zio-streams, better to be a separate module.

## Requirements

- Scala 3.6+
- scalasql 0.3.0
- ZIO 2.1.x

## License

MIT
