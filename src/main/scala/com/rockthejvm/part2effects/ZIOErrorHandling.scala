package com.rockthejvm.part2effects

import zio._
import scala.util.Try

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
  val aTryToZIO: ZIO[Any, Throwable, Int] | Task[Int] = ZIO.fromTry(Try(42 / 0))
  val anEither: Either[Int, String] = Right("Success!")
  val anEitherZIO: ZIO[Any, Int, String] | IO[Int, String] =
    ZIO.fromEither(anEither)

  // wrap the value channel with the either as a result of its computation
  val eitherZIO =
    anAttempt.either // value channel will contain an Either[Throwable, Int]

    // absolve will take the either in the value channel and it will bring the Throwable back to the error channel.
  val anAttempt_v2 = eitherZIO.absolve

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = ???

}
