package com.rockthejvm.part4coordination

import zio._
import com.rockthejvm.utils._
import java.util.concurrent.TimeUnit

object Refs extends ZIOAppDefault {
  
  // refs are purely functional atomic references (thread safe variable, it protects from reads / writes from multiple threads)
  val atomicMOL: ZIO[Any, Nothing, Ref[Int]] = Ref.make(42)

  // obtain a value
  val mol = atomicMOL.flatMap { ref => 
    ref.get // returns a UIO[Int], thread-safe getter  
  }

  // changing
  val setMol = atomicMOL.flatMap { ref => 
    ref.set(100) // UIO[Unit], thread-safe setter  
  }

  // get + change in ONE atomic operation
  val gsMol = atomicMOL.flatMap {
    ref => ref.getAndSet(500)
  }

  // update - run a function on the value
  val updatedMOL = atomicMOL.flatMap { ref => 
      ref.update(_ * 100) // update is atomic and it's kinda saying ref.getAndSet(f(value))
  }

  val updatedMolWithValue = atomicMOL.flatMap { ref => 
    ref.updateAndGet(_ * 100) // returns the NEW value
    ref.getAndUpdate(_ * 100) // returns the OLD value  
  }

  // modify - returning a different type and do an operation on the value
  // This returns a UIO[String], but the value is multiplied by 1000
  val modifiedMol: UIO[String] = atomicMOL.flatMap { ref => 
    ref.modify(value => (s"my current value is $value", value * 100))  
  }

  // example: distributing work
  def demoConcurrentWorkImpure(): UIO[Unit] = {
    var count = 0

    def task(workload: String): UIO[Unit] = {
      val wordCount = workload.split(" ").length
      for {
        _ <- ZIO.succeed(s"Counting words for: $workload").debugThread
        newCount <- ZIO.succeed(count + wordCount)
        _ <- ZIO.succeed(s"New total: $newCount").debugThread
        _ <- ZIO.succeed(count += wordCount) // update var
      } yield ()
    }

    val effects = List("I love ZIO", "This Ref is cool", "Daniel writes a LOT of code!").map(task)

    ZIO.collectAllParDiscard(effects)
  }

  def demoConcurrentWorkPure(): UIO[Unit] = {
    
    def task(workload: String, total: Ref[Int]): UIO[Unit] = {
      val wordCount = workload.split(" ").length
      for {
        _ <- ZIO.succeed(s"Counting words for: $workload: $wordCount").debugThread
        newCount <- total.updateAndGet(_ + wordCount)
        _ <- ZIO.succeed(s"New total: $newCount").debugThread
      } yield ()
    }

    for {
      counter <- Ref.make(0)
      _ <- ZIO.collectAllParDiscard(List("I love ZIO", "This Ref is cool", "Daniel writes a LOT of code!")
              .map(load => task(load, counter)))
    } yield ()
  }
  /* 
    - NOT THREAD SAFE!
    - hard to debug in case of failure
    - mixing pure and impure code
   */


  def run = demoConcurrentWorkPure()
}

object ExercisesRefs extends ZIOAppDefault {
  /* 
    1 - Refactor this code using Ref
   */
  def tickingClockImpure(): UIO[Unit] = {
    var ticks = 0L
    // print the current time every 1s + increase a counter ("ticks")
    def tickingClock: UIO[Unit] = for {
      _ <- ZIO.sleep(1.second)
      _ <- Clock.currentTime(TimeUnit.MILLISECONDS).debugThread
      _ <- ZIO.succeed(ticks += 1)
      _ <- tickingClock
    } yield()
    
    // print the total ticks count every 5s
    def printTicks: UIO[Unit] = for {
      _ <- ZIO.sleep(5.seconds)
      _ <- ZIO.succeed(s"TICKS: $ticks").debugThread
      _ <- printTicks
    } yield ()

    (tickingClock zipPar printTicks).unit
  }

  // Solution:
  def tickingClockPure(): UIO[Unit] = {
    // print the current time every 1s + increase a counter ("ticks")
    def tickingClock(ticks: Ref[Long]): UIO[Unit] = for {
      _ <- ZIO.sleep(1.second)
      _ <- Clock.currentTime(TimeUnit.MILLISECONDS).debugThread
      _ <- ticks.update(_ + 1)
      _ <- tickingClock(ticks)
    } yield()
    
    // print the total ticks count every 5s
    def printTicks(ticks: Ref[Long]): UIO[Unit] = for {
      _ <- ZIO.sleep(5.seconds)
      _ <- ZIO.succeed(s"TICKS: $ticks").debugThread
      _ <- printTicks(ticks)
    } yield ()

    for {
      ticks <- Ref.make(0L)
      _ <- tickingClock(ticks) zipPar printTicks(ticks)
    } yield ()
    
  }

  def run = tickingClockPure()
}
