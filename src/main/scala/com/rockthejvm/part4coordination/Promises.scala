package com.rockthejvm.part4coordination

import zio._
import com.rockthejvm.utils._

object Promises extends ZIOAppDefault {
  
  val aPromise: ZIO[Any, Nothing, Promise[Throwable, Int]] = Promise.make[Throwable, Int]

  // await: block the fiber until the promise has the value
  val reader = aPromise.flatMap { promise =>
    promise.await
  }

  // succeed, fail, complete
  val writer = aPromise.flatMap { promise => 
    promise.succeed(42)  
  }

  def demoPromise(): Task[Unit] = {
    // producer - consumer problem: Classical problem in distributed / parallel computing.
    // Two separate entities, one is called the producer, and the other is the consumer
    // The producer stores something in a memory zone, from which the consumer reads and then process it later
    // These two entities are operating independently
    // We're going to demonstrate this problem by having the producer produce with 1 single write, and the consumer waits for reading
    def consumer(promise: Promise[Throwable, Int]): Task[Unit] = for {
      _ <- ZIO.succeed("[Consumer] waiting for result...").debugThread
      mol <- promise.await
      _ <- ZIO.succeed(s"[Consumer] I got the result: $mol").debugThread
    } yield()

    def producer(promise: Promise[Throwable, Int]): Task[Unit] = for {
      _ <- ZIO.succeed("[Producer] crunching numbers...").debugThread
      _ <- ZIO.sleep(3.seconds)
      _ <- ZIO.succeed("[Producer] complete.").debugThread
      mol <- ZIO.succeed(42)
      _ <- promise.succeed(mol)
    } yield ()

    for {
      promise <- Promise.make[Throwable, Int]
      _ <- consumer(promise) zipPar producer(promise)
    } yield()
  }

  /*
    Why promises are useful:
      - It's a purely functional block on a fiber until you get a signal from another fiber
      - Waiting for a value which may not be available, without thread starvation
      - It's a way of inter-fiber communication
  */

  // Simulate downloading file from multiple parts
  val fileParts = List("I ", "love S", "cala", " with pure FP an", "d ZIO! <EOF>")
  // let's define a program that will 'download' this file chunks in parallel, simulating it as a slow operation
  // and there will be another program that will be notified when the processing is complete
  def downloadFileWithRef(): UIO[Unit] = {

    def downloadFile(contentRef: Ref[String]): UIO[Unit] =
      ZIO.collectAllDiscard(
        fileParts.map { part => 
          ZIO.succeed(s"got '$part'").debugThread *> ZIO.sleep(1.second) *> contentRef.update(_ + part)  
        }
      )
    
    def notifyFileComplete(contentRef: Ref[String]): UIO[Unit] = for {
      file <- contentRef.get
      _ <- if (file.endsWith("<EOF>")) ZIO.succeed("File download complete.").debugThread
      else ZIO.succeed("downloading...").debugThread *> ZIO.sleep(500.millis) *> notifyFileComplete(contentRef)
    } yield ()

    for {
      contentRef <- Ref.make("")
      _ <- downloadFile(contentRef) zipPar notifyFileComplete(contentRef)
    } yield()
  }

  // We can avoid this polling from the notifyFileComplete with a Promise:
  def downloadFileWithRefAndPromise(): Task[Unit] = {
    def downloadFile(contentRef: Ref[String], promise: Promise[Throwable, String]): UIO[Unit] =
      ZIO.collectAllDiscard(
        fileParts.map { part =>
          for {
            _ <- ZIO.succeed(s"got '$part'").debugThread
            _ <- ZIO.sleep(1.second)
            file <- contentRef.updateAndGet(_ + part)  
            _ <- if (file.endsWith("<EOF>")) promise.succeed(file) else ZIO.unit
          } yield()
        }
      )
    
    def notifyFileComplete(promise: Promise[Throwable, String]): Task[Unit] = for {
      _ <- ZIO.succeed("downloading...").debugThread
      file <- promise.await
      _ <- ZIO.succeed(s"file download complete: $file").debugThread
    } yield ()

    for {
      contentRef <- Ref.make("")
      promise <- Promise.make[Throwable, String]
      _ <- downloadFile(contentRef, promise) zipPar notifyFileComplete(promise)
    } yield()
  }

  def run = downloadFileWithRefAndPromise()
}
