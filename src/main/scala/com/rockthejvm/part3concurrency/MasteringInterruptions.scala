package com.rockthejvm.part3concurrency

import zio._
import com.rockthejvm.part3concurrency.MasteringInterruptions.authFlow
import utils.given
import com.rockthejvm.utils._

object MasteringInterruptions extends ZIOAppDefault {

  // recap of interruptables:
  // fib.interrupt OR
  // interruptable effects: ZIO.race, ZIO.zipPar, ZIO.collectAllPar
  // outliving parent fiber

  // manual interruption (in this example we won't see 42 in the output since it was manually interrupted before)
  val aManuallyInterruptedZIO = ZIO.succeed("computing...").debugThread *> ZIO.interrupt *> ZIO.succeed(42).debugThread

  // finalizer
  val effectWithInterruptionFinalizer =
    aManuallyInterruptedZIO.onInterrupt(ZIO.succeed("I was interrupted").debugThread)

  // uninterruptable
  // Example: Let's say I have an e-commerce application, and the payment flow (once it's started) it's not supposed
  // to ever be interrupted
  val fussyPaymentSystem = (
    ZIO.succeed("payment running, don't cancel me...").debugThread *>
      ZIO.sleep(1.second) *> // Simulate the actual payment
      ZIO.succeed("payment completed").debugThread
  ).onInterrupt(ZIO.succeed("MEGA CANCEL OF DOOM!").debugThread) // we don't want this triggered!

  val cancellationOfDoom = for {
    fib <- fussyPaymentSystem.fork
    _ <- ZIO.sleep(500.millis) *> fib.interrupt
    _ <- fib.join
  } yield ()
  // We don't want to interrupt `fussyPaymentSystem`!!

  // Solution: ZIO.uninterruptable
  val atomicPayment = ZIO.uninterruptible(fussyPaymentSystem) // We made a ZIO atomic
  val atomicPayment_v2 = fussyPaymentSystem.uninterruptible // same

  val noCancellationOfDoom = for {
    fib <- atomicPayment.fork
    _ <- ZIO.sleep(500.millis) *> fib.interrupt
    _ <- fib.join
  } yield ()

  // interruptibility is regional
  val zio1 = ZIO.succeed(1)
  val zio2 = ZIO.succeed(2)
  val zio3 = ZIO.succeed(3)

  val zioComposed = (zio1 *> zio2 *> zio3).uninterruptible // This makes all ZIOs uninterruptible
  val zioComposed2 =
    (zio1 *> zio2.interruptible *> zio3).uninterruptible // inner scopes override outer scopes, so zio2 will be interruptible
  // Even though this is powerful, it's done in rare cases, because ZIO apps normally use `uninterruptibleMask` (see below)

  // uninterruptibleMask
  /* example: authentication service
      - input password, can be interrupted, because otherwise it might block the fiber indefinitely
      - verify password, which cannot be interrupted once it's triggered
   */
  val inputPassword = for {
    _ <- ZIO.succeed("Input password:").debugThread
    _ <- ZIO.succeed("(typing password)").debugThread
    _ <- ZIO.sleep(2.seconds) // simulating the user typing the password
    password <- ZIO.succeed("RockTheJVM1!")
  } yield password

  def verifyPassword(pw: String) = for {
    _ <- ZIO.succeed("verifying...").debugThread
    _ <- ZIO.sleep(2.seconds) // simulating verification process
    result <- ZIO.succeed(pw == "RockTheJVM1!")
  } yield result

  val authFlow = ZIO.uninterruptibleMask { restore =>
    // EVERYTHING in here is uninterruptible... EXCEPT whoever is wrapped with restore (see inputPassword effect)
    for {
      pw <- restore(inputPassword) /* <-- Everything interruptible except this thing */.onInterrupt(
        ZIO.succeed("Authentication timed out. Try again later.").debugThread
      )
      // ^^ This restores the interruptibility of the ZIO at the time of the call
      verification <- verifyPassword(pw)
      _ <-
        if (verification) ZIO.succeed("Authentication successful").debugThread
        else ZIO.succeed("Authentication failed").debugThread
    } yield ()
  }

  val authProgram = for {
    authFib <- authFlow.fork
    _ <- ZIO.sleep(3.seconds) *> ZIO.succeed("Attempting to cancel authentication...").debugThread *> authFib.interrupt
    _ <- authFib.join
  } yield ()

  // uninterruptibleMask is one of the most powerful features of ZIO since you can control interruptibility with pinpoint accuracy

  def run = authProgram
}

object ExercisesMasteringInterruptions extends ZIOAppDefault {

  /*
  Exercises:
    1. guess what these effects will do without running them?
   */
  val cancelBeforeMol = ZIO.interrupt *> ZIO.succeed(42).debugThread
  val uncancelBefroreMol = ZIO.uninterruptible(ZIO.interrupt *> ZIO.succeed(42).debugThread)

  // solution:
  // 1. It will interrupt at ZIO.interrupt and the 42 will never be printed
  // 2. ZIO.interrupt takes precedence since it's in a inner scope than the other

  // 2. Wrapping an uninterruptibleMask in another uninterruptibleMask:
  val authProgram_v2 = for {
    authFib <- ZIO.uninterruptibleMask(_ => authFlow).fork
    _ <- ZIO.sleep(1.seconds) *> ZIO.succeed("attempting to cancel authentication...").debugThread *> authFib.interrupt
    _ <- authFib.join
  } yield ()
  // Solution: This uninterruptibleMask will make authFlow uninterruptible, regardless if authflow has a recover(inputPassword)
  // So we have to be careful when wrapping multiple uninterruptible or uninterruptibleMask

  // 3.
  val threeStepProgram = {
    val sequence = ZIO.uninterruptibleMask { restore =>
      for {
        _ <- restore(ZIO.succeed("interruptible").debugThread *> ZIO.sleep(1.second))
        _ <- ZIO.succeed("uninterruptible").debugThread *> ZIO.sleep(1.second)
        _ <- restore(ZIO.succeed("interruptible 2").debugThread *> ZIO.sleep(1.second))
      } yield ()
    }

    for {
      fib <- sequence.fork
      _ <- ZIO.sleep(1500.millis) *> ZIO.succeed("INTERRUPTING!").debugThread *> fib.interrupt
      _ <- fib.join
    } yield ()
  }

  def run = threeStepProgram
}
