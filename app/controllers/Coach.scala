package controllers

import lila.api.Context
import lila.app._
import play.api.libs.json.Json
import play.api.mvc.Result
import views._

object Coach extends LilaController {

  private def env = Env.coach

  def refresh(username: String) = Open { implicit ctx =>
    Accessible(username) { user =>
      env.aggregator(user) inject Ok
    }
  }

  def index(username: String) = Open { implicit ctx =>
    Accessible(username) { user =>
      fuccess {
        Ok(html.coach.index(user, env.jsonView.stringifiedUi))
      }
    }
  }

  def json(username: String) = OpenBody { implicit ctx =>
    Accessible(username) { user =>
      implicit val req = ctx.body
      FormFuResult(env.dataForm.question)(
        _ => fuccess(Json.obj("error" -> "bad request"))
      ) {
          _.question.fold(fuccess(BadRequest(Json.obj("error" -> "bad request")))) { q =>
            env.api.ask(q, user) map
              lila.coach.Chart.fromAnswer map
              env.jsonView.chart.apply map { Ok(_) }
          }
        }
    }
  }

  private def Accessible(username: String)(f: lila.user.User => Fu[Result])(implicit ctx: Context) =
    lila.user.UserRepo named username flatMap {
      case None => notFound
      case Some(u) => env.share.grant(u, ctx.me) flatMap {
        case true                          => f(u)
        case false if isGranted(_.UserSpy) => f(u)
        case false                         => fuccess(Forbidden(html.coach.forbidden(u)))
      }
    }

  private def AccessibleJson(username: String)(f: lila.user.User => Fu[Result])(implicit ctx: Context) =
    lila.user.UserRepo named username flatMap {
      case None => notFoundJson(s"No such user: $username")
      case Some(u) => env.share.grant(u, ctx.me) flatMap {
        case true                          => f(u)
        case false if isGranted(_.UserSpy) => f(u)
        case false                         => fuccess(Forbidden(Json.obj("error" -> s"User $username data is protected")))
      }
    } map (_ as JSON)
}
