package com.rockthejvm.part2effects

import scala.concurrent.Future
import com.rockthejvm.part2effects.Effects.MyIO
import scala.io.StdIn

object Effects {

  // functional programming
  // It can be seen as a set of EXPRESSIONS, not instructions, i.e. operations that evaluate to a `val`

  // Functional programming has two main concepts:
  // local reasoning = type signature describes the kind of computation that will be performed. e.g.:
  def combine(a: Int, b: Int) = a + b // <= This is a mini functional program

  // referential transparency
  // the ability to replace a functional program with the expressions that it evaluates to => and then the value that it evaluates to. As many times as we want.
  // Example:
  // Let's invoke our mini functional program
  val five = combine(2, 3)

  // referential transparency will be the ability to replace previous val with:
  val five_2 = 2 + 3

  // and then
  val five_3 = 5

  // This happens WITHOUT CHANGING THE BEHAVIOUR OF THE PROGRAM.

  // Not all expressions are referential transparent
  // Example 1:
  val resultOfPrinting: Unit = println("Learning ZIO")

  // If I replace the expression with the value it evaluates to:
  val resultOfPrinting_v2: Unit = ()

  // And THAT CHANGES THE BEHAVIOUR of the program.

  // Example 2: changing a value
  var anInt = 0
  val changingInt: Unit = (anInt = 42) // side effect
  // After replacing previous expression with its value, it changes the program behaviour! () != (anInt=42)
  val changingInt_v2: Unit = ()

  // side effects are inevitable. We define then a data structure that will break the side effect and apply local reasoning and referential transparency:
  // That data structure is an Effect
  /*
    Effect has a bunch of effect properties:
    - the type signature describes what KIND of computation it will perform
    - the type signature describes the type of VALUE that it will produce
    - if side effects are required, construction of the data structure must be separate from the EXECUTION of the side effect
   */

  // Example: Option
  val anOption: Option[Int] = Option(42)

  // Option data structure:
  // - type signature describes the kind of computation = a possibly absent value √
  // - type signature says the computation returns an A, if the computation does produce something √
  // - no side effects are needed (there could be side effects in the expression inside, but how the option is constructed is side effect free) √
  // Conclusion: Option IS AN EFFECT

  // Another example: Future
  import scala.concurrent.ExecutionContext.Implicits.global
  val aFuture: Future[Int] = Future(42)

  // Future data structure:
  // - describes an asynchronous computation √
  // - it produces a value of type A, if it finishes and it's successful √
  // - side effects are required (allocating a JVM thread / OS thread, etc.), construction is NOT SEPARATE from execution (at the moment we define the Future, the
  // target JVM thread is allocated, so the side effect exists at the moment of construction) X
  // Conclusion: Future IS NOT AN EFFECT

  // Example3: Monad
  case class MyIO[A](unsafeRun: () => A) {
    def map[B](f: A => B): MyIO[B] = MyIO(() => f(unsafeRun()))
    def flatMap[B](f: A => MyIO[B]): MyIO[B] =
      MyIO(() => f(unsafeRun()).unsafeRun())
  }

  // MyIO is a monad (through demonstration of left-identity, right-identity and associativity using flatMap), but is it an effect?
  // - describes a computation that might perform side effects √
  // - produces a value of type A if the computation is successful
  // - side effects are required, is the construction separate from the execution? It is, because the construction itself does not produce the side effect, it's only
  // WHEN WE CALL unsafeRun when the side effect is produced √:
  val anIOWithSideEffects: MyIO[Int] = MyIO(() => {
    println("Producing effect")
    42
  })

  // If I run previous code, I don't see "Producing effect"

  // It's only when I run
  anIOWithSideEffects.unsafeRun()
  // when I see it

  // Conclusion: MyIO IS AN EFFECT
}

object EffectsExercises extends App {

  /** Exercise 1: Create some IO which measures the current time of the system
    */
  object TimeIO {
    type TimeIO = MyIO[Long]
    def currentTime(): TimeIO = MyIO(() => System.currentTimeMillis())
  }

  /** Exercise 2: Create some IO which measures the duration of a computation
    * (use exercise 1 and map/flatMap combinations of MyID) *
    */
  object DurationIO {
    def measure[A](computation: MyIO[A]): MyIO[(Long, A)] = {
      for {
        startTime <- TimeIO.currentTime()
        computationResult <- computation
        endTime <- TimeIO.currentTime()
      } yield (endTime - startTime, computationResult)
      // TimeIO
      //   .currentTime()
      //   .flatMap(time =>
      //     MyIO(() => computation.unsafeRun()).map(computationResult =>
      //       (TimeIO.currentTime().unsafeRun() - time, computationResult)
      //     )
      //   )
    }
  }

  // val durationIO = DurationIO.measure(MyIO(() => {
  //   Thread.sleep(1000)
  //   println("Hello")
  // })) // It's an effect, since it derives the execution to MyIO

  // println(durationIO.unsafeRun())

  /** Exercise 3: Read something from the console */
  object ConsoleReadIO {
    type ReadIO = MyIO[String]
    def read(): ReadIO = MyIO(() => StdIn.readLine())
  }

  // val readFromConsoleIO: ConsoleReadIO.ReadIO = ConsoleReadIO.read()
  // println(readFromConsoleIO.unsafeRun())

  /** Exercise 4: Print something to the console, then read, then print a
    * welcome message
    */
  object PrintConsoleIO {
    def printToConsole(): MyIO[Unit] =
      for {
        _ <- MyIO(() => println("What's your name?"))
        nameString <- ConsoleReadIO.read()
        _ <- MyIO(() => println(s"Hello, $nameString"))
      } yield ()
  }

  // This does not print anything. This makes PrintConsoleIO an effect (construction & execution of the side effect are separate)
  val printConsoleIO = PrintConsoleIO.printToConsole()

  printConsoleIO.unsafeRun()
}
