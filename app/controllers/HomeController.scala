package controllers

import javax.inject.Inject
import models._
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._
import views._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Manage a database of emails
  */
class HomeController @Inject()(emailService: EmailRepository,
                               recipientService: RecipientRepository,
                               cc: MessagesControllerComponents)(implicit ec: ExecutionContext)
  extends MessagesAbstractController(cc) {

  private val logger = play.api.Logger(this.getClass)

  /**
    * This result directly redirect to the application home.
    */
  val Home = Redirect(routes.HomeController.list(0, 2, ""))

  /**
    * Describe the email form (used in both edit and create screens).
    */
  val emailForm = Form(
    mapping(
      "id" -> ignored(None: Option[Long]),
      "title" -> nonEmptyText,
      "from_day" -> optional(number),
      "to_day" -> optional(number),
      "from_hour" -> optional(number),
      "to_hour" -> optional(number),
      "msg" -> nonEmptyText,
      "sender" -> optional(longNumber),
      "recipient" -> optional(longNumber),
      "status" -> nonEmptyText,
    )(Email.apply)(Email.unapply)
  )

  // -- Actions

  /**
    * Handle default path requests, redirect to emails list
    */
  def index = Action {
    Home
  }

  /**
    * Display the paginated list of emails.
    *
    * @param page    Current page number (starts from 0)
    * @param orderBy Column to be sorted
    * @param filter  Filter applied on email titles
    */
  def list(page: Int, orderBy: Int, filter: String) = Action.async { implicit request =>
    emailService.list(page = page, orderBy = orderBy, filter = ("%" + filter + "%")).map { page =>
      Ok(html.list(page, orderBy, filter))
    }
  }

  /**
    * Display the 'edit form' of a existing Email.
    *
    * @param id Id of the email to edit
    */
  def edit(id: Long) = Action.async { implicit request =>
    emailService.findById(id).flatMap {
      case Some(email) =>
        recipientService.options.map { options =>
          Ok(html.editForm(id, emailForm.fill(email), options))
        }
      case other =>
        Future.successful(NotFound)
    }
  }

  /**
    * Handle the 'edit form' submission
    *
    * @param id Id of the email to edit
    */
  def update(id: Long) = Action.async { implicit request =>
    emailForm.bindFromRequest.fold(
      formWithErrors => {
        logger.warn(s"form error: $formWithErrors")
        recipientService.options.map { options =>
          BadRequest(html.editForm(id, formWithErrors, options))
        }
      },
      email => {
        emailService.update(id, email).map { _ =>
          Home.flashing("success" -> "Email %s has been updated".format(email.title))
        }
      }
    )
  }

  /**
    * Display the 'new email form'.
    */
  def create = Action.async { implicit request =>
    recipientService.options.map { options =>
      Ok(html.createForm(emailForm, options))
    }
  }

  /**
    * Handle the 'new email form' submission.
    */
  def save = Action.async { implicit request =>
    emailForm.bindFromRequest.fold(
      formWithErrors => recipientService.options.map { options =>
        BadRequest(html.createForm(formWithErrors, options))
      },
      email => {
        emailService.insert(email).map { _ =>
          Home.flashing("success" -> "Email %s has been created".format(email.title))
        }
      }
    )
  }

  /**
    * Handle email deletion.
    */
  def delete(id: Long) = Action.async {
    emailService.delete(id).map { _ =>
      Home.flashing("success" -> "Email has been deleted")
    }
  }

}