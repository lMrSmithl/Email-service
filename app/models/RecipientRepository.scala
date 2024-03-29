package models

import javax.inject.Inject

import scala.util.{ Failure, Success }

import anorm._
import anorm.SqlParser.{ get, str }

import play.api.db.DBApi

import scala.concurrent.Future

case class Recipient(id: Option[Long] = None, name: String, email_address: String, phone_number: String)

@javax.inject.Singleton
class RecipientRepository @Inject()(dbapi: DBApi)(implicit ec: DatabaseExecutionContext) {

  private val db = dbapi.database("default")

  /**
    * Parse a Company from a ResultSet
    */
  private[models] val simple = {
    get[Option[Long]]("recipient.id") ~ str("recipient.name") ~ str("recipient.email_address") ~ str("recipient.phone_number") map {
      case id ~ name ~ email_address ~ phone_number => Recipient(id, name, email_address, phone_number)
    }
  }

  /**
    * Construct the Seq[(String,String)] needed to fill a select options set.
    *
    * Uses `SqlQueryResult.fold` from Anorm streaming,
    * to accumulate the rows as an options list.
    */
  def options: Future[Seq[(String,String)]] = Future(db.withConnection { implicit connection =>
    SQL"select * from recipient order by name".
      fold(Seq.empty[(String, String)], ColumnAliaser.empty) { (acc, row) => // Anorm streaming
        row.as(simple) match {
          case Failure(parseErr) => {
            println(s"Fails to parse $row: $parseErr")
            acc
          }

          case Success(Recipient(Some(id), name, email_address, phone_number)) =>
            (id.toString -> name) +: acc

          case Success(Recipient(None, _, _, _)) => acc
        }
      }
  }).flatMap {
    case Left(err :: _) => Future.failed(err)
    case Left(_) => Future(Seq.empty)
    case Right(acc) => Future.successful(acc.reverse)
  }
}
