package jp.iwmat.sawtter.infrastructure.jdbc._slick

import java.time.ZonedDateTime
import javax.inject.Inject

import scala.concurrent.ExecutionContext

import jp.iwmat.sawtter.generators.{ Clocker, IdentifyBuilder }
import jp.iwmat.sawtter.models._
import jp.iwmat.sawtter.models.types._
import jp.iwmat.sawtter.repositories._

class UserRepositorySlick @Inject()(
  identifyBuilder: IdentifyBuilder,
  clocker: Clocker
)(
  implicit
  ec: ExecutionContext
) extends RepositoryBaseSlick with UserRepository {

  def findAny[A](key: String, value: A)(implicit setter: slick.jdbc.SetParameter[A]): DBResult[Option[User]] = {
    val dbio = sql"""
      select
        user_id, email, status, version, updated_at, created_at
      from
        users
      where
        #$key = $value
      limit 1
    """
      .as[(Long, String, String, Long, ZonedDateTime, ZonedDateTime)]
      .map(_.headOption.flatMap { case (userId, email, status, version, updatedAt, createdAt) =>
        UserStatus.valueOf(status).toOption.map { status =>
          User(ID(userId), Email(email), status, Version(version), updatedAt, createdAt)
        }
      })
    DBIOResult(dbio)
  }

  def findBy(userId: ID[User]): DBResult[Option[User]] = {
    findAny("user_id", userId.value)
  }

  def findBy(email: Email[SignUp]): DBResult[Option[User]] = {
    findAny("email", email.value)
  }

  def findBy(login: Login): DBResult[Option[User]] = {
    val dbio = sql"""
      select
        user_id, email, status, version, updated_at, created_at
      from
        users
      where
        email = ${login.email.value} and
        password = ${identifyBuilder.hash(login.password.value)}
      limit 1
    """
      .as[(Long, String, String, Long, ZonedDateTime, ZonedDateTime)]
      .map(_.headOption.flatMap { case (userId, email, status, version, updatedAt, createdAt) =>
        UserStatus.valueOf(status).toOption.map { status =>
          User(ID(userId), Email(email), status, Version(version), updatedAt, createdAt)
        }
      })
    DBIOResult(dbio)
  }

  def findToken(token: String): DBResult[Option[UserToken]] = {
    val dbio= sql"""
      select
        ut.token, ut.user_id, ut.expired_at
      from
        user_tokens as ut
        inner join users as u
          on ut.user_id = u.user_id
      where
        ut.token = ${token} and
        ut.expired_at >= ${clocker.now} and
        u.status = ${UserStatus.Registered.value}
      limit 1
    """
      .as[(String, Long, ZonedDateTime)]
      .map(_.headOption.map { case (token, userId, expiredAt) =>
        UserToken(ID(userId), Token(token), expiredAt)
      })
    DBIOResult(dbio)
  }

  def add(signup: SignUp): DBResult[UserToken] = {
    val justNow = clocker.now
    val userId = identifyBuilder.generate()
    val token = identifyBuilder.generateUUID()
    val userToken = UserToken(ID(userId), Token(token), justNow.plusDays(7))

    def addUser() = {
      val hashed = identifyBuilder.hash(signup.password.value)
      sql"""
        insert into
          users
          (user_id, email, password, status, version, updated_at, created_at)
        values
          ($userId, ${signup.email.value}, $hashed, ${UserStatus.Registered.value}, 1, $justNow, $justNow)
      """.as[Int].map(_ => userId)
    }

    def addToken() = {
      sql"""
        insert into
          user_tokens
          (token, user_id, expired_at, created_at)
        values
          ($token, $userId, ${userToken.expiredAt}, $justNow)
      """.as[Int].map(_ => token)
    }

    val dbio = for {
      _ <- addUser()
      _ <- addToken()
    } yield userToken
    DBIOResult(dbio)
  }

  def enable(user: User): DBResult[Unit] = {
    val dbio = sqlu"""
      update
        users
      set
        status = ${UserStatus.Enabled.value},
        version = ${user.version.next.value}
      where
        user_id = ${user.userId.value}
    """.map(_ => ())
    DBIOResult(dbio)
  }
}
