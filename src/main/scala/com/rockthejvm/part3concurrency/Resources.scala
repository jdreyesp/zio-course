package com.rockthejvm.part3concurrency

import zio._ 
import java.util.Scanner
import java.io.File
import utils.given
import com.rockthejvm.utils._

object Resources extends ZIOAppDefault {
  
  // finalizers
  def unsafeMethod(): Int = throw new RuntimeException("Not an int here for you")
  val anAttempt = ZIO.attempt(unsafeMethod())

  // finalizers
  val anAttemptWithFinalizers = anAttempt.ensuring(ZIO.succeed("finalizer!").debugThread)   

  // multiple finalizers
  val anAttemptWithFinalizers2 = anAttemptWithFinalizers.ensuring(ZIO.succeed("Another finalizer!").debugThread)

  // The whole point of using finalizers is to manage a resource lifecycle. Example:
  class Connection(url: String) {
    def open() = ZIO.succeed(s"opening connection to $url...").debugThread
    def close() = ZIO.succeed(s"closing connection to $url...").debugThread
  }

  object Connection {
    def create(url: String) = ZIO.succeed(new Connection(url))
  }

  // if for example we want to fetch pages from the connection
  val fetchUrl = for {
    conn <- Connection.create("rockthejvm.com")
    fib <- (conn.open() *> ZIO.sleep(300.seconds)).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield() // resource leak (there's no closure of the connection anywhere)


  // Correct fetchUrl:
    val correctFetchUrl = for {
    conn <- Connection.create("rockthejvm.com")
    fib <- (conn.open() *> ZIO.sleep(300.seconds)).ensuring(conn.close()).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield()

  // Problem with previous solution is that it's tedious: every time we need to fetch an url we need 
  // to ensure that the connection is closed, etc.
  // A different approach could be with acquireRelease:
  val cleanConnection = ZIO.acquireRelease(Connection.create("rockthejvm.com"))(_.close())

  val fetchWithResource = for {
    conn <- cleanConnection
     fib <- (conn.open() *> ZIO.sleep(300.seconds)).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield ()

  /* Benefits of acquiringRelease:
    - acquiring cannot be interrupted
    - all finalizers are guaranteed to run
  */

  // Caveat: See that the acquireRelease needs a Scope in the [R]. 
  // In order to remove that scope, we need to:
  val fetchWithScopedResource = ZIO.scoped(fetchWithResource) // and this removes the scope

  // acquireReleaseWith: combines the acquiring effect, the closure effect and the usage effect in a giant expression
  val cleanConnection_v2 = ZIO.acquireReleaseWith(
    Connection.create("rockthejvm.com") // acquire 
  ) (
    _.close() // release
  ) (
    conn => conn.open() *> ZIO.sleep(300.seconds) // Use
  )

  val fetchWithResource_v2 = for {
    fib <- cleanConnection_v2.fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  } yield ()

  // Since we specify the usage on the acquireReleaseWith, that will return an effect with no scope needed.
  def run = fetchWithResource_v2
}

object MyExercisesResources extends ZIOAppDefault {

  /* Exercises:
    1. Use the acquireRelease to open a file, print all lines (one every 100 millis), then close the file
  */
  def openFileScanner(path: String): UIO[Scanner] = 
    ZIO.succeed(new Scanner(new File(path)))

    // why scanner is not seeing in here?
  def readLines(scanner: Scanner): UIO[Unit] = 
    ZIO.iterate(())(_ => scanner.hasNextLine())(_ => ZIO.succeed(scanner.nextLine()).debugThread *> ZIO.sleep(100.millis)) 

  def acquireOpenFile(path: String): UIO[Unit] =
    ZIO.succeed(println("Acquiring scanner...")) *> 
    ZIO.acquireReleaseWith(
      openFileScanner(path).debugThread
      )(
        scanner => ZIO.succeed(scanner.close())
      )(readLines)

  val testInterruptFileDisplay = for {
    fib <- acquireOpenFile("src/main/scala/com/rockthejvm/part3concurrency/Parallelism.scala").fork
    _ <- ZIO.sleep(2.seconds) *> fib.interrupt
  } yield()

  def run = testInterruptFileDisplay
}

// Additionally, using acquireRelease is better than using acquireReleaseWith when multiple resources need to 
// be managed, since acquireReleaseWith forces you to specify the usage section, and then we will have something like:
// ZIO.acquireReleaseWith(open)(close) {
//    ZIO.acquireReleaseWith(open2)(close2) {
//      ...
//    }
//}
// etc.
// so it's better to use acquireRelease in a for comprehension, like
// for {
//   resource1 <- ZIO.acquireRelease(open)(close)
//   resource2 <- ZIO.acquireRelease(open2)(close2)
//   _ <- //usage goes here
//  }