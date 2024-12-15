package com.rockthejvm.part2effects

// Similar definition of MyZIO built from the Effects.scala definition of MyIO[A]
// Since R consumes context, it's contravariant
// Since E (error channel) and A (correct channel) produce values, they are covariant.
// case class MyZIO[-R, +E, +A](unsafeRun: (r: R) => Either[E, A]) {
//   def map[B](f: A => B): MyZIO[R, E, B] = MyZIO((r) =>
//     unsafeRun(r) match {
//       case Left(e)  => Left(e)
//       case Right(v) => Right(f(v))
//     }
//   )

// Types are adjusted here so that covariants are correctly defined (see Variance in scala 3 advanced course for explanation)
// Also the contravariant R needs to be adjusted (by defining a type that is a subtype of R)
//   def flatMap[R1 <: R, E1 >: E, B](f: A => MyZIO[R1, E1, B]): MyZIO[R1, E1, B] =
//     MyZIO((r) =>
//       unsafeRun(r) match {
//         case Left(e)  => Left(e)
//         case Right(v) => f(v).unsafeRun(r)
//       }
//     )
// }

import zio._
import scala.io.StdIn

object ZIOEffects extends App {

  // This will return a ZIO effect whose environment is any requiring no side effects
  // The error channel is nothing
  // The value channel is Int
  val meaningOfLife: ZIO[Any, Nothing, Int] = ZIO.succeed(42)

  // Failure
  val aFailure: ZIO[Any, String, Nothing] = ZIO.fail("Something went wrong")

  // Suspend/delay the execution of another effect
  val aSuspendedZIO: ZIO[Any, Throwable, Int] = ZIO.suspend(meaningOfLife)

  // map + flatmap
  val improvedMOL = meaningOfLife.map(_ * 2)
  val printingMOL = meaningOfLife.flatMap(mol => ZIO.succeed(println(mol)))

  // for comprehensions
  val smallProgram = for {
    _ <- ZIO.succeed(println("What's your name?"))
    name <- ZIO.succeed(StdIn.readLine())
    _ <- ZIO.succeed(println(s"Welcome to ZIO, $name"))
  } yield ()

  // ZIO has a LOT of combinators
  // zip
  val anotherMOL = ZIO.succeed(10)
  val tupledZIO = meaningOfLife.zip(anotherMOL)
  // This combines two ZIOs and apply the described function
  val combinedZIO = meaningOfLife.zipWith(anotherMOL)(_ * _)

  /** Type aliases of ZIO
    */
  // UIO (Universal IO that can't fail) = ZIO[Any, Nothing, A]
  val aUIO: UIO[Int] = ZIO.succeed(99)
  // URIO (Same, it can't fail, but it has requirements)
  val aURIO: URIO[Int, Int] = ZIO.succeed(67)
  // RIO[R, A] = ZIO[R, Throwable, A] (It's a ZIO that has requirements and that it can fail)
  val anRIO: RIO[Int, Int] = ZIO.succeed(98)
  val aFailedRIO: RIO[Int, Int] = ZIO.fail(new RuntimeException("RIO failed"))

  // Task[A] = ZIO[Any, Throwable, A] - no requirements, can fail with Throwable, produces A
  val aSuccessfulTask: Task[Int] = ZIO.succeed(89)
  val aFailedTask: Task[Int] = ZIO.fail(new RuntimeException("Something bad"))

  // IO[E, A] = ZIO[Any, E, A] - no requirements
  val aSuccessfulIO: IO[String, Int] = ZIO.succeed(34)
  val aFailedIO: IO[String, Int] = ZIO.fail("Something bad happened")
}

object Exercise1 extends ZIOAppDefault {

  /** 1. sequence two ZIOs and take the value of the last one */
  def sequenceTakeLast[R, E, A, B](
      zioa: ZIO[R, E, A],
      ziob: ZIO[R, E, B]
  ): ZIO[R, E, B] =
    for {
      _ <- zioa
      bResult <- ziob
    } yield bResult

  // Using ZIO library sequence operation
  def sequenceTakeLast_v2[R, E, A, B](
      zioa: ZIO[R, E, A],
      ziob: ZIO[R, E, B]
  ): ZIO[R, E, B] = zioa *> ziob

  // In order to manually run a ZIO

