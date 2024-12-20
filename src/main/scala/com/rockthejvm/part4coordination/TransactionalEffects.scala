package com.rockthejvm.part4coordination

import zio._
import com.rockthejvm.utils._
import zio.stm._

object TransactionalEffects extends ZIOAppDefault {

  // STM (Software transactional Memory) = atomic effects (once it's started it can't be interrupted and will complete)
  val anSTM: ZSTM[Any, Nothing, Int] = STM.succeed(42)
  val aFailedSTM: ZSTM[Any, String, Int] = STM.fail("something bad")
  val anAttempt: ZSTM[Any, Throwable, Int] = STM.attempt(42 / 0)
  // we also have map, flatmap and for

  // type aliases
  val ustm: USTM[Int] = STM.succeed(42)
  val anSTM_v2: STM[Nothing, Int] = STM.succeed(42) // same as IO[E, A]

  // STM vs ZIO
  // compose STMs to obtain othet STMs
  // evaluation is FULLY ATOMIC (it's called 'commit' (like transaction.commit :) ), and it evaluates to a ZIO (it's not like ZIO, that evaluates to a value (unsafe call))
  val anAtomicEffect: ZIO[Any, Throwable, Int] = anAttempt.commit

  // example:
  def transferMoney(sender: Ref[Long], receiver: Ref[Long], amount: Long): ZIO[Any, String, Long] =
    for {
      senderBalance <- sender.get
      _ <- if (senderBalance < amount) ZIO.fail("transfer failed: Insufficient funds") else ZIO.unit
      _ <- sender.update(_ - amount)
      _ <- receiver.update(_ + amount)
      newBalance <- sender.get
    } yield newBalance
  // This is not transactional since multiple fibers can run `sender.update(_ - amount)` before it gets evaluated once,
  // so it will lead to inconsistent state.

  // Same solution but using STMs
  // Note about TRef instead of Ref and STM instead of ZIO
  def transferMoneyTx(sender: TRef[Long], receiver: TRef[Long], amount: Long): STM[String, Long] =
    for {
      senderBalance <- sender.get
      _ <- if (senderBalance < amount) STM.fail("transfer failed: Insufficient funds") else STM.unit
      _ <- sender.update(_ - amount)
      _ <- receiver.update(_ + amount)
      newBalance <- sender.get
    } yield newBalance

  def cannotExploit() = for {
    sender <- TRef.make(1000L).commit
    receiver <- TRef.make(0L).commit
    fib1 <- transferMoneyTx(sender, receiver, 1000).commit.fork
    fib2 <- transferMoneyTx(sender, receiver, 1000).commit.fork // This will fail for sure, since there are no funds
    _ <- (fib1 zip fib2).join
    _ <-
      receiver.get.commit.debugThread // This will never be printed since fib1 or fib2 will fail (with insufficient funds)
  } yield ()

  /*
    STM data structures. Since ZIO cannot assure atomicity, we need to replicate all data structures like arrays, list, maps, ...
      - atomic variable: TRef
        - same API: get, update, modify, set
   */
  val aVariable: USTM[TRef[Int]] = TRef.make(42)

  // TArray -- all operations are atomic
  val specifiedValuesTArray: USTM[TArray[Int]] = TArray.make(1, 2, 3)
  val iterableArray: USTM[TArray[Int]] = TArray.fromIterable(List(1, 2, 3, 4, 5))
  // get/apply
  // see that getting an element can be cumbersome, but in the end we also get the benefit of running this atomically
  val tArrayGetElement: USTM[Int] = for {
    tArray <- iterableArray
    elem <- tArray(2)
  } yield elem

  // update
  val tArrayUpdateElem: USTM[TArray[Int]] = for {
    tArray <- iterableArray
    _ <- tArray.update(index = 1, el => el + 10)
  } yield tArray

  // transform
  val transformedArray: USTM[TArray[Int]] = for {
    tArray <- iterableArray
    _ <- tArray.transform(_ * 10) // like map, but in place
  } yield tArray

  // TSet
  val specificValuesTSet: USTM[TSet[Int]] = TSet.make(1, 2, 3, 4, 5, 1, 2, 3)
  // contains
  val tSetContainsElement: USTM[Boolean] = for {
    tSet <- specificValuesTSet
    res <- tSet.contains(3)
  } yield res

  // put
  val putElem: USTM[TSet[Int]] = for {
    tSet <- specificValuesTSet
    _ <- tSet.put(7)
  } yield tSet

  // delete
  val deleteElem: USTM[TSet[Int]] = for {
    tSet <- specificValuesTSet
    _ <- tSet.delete(1)
  } yield tSet
  // union, intersect, diff if you want to interact with other TSets
  // removeIf, retainIf
  // transform, fold + STM version

  // TMap
  val aTMapEffect: USTM[TMap[String, Int]] = TMap.make(("Daniel", 123), ("Alice", 456), ("QE2", 999))
  // put
  val putElemTMap: USTM[TMap[String, Int]] = for {
    tMap <- aTMapEffect
    _ <- tMap.put("Master Yoda", 111)
  } yield tMap

