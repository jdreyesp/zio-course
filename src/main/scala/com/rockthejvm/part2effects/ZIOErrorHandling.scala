package com.rockthejvm.part2effects

import zio._
import scala.util.Try
import java.io.IOException
import java.net.NoRouteToHostException

object ZIOErrorHandling extends ZIOAppDefault {

  // ZIOs can fail
  val aFailedZIO = ZIO.fail("Something went wrong")
  // The runtimeexception will be constructed only, not executed (remember ZIO is an effect)
  val failedWithThrowable = ZIO.fail(new RuntimeException("Boom"))

  // We can map the error so that we convert it to a different type
  val failedWithDescription = failedWithThrowable.mapError(_.getMessage)

  // attempt: run an effect that might throw an exception
  val anAttempt: ZIO[Any, Throwable, Int] = ZIO.attempt {
    println("Trying something")
    val string: String = null
    string.length()
  }

  // how to 'effectfully' (a.k.a. returning an effect) catch errors
  val catchError = anAttempt.catchAll(e =>
    ZIO.succeed(s"Returning a different value because $e")
  )
  val catchSelectiveErrors = anAttempt.catchSome {
    // It receives a partial function where we can control the exception types
    case e: RuntimeException => ZIO.succeed(s"Ignoring runtime exception $e")
    // In this case, we can broaden the type of the exception, since the anAttempt returns a RuntimeException and
    // ZIO.fail returns a string => the return type here will be Serializable
    case _ => ZIO.fail("Ignoring everything else")
  }

  // chain effects
  val aBetterAttempt = anAttempt.orElse(ZIO.succeed(56))
  // fold: handle both success and failure
  val handleBoth: ZIO[Any, Nothing, String] = anAttempt.fold(
    ex => s"Something bad happened $ex",
    value => "Length of the string: $value"
  )

  // you can use foldZIO to wrap the result of the fold into a ZIO
  val handleBoth_v2: ZIO[Any, Nothing, String] = anAttempt.foldZIO(
    ex => ZIO.succeed(s"Something bad happened $ex"),
    value => ZIO.succeed("Length of the string: $value")
  )

  // Conversions between Option/Try/Either to ZIO

  // TRY
  val aTryToZIO: ZIO[Any, Throwable, Int] | Task[Int] = ZIO.fromTry(Try(42 / 0))

  // EITHER
  val anEither: Either[Int, String] = Right("Success!")
  val anEitherZIO: ZIO[Any, Int, String] | IO[Int, String] =
    ZIO.fromEither(anEither)

  // wrap the value channel with the either as a result of its computation
  val eitherZIO =
    anAttempt.either // value channel will contain an Either[Throwable, Int]

      // absolve will take the either in the value channel and it will bring the Throwable back to the error channel.
  val anAttempt_v2 = eitherZIO.absolve

  // OPTION

  // The error channel is an Option[Nothing], representing a None
  val anOptionZIO: IO[Option[Nothing], Int] = ZIO.fromOption(Option(42))

  /** Errors vs defects
    *
    * Error = failures present in ZIO type signature ("checked" exceptions)
    * Defects = failures that are unrecoverable, unforeseen, NOT present in the
    * ZIO type signature
    */

  // This does not have any error (it's an UIO), but it has a defect (1/0)
  val divisionByZero: UIO[Int] = ZIO.succeed(1 / 0)

  /** ZIO[R, E, A] in general can finish with:
    *
    *   - Success[A] containing a value or
    *   - Cause[E]
    *     - Fail[E] containing the error
    *     - Die(t: Throwable) which was unforeseen (defect)
    *
    * This is the definition of Exit[E, A]
    */

  // Let's explore how we can treat errors as Fail[E] or as Die(t)
  val failedInt: ZIO[Any, String, Int] = ZIO.succeed(1 / 0)
  val failureCauseExposed: ZIO[Any, Cause[String], Int] = failedInt.sandbox
  val failureCauseHidden: ZIO[Any, String, Int] = failureCauseExposed.unsandbox

  // you can also fold(ex: Throwable, val: String) with the cause:
  val foldedWithCause = failedInt.foldCause(
    cause => s"this failed with ${cause.defects}",
    value => s"This succeeded with $value"
  )
  val foldedWithCause_v2 = failedInt.foldCauseZIO(
    cause => ZIO.succeed(s"this failed with ${cause.defects}"),
    value => ZIO.succeed(s"This succeeded with $value")
  )

  /* Good practice:
    - at a lower level, your "errors" should be treated
    - at a high level, you should hide "errors" and assume they are unrecoverable
   */

  // Following this rule, we now convert a checked exception, and consider it a defect, like:
  def callHTTPEndpoint(url: String): ZIO[Any, IOException, String] =
    ZIO.fail(new IOException("No internet, dummy!"))

