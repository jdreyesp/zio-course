package com.rockthejvm.part4coordination

import zio._
import com.rockthejvm.utils._
import scala.collection.immutable.Queue

// Mutex: Concurrency primitive that allows you to lock a certain area of code
// from concurrent access from multiple threads (in this case from ZIO fibers)

// If a fiber call acquire, then the Mutex will be locked for any other fiber that call acquire
// All the subsequente fibers that will try to acquire will be semantically blocked until the fiber that took the mutex
// call release

// Let's create our own Mutex
abstract class Mutex {
  def acquire: UIO[Unit]
  def release: UIO[Unit]
}

object Mutex {

  type Signal = Promise[Nothing, Unit]
  case class State(locked: Boolean, waiting: Queue[Signal])
  val unlocked = State(locked = false, Queue())

  // There's a problem though with this implementation. If signal.await is interrupted, we would need to manage
  // what to do in that case. Please check https://github.com/rockthejvm/zio-course/blob/4.6-semaphores/src/main/scala/com/rockthejvm/part4coordination/Mutex.scala
  // for the solution
  def make: UIO[Mutex] = Ref.make(unlocked).map { (state: Ref[State]) =>
    new Mutex {

      /*
      Change the state of the Ref
      - if the mutex is unlocked, lock it
      - if the mutex is locked, state becomes (true, queue + new signal) and WAIT for that
       */
      override def acquire: UIO[Unit] = Promise.make[Nothing, Unit].flatMap { signal =>
        state.modify {
          case State(false, _)      => (ZIO.unit -> State(locked = true, Queue()))
          case State(true, waiting) => signal.await -> State(locked = true, waiting.enqueue(signal))
        }.flatten
      }

      override def release: UIO[Unit] = state.modify {
        case State(false, _)                         => ZIO.unit -> unlocked
        case State(true, waiting) if waiting.isEmpty => ZIO.unit -> unlocked
        case State(true, waiting) => {
          val (first, rest) = waiting.dequeue
          first.succeed(()).unit -> State(locked = true, rest)
        }
      }.flatten

    }
  }
}

object MutexPlayground extends ZIOAppDefault {

  def workInCriticalRegion(): UIO[Int] =
    ZIO.sleep(1.second) *> Random.nextIntBounded(100)

  def demoNonLockingTasks() =
    ZIO.collectAllParDiscard((1 to 10).toList.map { i =>
      for {
        _ <- ZIO.succeed(s"[task $i] working...").debugThread
        result <- workInCriticalRegion()
        _ <- ZIO.succeed(s"[task $i] got result: $result").debugThread
      } yield ()
    })

  def createTask(id: Int, mutex: Mutex): UIO[Unit] = for {
    _ <- ZIO.succeed(s"[task $id] waiting for mutex...").debugThread
    _ <- mutex.acquire
    // critical region
    _ <- ZIO.succeed(s"[task $id] working...").debugThread
    result <- workInCriticalRegion()
    _ <- ZIO.succeed(s"[task $id] got result: $result, releasing mutext").debugThread
    // critical region
    _ <- mutex.release

  } yield ()

  def demoLockingTasks = for {
    mutex <- Mutex.make
    tasks <- ZIO.collectAllParDiscard((1 to 10).toList.map { i => createTask(i, mutex) })
  } yield ()

  def run = demoLockingTasks
}