  // We use runtime to evaluate ZIO (something like the unsafeRun() of MyIO in Effects.scala)
  // val runtime = Runtime.default
  // It enables debugging your code regardless if ZIOs are run on the main application thread or in some other thread
  // given trace: Trace = Trace.empty
  /*
        Unsafe.unsafe { implicit u: Unsafe =>
            val mol = runtime.unsafe.run(ZIOEffects.meaningOfLife)
            println(mol)
        }
   */

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = {
    val firstEffect = ZIO.succeed {
      println("Computing first effect...")
      Thread.sleep(1000)
      1
    }

    val secondEffect = ZIO.succeed {
      println("Computing second effect...")
      Thread.sleep(1000)
      2
    }

    sequenceTakeLast_v2(firstEffect, secondEffect).map(println)
  }

}

object Exercise2 extends ZIOAppDefault {

  /** 1. sequence two ZIOs and take the value of the first one */
  def sequenceTakeFirst[R, E, A, B](
      zioa: ZIO[R, E, A],
      ziob: ZIO[R, E, B]
  ): ZIO[R, E, A] =
    // Using flatMap / map syntax instead of for comprehension
    zioa.flatMap(a => ziob.map(_ => a))

  // Using ZIO library sequence operation
  def sequenceTakeFirst_v2[R, E, A, B](
      zioa: ZIO[R, E, A],
      ziob: ZIO[R, E, B]
  ): ZIO[R, E, A] = zioa <* ziob

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    sequenceTakeFirst_v2(ZIO.succeed(42), ZIO.succeed(84)).map(println)
}

object Exercise3 extends ZIOAppDefault {

  /** 1. Run a ZIO forever */
  // Concept of trampolining, which is that ZIO recursion is performed in the heap, not in the stack,
  // since in every loop a new object is created, and then the computation is resolved in a recursive way in order.
  // Normally we don't have to worry about this, but in this case (infinite loop) this will lead to heap exhaustion (eventually returning a java.lang.OutOfMemoryError)
  def runForever[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio.flatMap(_ => runForever(zio))

  def runForever_v2[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio *> runForever_v2(zio) // Same

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = ???
}

object Exercise4 extends ZIOAppDefault {

  /** 1. Convert the value of a ZIO to something else */
  def convert[R, E, A, B](zio: ZIO[R, E, A], value: B): ZIO[R, E, B] =
    zio.map(_ => value)

  // Using ZIO API
  def convert_v2[R, E, A, B](zio: ZIO[R, E, A], value: B): ZIO[R, E, B] =
    zio.as(value)

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = convert(
    ZIO.succeed(42),
    10
  ).map(println)
}

object Exercise5 extends ZIOAppDefault {

  /** 1. Discard the value of a ZIO to Unit */
  def asUnit[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, Unit] =
    zio.map(_ => ()) // or convert(zio, ())

  // Using ZIO API
  def asUnit_v2[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, Unit] =
    zio.unit

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = asUnit(
    ZIO.succeed(42)
  ).map(println)

}

object Exercise6 extends ZIOAppDefault {

  // Recursion:
  def sum(n: Int): Int =
    if (n == 0) 0
    else n + sum(n - 1) // will crash at ~ sum(20000)

  def sumZIO(n: Int): UIO[Int] =
    if (n == 0) ZIO.succeed(0)
    else
      ZIO.succeed(n).flatMap(n => sumZIO(n - 1)).map(n1 => n + n1)

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    sumZIO(20000).map(println)

}

object Exercise7 extends ZIOAppDefault {

  def fibo(n: Int): BigInt = {
    if (n <= 2) 1
    else fibo(n - 1) + fibo(n - 2)
  }

  // In ZIO
  def fiboZIO(n: Int): UIO[BigInt] = {
    if (n <= 2) ZIO.succeed(1)
    else
      for {
        // This will crash with big numbers, since fiboZIO(n - 1) is evaluated eagerly when evaluating the final ZIO
        // since this can be translated into
        // fiboZIO(n - 1).flatMap(last => fiboZIO(n - 2).map(prev => last + prev))
        //   ^^  this is evaluated before moving to the next effect
        // solution: next method
        last <- fiboZIO(n - 1)
        prev <- fiboZIO(n - 2)
      } yield last + prev
  }

  def fiboZIOCorrect(n: Int): UIO[BigInt] = {
    if (n <= 2) ZIO.succeed(1)
    else
      for {
        // Delays the execution of fiboZIO(n - 1) only when it's needed (and not eagerly like in a regular flatMap)
        last <- ZIO.suspendSucceed(fiboZIO(n - 1))
        prev <- fiboZIO(n - 2)
      } yield last + prev
  }

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    fiboZIOCorrect(10).map(println)
}
