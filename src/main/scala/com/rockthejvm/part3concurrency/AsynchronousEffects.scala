package com.rockthejvm.part3concurrency

import zio._
import utils._
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object AsynchronousEffects extends ZIOAppDefault {

  // Asynchronous vs (concurrent || parallel) computation is that 
  // asynchronous is CALLBACK-based, that is it will invoke a callback function (where we'll create more effects in)
  // Asynchronous could be run concurrently or parallel, or neither, that's not related at all
  // in any case, the whole point of asymchronous is to be run in a separate thread/fiber in order to free the main thread/fiber, 
  // so being asynchronous and not concurrent/parallel does not make much sense.

  // asynchronous API
  object LoginService {
    case class AuthError(message: String)
    case class UserProfile(email: String, name: String)

    // thread pool
    val executor = Executors.newFixedThreadPool(8)

    // database
    val passwd = Map(
      "daniel@rockthejvm.com" -> "RockTheJVM1!"
    )

    // The profile data
    val database = Map(
      "daniel@rockthejvm.com" -> "Daniel"
    )

    def login(email: String, password: String)(onSuccess: UserProfile => Unit, onFailure: AuthError => Unit) = 
      executor.execute { () => 
        println(s"[${Thread.currentThread().getName()}] Attempting login for $email")
        passwd.get(email) match {
          case Some(`password`) /* Same as case Some(p) if p == password */ => onSuccess(UserProfile(email, database(email)))
          case Some(_) => onFailure(AuthError("Incorrect password"))
          case None => onFailure(AuthError(s"User with $email does not exists"))
        }
      }
  }

  def loginAsZIO(id: String, pw: String): ZIO[Any, LoginService.AuthError, LoginService.UserProfile] = 
      ZIO.async[Any, LoginService.AuthError, LoginService.UserProfile] { cb => // callback object created by ZIO
        LoginService.login(id, pw)(
          profile => cb(ZIO.succeed(profile)), // here, by calling cb(...) we're notifying ZIO fiber to complete the ZIO with a success
          error => cb(ZIO.fail(error)) // same, with a failure
        )  
      }
      // If we never call cb(...) this ZIO fiber (created with ZIO.async) will be semantically blocked forever!

  val loginProgram = for {
    email <- Console.readLine("Email: ")
    pass <- Console.readLine("Password: ")
    profile <- loginAsZIO(email, pass).debugThread
    _ <- Console.printLine(s"Welcome to RockTheJVM, ${profile.name}")
  } yield ()
  /* Notice that when running previous program, and using login:
    Email: daniel@rockthejvm.com
    Password: RockTheJVM1!

    Attempting login happens in [pool-1-thread-x] from the JVM, and
    UserProfile is returned in a ZScheduler-Worker-X, that is a ZIO worker fiber.
    So we successfully leveraged the resolution of the callback into a ZIO fiber by calling cb(...)
  */

  def run = loginProgram  
}

object ExercisesAsynchEffects extends ZIOAppDefault {

  // 1 - surface a computation running on some (external) thread to a ZIO
  // hint: invoke the cb when the computation is complete
  // hint2: don't wrap the computation into a ZIO
  def external2ZIO[A](computation: () => A)(executor: ExecutorService): Task[A] = ???

  val demoExternal2ZIO = {
    val executor: ExecutorService = Executors.newFixedThreadPool(8)
    val zio = external2ZIO { () =>
      println(s"[${Thread.currentThread().getName()}] computing the meaning of life on some thread")
      Thread.sleep(1000)
      42
    } (executor)

    zio.debugThread.unit // this should come out from a ZIO fiber, and not from the thread pool managed by the executor
  }

  // 2 - lift a Future to a ZIO
  // hint: invoke cb when the future completes (since the Future API is Asynchronous)
  def future2ZIO[A](future: => Future[A])(using ec: ExecutionContext): Task[A] = ???

  val demoFuture2ZIO = {
    val executor = Executors.newFixedThreadPool(8)
    given ec: ExecutionContext = ExecutionContext.fromExecutorService(executor)
    val mol = future2ZIO(Future {
      println(s"[${Thread.currentThread().getName()}] computing the meaning of life on some thread")
      Thread.sleep(1000)
      42
    })

    mol.debugThread.unit
    // we should see 2 thread names: 
    // - one that is actually computing the future 
    // - and one that it's native to ZIO and it's evaluating the number 42
  }

  // 3 - Implement a never ending ZIO
  def neverEndingZIO[A]: UIO[A] = ???
  
  def run = ???
}