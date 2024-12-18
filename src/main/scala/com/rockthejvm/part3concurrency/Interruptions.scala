package com.rockthejvm.part3concurrency

import zio._
import utils._

object Interruptions extends ZIOAppDefault {

  val zioWithTime = {
    (ZIO.succeed("starting computation").debugThread *>
      ZIO.sleep(2.seconds) *> ZIO.succeed(42).debugThread)
      .onInterrupt(ZIO.succeed("I was interrupted").debugThread)
    // onDone
  }

  val interruption = for {
    fib <- zioWithTime.fork
    _ <- ZIO.sleep(1.second) *> ZIO
      .succeed("Interrupting!")
      // The calling fiber to interrupt will be blocked until the interrupted fiber is done/interrupted (semantic blocking)
      .debugThread *> fib.interrupt
    _ <- ZIO.succeed("Interruption complete").debugThread
    result <- fib.join
  } yield result

  // In this case we fork the interruption, so the waiting fiber will be other and this one can proceed
  // We also spin up the fork but we never join it. We lose control of the fiber, but it's known that the fibers
  // are not costly (they are simply data structures in the heap, not like threads which are linked to OS threads),
  // so as soon as the fiber is not referenced anymore, it will be cleaned up by the GC.
  val interruption_v2 = for {
    fib <- zioWithTime.fork
    _ <- (ZIO.sleep(1.second) *> ZIO
      .succeed("Interrupting!")
      .debugThread *> fib.interrupt).fork // or fib.interruptFork
    _ <- ZIO.succeed("Interruption complete").debugThread
    result <- fib.join
  } yield result

  // Automatic interruption
  // .fork creates a parent / child relationship between fibers, so that, if the parent fiber is done before
  // the child parent is, then the child will be automatically interrupted! Example:
  val parentEffect =
    ZIO.succeed("Spawning fiber").debugThread *>
      zioWithTime.fork *>
      ZIO.sleep(1.second) *>
      ZIO.succeed("parent successful").debugThread // done here

  val testOutlivingParent = for {
    parentEffectFib <- parentEffect.fork
    _ <- ZIO.sleep(3.seconds)
    _ <- parentEffectFib.join
  } yield ()

  // Alternative: .forkDaemon
  // forkDaemon will make the fiber a child of the MAIN fiber, not the fiber spawning it:
  val someEffect =
    ZIO.succeed("Spawning fiber").debugThread *>
      zioWithTime.forkDaemon *> // child of the MAIN fiber
      ZIO.sleep(1.second) *>
      ZIO.succeed("parent successful").debugThread // done here

  val testOutlivingParent_v2 = for {
    parentEffectFib <- someEffect.fork
    _ <- ZIO.sleep(3.seconds)
    _ <- parentEffectFib.join
  } yield ()

  // Racing: two effects can race each other and the fatest wins
  val slowEffect = (ZIO.sleep(2.seconds) *> ZIO.succeed("slow").debugThread)
    .onInterrupt(ZIO.succeed("[slow] interrupted").debugThread)
  val fastEffect = (ZIO.sleep(1.seconds) *> ZIO.succeed("fast").debugThread)
    .onInterrupt(ZIO.succeed("[fast] interrupted").debugThread)

  val resultEffect = for {
    race <- slowEffect.race(fastEffect).fork
    result <- race.join
  } yield (result)

  def run = resultEffect
}

object Exercises_Interruption extends ZIOAppDefault {

  // 1 - Implement a timeout function
  // If the zio finishes successfully before timeout = ZIO.succeed
  // If the zio finishes with a failure before timeout = ZIO.fail
  // If the zio does not finish before timeout = interrupt the effect
  def timeout[R, E, A](zio: ZIO[R, E, A], time: Duration): ZIO[R, E, A] = {
    for {
      fib <- zio.fork
      _ <- (ZIO.sleep(time) *> fib.interrupt).fork
      result <- fib.join
    } yield result
  }

  // 2 - timeout v2
  // Same as before but we return:
  // If the zio finishes successfully before timeout = ZIO.succeed of some(value)
  // If the zio finishes with a failure before timeout = ZIO.fail
  // If the zio does not finish before timeout = interrupt the effect and ZIO.succeed with None
  // hint: use foldCauseZIO  // Or Cause.isInterrupted
  def timeout_v2[R, E, A](
      zio: ZIO[R, E, A],
      time: Duration
  ): ZIO[R, E, Option[A]] = timeout(zio, time).foldCauseZIO(
    cause =>
      if (cause.isInterrupted) ZIO.succeed(None) else ZIO.failCause(cause),
    value => ZIO.succeed(Some(value))
  )

  def test_timeout_v2 = timeout_v2(
    ZIO.succeed("Starting").debugThread *> ZIO
      .sleep(2.seconds) *> ZIO.succeed("I made it!").debugThread,
    3.second
  ).debugThread

  def run = test_timeout_v2
}
