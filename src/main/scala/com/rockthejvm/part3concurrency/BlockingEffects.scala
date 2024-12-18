package com.rockthejvm.part3concurrency

import zio._
import utils._
import java.util.concurrent.atomic.AtomicBoolean

object BlockingEffects extends ZIOAppDefault {
  

  def blockingTask(n: Int): UIO[Int] = 
    ZIO.succeed(s"Running blocking task $n").debugThread *>
    ZIO.succeed(Thread.sleep(10000)) *>
    blockingTask(n)

  val program = ZIO.foreachPar((1 to 100).toList)(blockingTask)
  // thread starvation

  // In order to prevent thread starvation, blocking tasks should be run in a specialised blocking threadpool
  val aBlockingZIO = ZIO.attemptBlocking {
    println(s"[${Thread.currentThread().getName()}] Running a long computation")
    Thread.sleep(10000)
    42
  }

  // blocking code cannot (usually) be interrupted
  val tryInterrupting = for {
    blockingFib <- aBlockingZIO.fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting..").debugThread *> blockingFib.interrupt
    mol <- blockingFib.join
  } yield mol
  // this previous piece of code will not interrupt the blocking code. Instead, the blocking code will finish and
  // then the calling fiber will finish (due to the active waiting in blockingFib.join)

  // In order to interrupt it, we can use attemptBlockingInterrupt
  // This is based on JAVA's Thread.interrupt
  val aBlockingInterruptibleZIO = ZIO.attemptBlockingInterrupt {
    println(s"[${Thread.currentThread().getName()}] Running a long computation")
    Thread.sleep(10000)
    42
  }

  // set a flag/switch: The correct way of interrupt a blocking operation
  def interruptibleBlockingEffect(cancelFlag: AtomicBoolean): Task[Unit] = 
    ZIO.attemptBlockingCancelable {
      (1 to 100000).foreach { element =>
        if (!cancelFlag.get()) {
          println(element)
          Thread.sleep(100)
        }
      }
    } (ZIO.succeed(cancelFlag.set(true))) // cancelling / interrupting effect

  val interruptableBlockingDemo = for {
    fib <- interruptibleBlockingEffect(new AtomicBoolean(false)).fork
    _ <- ZIO.sleep(2.seconds) *> ZIO.succeed("Interrupting ...").debugThread *> fib.interrupt
    _ <- fib.join
  } yield()

  // SEMANTIC blocking - no blocking of threads, descheduling the effect/fiber
  // ZIO.sleep is done through `yielding`:
  // (that is because ZIO.sleep is basically a data structure that will tell the ZIO runtime 
  // to wait for the desired time and then notify the fiber when the time has expired, it's NOT blocking threads)

  // this `val sleeping` is semantically blocking, interruptible 
  val sleeping = ZIO.sleep(1.second) 
  //VS this sleepingThread, which is blocking and uninterruptible 
  val sleepingThread = ZIO.succeed(Thread.sleep(1000)) 

  //yield - a hint to the ZIO runtime that it could move the execution of the computation to another thread.
  val chainedZIO = (1 to 1000).map(i => ZIO.succeed(i)).reduce(_.debugThread *> _.debugThread)
  val yieldingDemo = (1 to 1000).map(i => ZIO.succeed(i)).reduce(_.debugThread *> ZIO.yieldNow *> _.debugThread)
  // If we run previous code, the ZIO runtime will decide when to switch the thread that will execute this code
  // Looking back at ZIO.sleep, ZIO runtime will almost ALWAYS yield the computation to another thread since it knows
  // that a sleeping operation is blocking by default.
   
  def run = chainedZIO
}