  val endpointCallWithDefects: ZIO[Any, Nothing, String] = callHTTPEndpoint(
    "rockthejvm.com"
  ).orDie // all errors are now defects

  // I can also refine the exceptions that are wider into more specific ones, like:
  def callHTTPEndpointWideError(url: String): ZIO[Any, Exception, String] =
    ZIO.fail(new IOException("No internet, dummy!"))

  def callHttpEndpoint_v2(url: String): ZIO[Any, IOException, String] =
    callHTTPEndpointWideError(url).refineOrDie[IOException] {
      case e: IOException => e
      case _: NoRouteToHostException =>
        new IOException(s"No route to host to $url")
    }

  // The opposite can be done (unrefine), so that I redeclare the error type with whatever I define in the
  // partial function from a Throwable (since all defects are Throwable).
  // This will convert defects to Strings in the error channel
  val endpointCallWithError = endpointCallWithDefects.unrefine { case e =>
    e.getMessage()
  }

  /* Combining errors */

  // There are some situations where we need to combine different errors from different layers of our
  // application. In this case, the type safety is lost:
  case class IndexError(message: String)
  case class DBError(message: String)
  val callAPI: ZIO[Any, IndexError, String] = ZIO.succeed("Page: <html></html>")
  val queryDB: ZIO[Any, DBError, Int] = ZIO.succeed(0)

  // If I now do:
  val combined = for {
    apiResponse <- callAPI
    rowsAffected <- queryDB
  } yield (apiResponse, rowsAffected) // we lose type safety here!

  // Possible solutions:
  // - (Recommended) define a good error model
  // - use Scala3 union types (by saying `val combined: ZIO[Any, IndexError | DBError, (String, Int)]`)
  // - use .mapError in the for comprehension
  // The soluciotn for the good error model would be
  trait MyAppError
  // case class IndexError(message: String) extends AppError
  // case class DBError(message: String) extends AppError

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    combined.map(println)

}

object Exercises extends ZIOAppDefault {
  // 1 - Make this effect fail with a TYPED error
  val aBadFailure = ZIO.succeed[Int](throw new RuntimeException("this is bad!"))
  // solution1: exposing the defect in the Cause
  val aBetterFailure = aBadFailure.sandbox
  // solution2: Surfaces out the exception in the error channel
  val aBetterFailure_v2 = aBadFailure.unrefine { case e => e }

  // 2 - Transform a ZIO to another ZIO with a narrower exception type
  def ioException[R, A](zio: ZIO[R, Throwable, A]): ZIO[R, IOException, A] =
    zio.refineOrDie { case e: IOException => e }

  // 3 -
  //   def left[R, E, A, B](
  //       zio: ZIO[R, E, Either[A, B]]
  //   ): ZIO[R, Either[E, A], B] =
  //     zio.foldZIO(
  //       e => ZIO.fail(Left(e)),
  //       either =>
  //         either match {
  //           case Left(a)  => ZIO.fail(Right(a))
  //           case Right(b) => ZIO.succeed(b)
  //         }
  //     )

  def left[R, E, A, B](
      zio: ZIO[R, E, Either[A, B]]
  ): ZIO[R, Either[E, A], B] = {
    zio.foldZIO(
      e => ZIO.fail(Left(e)),
      either =>
        either match {
          case Left(a)  => ZIO.fail(Right(a))
          case Right(b) => ZIO.succeed(b)
        }
    )
  }

  // 4 -
  val database = Map(
    "daniel" -> 123,
    "alice" -> 789
  )
  case class QueryError(reason: String)
  case class UserProfile(name: String, phone: Int)

  def lookupProfile(userId: String): ZIO[Any, QueryError, Option[UserProfile]] =
    if (userId != userId.toLowerCase())
      ZIO.fail(QueryError("user Id format is invalid"))
    else
      ZIO.succeed(database.get(userId).map(phone => UserProfile(userId, phone)))

  // the exercise consists on implementing this betterLookupProfile that stores a UserProfile in the
  // value channel, and all possible errors in the error channel.
  trait AppError
  def betterLookupProfile(
      userId: String
  ): ZIO[Any, Option[QueryError], UserProfile] =
    lookupProfile(userId).foldZIO(
      failure => ZIO.fail(Some(failure)),
      userProfileOption =>
        userProfileOption match {
          case None              => ZIO.fail(None)
          case Some(userProfile) => ZIO.succeed(userProfile)
        }
    )

    // Same as:
  def betterLookupProfile_v2(
      userId: String
  ): ZIO[Any, Option[QueryError], UserProfile] = lookupProfile(userId).some

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = ???
}