  // get
  val getElem: USTM[Option[Int]] = for {
    tMap <- aTMapEffect
    elem <- tMap.get("Daniel")
  } yield elem
  // delete, removeIf, retainIf
  // transform + STM
  // fold + STM
  // foreach
  // keys, values

  // TQueue
  val tQueueBounded: USTM[TQueue[Int]] = TQueue.bounded[Int](5)
  // offer/offerAll
  val demoOffer: USTM[TQueue[Int]] = for {
    tQueue <- tQueueBounded
    _ <- tQueue.offerAll(List(1, 2, 3, 4, 5, 6))
  } yield tQueue

  // takeAll
  // Chunk - ZIO's purely functional, much richer ArrayList API
  val demoTakeAll: USTM[Chunk[Int]] = for {
    tQueue <- demoOffer
    elems <- tQueue.takeAll
  } yield elems

  // takeOption, peek
  // toList, toVector
  // size

  // TPriorityQueue
  val maxQueue: USTM[TPriorityQueue[Int]] = TPriorityQueue.make(3, 4, 1, 2, 5)

  /*
    Concurrent coordination
   */
  // We have TRef that we explored earlier

  // We also have TPromise (same as ZIO Promise)
  val tPromise: USTM[TPromise[String, Int]] = TPromise.make[String, Int]
  // await
  val tPromiseAwait: STM[String, Int] = for {
    promise <- tPromise
    res <- promise.await
  } yield res

  // succeed/fail/complete
  val demoSucceed: USTM[Unit] = for {
    p <- tPromise
    _ <- p.succeed(100)
  } yield ()

  // TSemaphore
  val tSemaphoreEffect: USTM[TSemaphore] = TSemaphore.make(10)
  // acquire + acquireN
  val semaphoreAcq: USTM[Unit] = for {
    sem <- tSemaphoreEffect
    _ <- sem.acquire
  } yield ()

  // release + releaseN
  val semaphoreRel: USTM[Unit] = for {
    sem <- tSemaphoreEffect
    _ <- sem.release
  } yield ()

  // withPermit - whatever is inside the withPermit is run in a transaction, but this returns a ZIO, not an STM
  val semWithPermit: UIO[Int] = tSemaphoreEffect.commit.flatMap { sem =>
    sem.withPermit {
      ZIO.succeed(42)
    }
  }

  // TReentrantLock - Mutex - can acquire the same lock multiple times without deadlock
  // It was created by a particular problem in CS - readers/writers problem
  // Only 1 writer can write at anyone time. However, multiple readers can read at anyone time
  // It has two locks: read lock (lower priority) and write lock (higher priority). This means: when a writer is writing, all readers that are
  // reading from the shared resource will block. This means that readers will only read when there's no writer writing at that moment.
  // At the same time, a writer will not be able to write while there are readers reading from the shared resource. It will wait until all readers
  // are done.
  val reentrantLockEffect = TReentrantLock.make
  val demoReentrantLock = for {
    lock <- reentrantLockEffect
    _ <- lock.acquireRead // acquires the read lock (any writer will not be able to write)
    _ <- STM.succeed(100) // THis is the critical section, only those that acquire read lock can access
    readLocked <- lock.readLocked // status of the lock, whether is read-locked
    writeLocked <- lock.writeLocked // status of the lock for writer
  } yield ()

  // 10 readers that will try to read from a shared resource and 1 writer that will try to write to it
  def demoReadersWriters(): UIO[Unit] = {

    def read(i: Int, lock: TReentrantLock): UIO[Unit] = for {
      _ <- lock.acquireRead.commit
      // critical region
      _ <- ZIO.succeed(s"[task $i] taken the read lock, reading...").debugThread
      time <- Random.nextIntBounded(1000)
      _ <- ZIO.sleep(time.millis)
      res <- Random.nextIntBounded(100) // actual simulated computation
      _ <- ZIO.succeed(s"[task $i] read value: $res")
      // critical region end
      _ <- lock.releaseRead.commit
    } yield ()

    def write(lock: TReentrantLock): UIO[Unit] = for {
      // writer
      _ <- ZIO.sleep(200.millis)
      _ <- ZIO.succeed("[writer] trying to write...").debugThread
      _ <- lock.acquireWrite /*This is an STM so I need to commit*/ .commit
      // critical region
      _ <- ZIO.succeed("[writer] I'm able to write").debugThread
      // critical region end
      _ <- lock.releaseWrite.commit
    } yield ()

    for {
      lock <- TReentrantLock.make.commit
      readersFib <- ZIO.collectAllParDiscard((1 to 10).map(read(_, lock))).fork
      writersFib <- write(lock).fork
      _ <- readersFib.join
      _ <- writersFib.join
    } yield ()
  }
  def run = demoReadersWriters()
}
