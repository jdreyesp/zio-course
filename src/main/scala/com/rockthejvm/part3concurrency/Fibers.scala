package com.rockthejvm.part3concurrency

import zio._
import utils._
import com.rockthejvm.part2effects.ZIOEffects.tupledZIO
import java.io.FileWriter
import java.io.File
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.Path

object Fibers extends ZIOAppDefault {

  val meaningOfLife = ZIO.succeed(42)
  val favLang = ZIO.succeed("Scala")

  // Fiber vs Thread
  // Thread is managed by the JVM threads (that map 1:1 to the CPU Threads)
  // Fiber is a 'lightweight thread' within the threads managed by the ZIO environment
  def createFiber: Fiber[Throwable, String] =
    ??? // Impossible to create it manually

  val sameThreadIO: UIO[(Int, String)] = for {
    mol <- meaningOfLife.debugThread
    lang <- favLang.debugThread
  } yield (mol, lang)

  val differentThreadIO = for {
    // This will produce an effect whose value will be a Fiber
    _ <- meaningOfLife.debugThread.fork
    _ <- favLang.debugThread.fork
  } yield ()

  // So fork return type will be:
  val meaningOfLifeFiber: ZIO[Any, Nothing, Fiber[Throwable, Int]] =
    meaningOfLife.fork

  // With this, we can create functionality for waiting for a fiber to finish, like:
  def runOnAnotherThread[R, E, A](zio: ZIO[R, E, A]) = for {
    fib <- zio.fork
    // This will actually wait until fib has finished
    result <- fib.join
  } yield result

  // You can also wait for the fiber to finish, but you could only be interested in the result of the exited
  // computation (modeled by Exit[E, A], Success[A](value) and Failure[C: Cause[E]])
  def runOnAnotherThread_v2[R, E, A](zio: ZIO[R, E, A]) = for {
    fib <- zio.fork
    result <- fib.await
  } yield result match {
    case Exit.Success(value) => s"Succeeded with $value"
    case Exit.Failure(cause) => s"Failed with $cause"
  }

  // You can also poll (peek the result of a fiber RIGHT NOW, without blocking)
  val peekFiber = for {
    fib <- ZIO.attempt {
      Thread.sleep(1000)
      42
    }.fork
    result <- fib.poll
  } yield result

  // You can also compose fibers with `zip`
  val zippedFibers = for {
    fib1 <- ZIO.succeed("Result from fiber 1").debugThread.fork
    fib2 <- ZIO.succeed("Result from fiber 2").debugThread.fork
    fiber = fib1.zip(
      fib2
    ) // this is aliased since zip returns a Fiber, not an effect
    tuple <- fiber.join
  } yield tuple

  // compose using orElse
  val chainedFibers = for {
    fiber1 <- ZIO.fail("Not good!").debugThread.fork
    fiber2 <- ZIO.succeed("Rock the JVM").debugThread.fork
    fiber = fiber1.orElse(fiber2)
    result <- fiber.join
  } yield result

  def run = chainedFibers.debugThread
}

object Exercises extends ZIOAppDefault {

  // 1 - zip two fibers without using the zip combinator
  // hint: create a fiber that waits for both
  def zipFibers[E, A, B](
      fiber1: Fiber[E, A],
      fiber2: Fiber[E, B]
  ): ZIO[Any, Nothing, Fiber[E, (A, B)]] = {
    // Main drawback of this zipFibers is that we need to wait for fiber1 and fiber2 in order
    val finalEffect = for {
      v1 <- fiber1.join
      v2 <- fiber2.join
    } yield (v1, v2)
    finalEffect.fork
  }

  // 2 - same thing for orElse
  def chainFibers[E, A](
      fiber1: Fiber[E, A],
      fiber2: Fiber[E, A]
  ): ZIO[Any, Nothing, Fiber[E, A]] =
    fiber1.join.orElse(fiber2.join).fork

  // 3 - distributing a task between many fibers
  // Spawn n fibers, count the n of words of each file,
  // then aggregate all the results together in one big number
  // (fun fact: this is the typical mapReduce problem that we're going to solve using ZIO fibers here :)

  // part 1 - an effect which reads one file and counts the words there
  def countWords(path: String): UIO[Int] = {
    ZIO.succeed {
      val source = scala.io.Source.fromFile(path)
      val nWords = source.getLines().mkString(" ").split(" ").count(_.nonEmpty)
      println(s"Counted $nWords in path $path")
      source.close()
      nWords
    }
  }

  // part 2 - spin up fibers for all the files
  def wordCountParallel(n: Int): UIO[Int] = {
    val effects: Seq[ZIO[Any, Nothing, Int]] = (1 to n)
      .map(i => s"src/main/resources/testfile_$i.txt") // paths
      .map(countWords) // list of effects
      .map(_.fork) // list of effects returning fibers
      .map(fiberEff =>
        fiberEff.flatMap(_.join)
      ) // list of effects returning values

    effects.reduce { (zioa, ziob) =>
      for {
        a <- zioa
        b <- ziob
      } yield a + b
    }
  }

  def generateRandomFile(path: String): Unit = {
    val random = scala.util.Random
    val chars = 'a' to 'z'
    val nWords = random.nextInt(2000) // at most 2000 words

    // 1 word (of max_length=10) for every 1 to nWords
    val content = (1 to nWords)
      .map(_ =>
        (1 to random.nextInt(10)).map(_ => chars(random.nextInt(26))).mkString
      )
      .mkString(" ")

    val writer = new FileWriter(new File(path))
    writer.write(content)
    writer.flush() // in case there are buffers that need to be written to disk
    writer.close()
  }
  // def run = ZIO.succeed(
  //   (1 to 10).foreach(i =>
  //     generateRandomFile(s"src/main/resources/testfile_$i.txt")
  //   )
  // )

  def run = wordCountParallel(10).debugThread
}
