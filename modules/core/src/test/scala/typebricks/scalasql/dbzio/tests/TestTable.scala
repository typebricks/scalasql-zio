package typebricks.scalasql.dbzio.tests

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import javax.sql.DataSource
import org.sqlite.SQLiteDataSource
import zio.*

import scalasql.*
import scalasql.core.SqlStr.SqlStringSyntax
import scalasql.core.TypeMapper
import scalasql.dialects.SqliteDialect
import scalasql.query.Table as QTable

import typebricks.scalasql.dbzio.*

given scalasql.dialects.ReturningDialect = SqliteDialect
import SqliteDialect.{dialectSelf as _, *}

object Email extends RefinedType[String, Pure]:
  given TypeMapper[T] = assumeAll[TypeMapper](summon)
type Email = Email.T

object UserName extends RefinedType[String, Pure]:
  given TypeMapper[T] = assumeAll[TypeMapper](summon)
type UserName = UserName.T

case class User[+F[_]](
    id: F[Long],
    email: F[Email],
    name: F[UserName]
)

object User extends QTable[User]

case class Account[+F[_]](
    id: F[Long],
    userId: F[Long],
    balance: F[BigDecimal]
)

object Account extends QTable[Account]

case class TransferAudit[+F[_]](
    id: F[Long],
    fromUserId: F[Long],
    toUserId: F[Long],
    amount: F[BigDecimal],
    event: F[String]
)

object TransferAudit extends QTable[TransferAudit]

object TestDb:
  private val freshDataSource: Task[DataSource] = ZIO.attemptBlocking:
    val tmpFile = java.io.File.createTempFile("scalasqlzio-test-", ".db")
    tmpFile.deleteOnExit()
    val ds = new SQLiteDataSource()
    ds.setUrl(s"jdbc:sqlite:${tmpFile.getAbsolutePath}")
    ds

  def clientLayer: ZLayer[Any, DbException, ZDbClient] = ZLayer.fromZIO:
    for
      ds <- freshDataSource.mapError(DbException(_))
      client = ZDbClient.dataSource(ds, new scalasql.Config {})
      _ <- client.withAutoCommits:
        ZIO.serviceWithZIO[ZDbApi]: db =>
          for
            _ <- db.updateSql(sql"""CREATE TABLE user(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  email TEXT NOT NULL,
                  name TEXT NOT NULL
                )""")
            _ <- db.updateSql(sql"""CREATE TABLE account(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id INTEGER NOT NULL REFERENCES user(id),
                  balance DECIMAL(12,2) NOT NULL DEFAULT 0
                )""")
            _ <- db.updateSql(sql"""CREATE TABLE transfer_audit(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  from_user_id INTEGER NOT NULL REFERENCES user(id),
                  to_user_id INTEGER NOT NULL REFERENCES user(id),
                  amount DECIMAL(12,2) NOT NULL,
                  event TEXT NOT NULL
                )""")
          yield ()
    yield client
