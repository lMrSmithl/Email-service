package models

import javax.inject.Inject

import anorm.SqlParser.{ get, scalar }
import anorm._
import play.api.db.DBApi

import scala.concurrent.Future

case class Email(id: Option[Long] = None,
                 title: String,
                 from_day: Option[Int],
                 to_day: Option[Int],
                 from_hour: Option[Int],
                 to_hour: Option[Int],
                 msg: String,
                 sender_id: Option[Long],
                 recipient_id: Option[Long],
                 status: String)

object Email {
  implicit def toParameters: ToParameterList[Email] =
    Macro.toParameters[Email]
}

/**
  * Helper for pagination.
  */
case class Page[A](items: Seq[A], page: Int, offset: Long, total: Long) {
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)
}


@javax.inject.Singleton
class EmailRepository @Inject()(dbapi: DBApi, recipientRepository: RecipientRepository)(implicit ec: DatabaseExecutionContext) {

  private val db = dbapi.database("default")

  // -- Parsers

  /**
    * Parse a Email from a ResultSet
    */
  private val simple = {
    get[Option[Long]]("email.id") ~
      get[String]("email.title") ~
      get[Option[Int]]("email.from_day") ~
      get[Option[Int]]("email.to_day") ~
      get[Option[Int]]("email.from_hour") ~
      get[Option[Int]]("email.to_hour") ~
      get[String]("email.msg") ~
      get[Option[Long]]("email.sender_id") ~
      get[Option[Long]]("email.recipient_id") ~
      get[String]("email.status") map {
      case id ~ title ~ from_day ~ to_day ~ from_hour ~ to_hour ~ msg ~ sender_id ~ recipient_id ~ status =>
        Email(id, title, from_day, to_day, from_hour, to_hour, msg, sender_id, recipient_id, status)
    }
  }

  /**
    * Parse a (Email,Recipient) from a ResultSet
    */
  private val withRecipient = simple ~ (recipientRepository.simple.?) map {
    case email ~ recipient_id => email-> recipient_id
  }

  // -- Queries

  /**
    * Retrieve a email from the id.
    */
  def findById(id: Long): Future[Option[Email]] = Future {
    db.withConnection { implicit connection =>
      SQL"select * from email where id = $id".as(simple.singleOpt)
    }
  }(ec)

  /**
    * Return a page of (Email,Recipient).
    *
    * @param page Page to display
    * @param pageSize Number of emails per page
    * @param orderBy Email property used for sorting
    * @param filter Filter applied on the name column
    */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%"): Future[Page[(Email, Option[Recipient])]] = Future {

    val offset = pageSize * page

    db.withConnection { implicit connection =>

      val emails = SQL"""
        select * from email
        left join recipient on email.recipient_id = recipient.id
        where email.title like ${filter}
        order by ${orderBy} nulls last
        limit ${pageSize} offset ${offset}
      """.as(withRecipient.*)

      val totalRows = SQL"""
        select count(*) from email
        left join recipient on email.sender_id = recipient.id
        where email.title like ${filter}
      """.as(scalar[Long].single)

      Page(emails, page, offset, totalRows)
    }
  }(ec)

  /**
    * Update a email.
    *
    * @param id The email id
    * @param email The email values.
    */
  def update(id: Long, email: Email) = Future {
    db.withConnection { implicit connection =>
      SQL("""
        update email set title = {title}, from_day = {from_day}, to_day = {to_day}, from_hour = {from_hour}, to_hour = {to_hour}, msg = {msg}, sender_id = {sender_id}, recipient_id = {recipient_id}, status = {status}
        where id = {id}
      """).bind(email.copy(id = Some(id)/* ensure */)).executeUpdate()
      // case class binding using ToParameterList,
      // note using SQL(..) but not SQL.. interpolation
    }
  }(ec)

  /**
    * Insert a new email.
    *
    * @param email The email values.
    */
  def insert(email: Email): Future[Option[Long]] = Future {
    db.withConnection { implicit connection =>
      SQL("""
        insert into email values (
          (select next value for email_seq),
          {title}, {from_day}, {to_day}, {from_hour}, {to_hour}, {msg}, {sender_id}, {recipient_id}, {status}
        )
      """).bind(email).executeInsert()
    }
  }(ec)

  /**
    * Delete a email.
    *
    * @param id Id of the email to delete.
    */
  def delete(id: Long) = Future {
    db.withConnection { implicit connection =>
      SQL"delete from email where id = ${id}".executeUpdate()
    }
  }(ec)

}
