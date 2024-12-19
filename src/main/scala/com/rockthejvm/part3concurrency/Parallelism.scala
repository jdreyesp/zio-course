package com.rockthejvm.part3concurrency

import zio._
import utils.given
import com.rockthejvm.utils._

object Parallelism extends ZIOAppDefault {

  val meaningOfLife = ZIO.succeed(42)
  val favLang = ZIO.succeed("Scala")

  val combined = meaningOfLife.zip(favLang)

  // combine in parallel
  val combinedPar = meaningOfLife.zipPar(favLang)

  /* There are quite some concerns about the implementation of zipPar:
    - start each zio on fibers
    - what if one of the zio fails? the other should be interrupted
    - what if one of the zios is interrupted? (Because it's spinned up from other fiber)? The entire thing should be interrupted
    - what if the entire thing is interrupted? we need to interrupt both effects
   */
  // trying a zipPar combinator
  // hint: fork/join/await, interrupt
  def myZipPar[R, E, A, B](
      zioa: ZIO[R, E, A],
      ziob: ZIO[R, E, B]
  ): ZIO[R, E, (A, B)] = {
    val exits = for {
      fiba <- zioa.fork
      fibb <- ziob.fork
      exita <- fiba.await
      exitb <- exita match {
        case Exit.Success(value) => fibb.await
        case Exit.Failure(_)     => fibb.interrupt
      }
    } yield (exita, exitb)

    exits.flatMap {
      case (Exit.Success(a), Exit.Success(b)) => ZIO.succeed((a, b))
      case (Exit.Success(_), Exit.Failure(cause)) => ZIO.failCause(cause) // one of them failed
      case (Exit.Failure(cause), Exit.Success(_)) => ZIO.failCause(cause)
      case (Exit.Failure(c1), Exit.Failure(c2))   => ZIO.failCause(c1 && c2)
    }
  }
  // In any case, this implementation of myZipPar is not entirely correct, since we always start checking exita first
  // and then exitb, so we should race them

  // all parallel combinators
  // zipPar, zipWithPar

  // collectorAllPar
  val effects: Seq[ZIO[Any, Nothing, Int]] = (1 to 10).map(i => ZIO.succeed(i).debugThread)
  val collectedValues: ZIO[Any, Nothing, Seq[Int]] = ZIO.collectAllPar(effects) // "traverse"

  // foreachPar
  val printlnParallel = ZIO.foreachPar((1 to 10).toList)(i => ZIO.succeed(println(i)))

  // reduceAllPar, mergeAllPar
  val sumPar = ZIO.reduceAllPar(ZIO.succeed(0), effects)(_ + _) // reduce for effects (based on merge all pars)
  val sumPar_v2 = ZIO.mergeAllPar(effects)(0)(_ + _) // merge all pars (the most generic one
  
  /* 
    - if all the effects succeed, all good
    - one effect fails => everyone else is interrupted, error is surfaced
    - one effect is interrupted => everyone else is interrupted, and the error = interruption (for the big expression)
    - if the entire thing is interrupted (the main fiber) => all the effects are interrupted 
   */

  def run = collectedValues.debugThread
}

object ExercisesParallelism extends ZIOAppDefault {

  /* 
  Exercise: Do the count word, but using a parallel combinator
   */
  // Original implementation with no combinator
  def countWords(path: String): UIO[Int] = {
    ZIO.succeed {
      val source = scala.io.Source.fromFile(path)
      val nWords = source.getLines().mkString(" ").split(" ").count(_.nonEmpty)
      println(s"Counted $nWords in path $path")
      source.close()
      nWords
    }
  }

  def wordCountParallel_v2(n: Int): UIO[Int] = {
    val effects = (1 to n)
      .map(i => s"src/main/resources/testfile_$i.txt") // paths
      .map(countWords) // list of effects

    ZIO.reduceAllPar(ZIO.succeed(0), effects)(_ + _)
  }

  def run = wordCountParallel_v2(10).debugThread

}
