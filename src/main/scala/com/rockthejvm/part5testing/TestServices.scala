package com.rockthejvm.part5testing

import zio._
import zio.test._

object SimpleDependencySpec extends ZIOSpecDefault {

  def spec = test("simple dependency") {
    val aZIO: ZIO[Int, Nothing, Int] = ZIO.succeed(42)
    assertZIO(aZIO.provide(ZLayer.succeed(10)))(Assertion.equalTo(42))
  } // you could also provide the ZLayer at the end of the test.
}

// Let's say we are creating a user centered application, where we provide user information and we store it in a DB
object BusinessLogicSpec extends ZIOSpecDefault {

  // "dependency"
  abstract class Database[K, V] {
    def get(key: K): Task[V]
    def put(key: K, value: V): Task[Unit]
  }

  object Database {
    def create(url: String): UIO[Database[String, String]] = ??? // the REAL thing
  }

  // logic under test (business logic)
  def normalizeUsername(name: String): UIO[String] = ZIO.succeed(name.toUpperCase())

  val mockedDatabase = ZIO.succeed(new Database[String, String] {
    import scala.collection.mutable
    val map = mutable.Map[String, String]()

    override def get(key: String): Task[String] = ZIO.attempt(map(key))
    override def put(key: String, value: String): Task[Unit] = ZIO.succeed(map += (key -> value))
  })

  def spec = suite("A user survey application should...")(
    test("normalize user name") {
      val surveyPreliminaryLogic = for {
        db <- ZIO.service[Database[String, String]]
        _ <- db.put("123", "Daniel")
        username <- db.get("123")
        normalized <- normalizeUsername(username)
      } yield normalized

      assertZIO(surveyPreliminaryLogic)(Assertion.equalTo("DANIEL"))
    }.provide(ZLayer.fromZIO(mockedDatabase))
  ) // you could also provide the ZLayer here so that it is injected to the whole suite of tests

}

/*
    built-in test services
    - console
    - random
    - clock
    - system
 */
object DummyConsoleApplication {
  def welcomeUser(): Task[Unit] = for {
    _ <- Console.printLine("Please enter your name")
    name <- Console.readLine("")
    _ <- Console.printLine(s"Welcome, $name!")
  } yield ()
}

object BuiltInTestServicesSpec extends ZIOSpecDefault {
  def spec = suite("Checking built-in test services")(
    test("ZIO console application") {
      val logicUnderTest: Task[Vector[String]] = for {
        _ <- TestConsole.feedLines("Daniel")
        _ <- DummyConsoleApplication.welcomeUser()
        output <- TestConsole.output
      } yield output.map(_.trim)

      assertZIO(logicUnderTest)(Assertion.hasSameElements(List("Please enter your name", "", "Welcome, Daniel!")))
    },
    test("ZIO clock") {
      val parallelEffect = for {
        fiber <- ZIO.sleep(5.minutes).timeout(3.minutes).fork
        _ <- TestClock.adjust(3.minutes) // adjust the time as if 3 minutes would have passed
        result <- fiber.join
      } yield result

      assertZIO(parallelEffect)(Assertion.isNone)
    },
    test("ZIO Random") {
      val effect = for {
        _ <- TestRandom.feedInts(3, 4, 1, 2)
        value <- Random.nextInt
      } yield value

      assertZIO(effect)(Assertion.equalTo(3))
    }
  )
}

/* Test aspects - decorations to tests that will give us some properties
 */
object AspectsSpec extends ZIOSpecDefault {

  import zio.test.TestAspect._

  def computeMeaningOfLife: UIO[Int] =
    ZIO.sleep(2.seconds) *> ZIO.succeed(42)

  def spec = suite("Testing aspects")(
    test("timeout aspect") {
      val effect = for {
        molFib <- computeMeaningOfLife.fork
        _ <- TestClock.adjust(3.seconds)
        v <- molFib.join
      } yield v

      assertZIO(effect)(Assertion.equalTo(42))
    } @@ timeout(10.millis) @@ timed // if the whole thing does not complete in 10 seconds, this will fail.
  )
  /*
    Aspects:
      - timeout(duration)
      - eventually - retries until successful
      - nonFlaky(n) - repeats n times, stops at first failure
      - repeats(n) - same
      - retries(n) - retries n times, stops at first success
      - debug - prints everything in the console
      - silent - prints nothing
      - diagnose(duration) - tells you why the test failed
      - parallel/sequential (aspects of a SUITE) - default: sequential
      - ignore - ignore test
      - success (in a SUITE) - it will fail all ignored tests
      - timed - measure execution time
      - before/beforeAll + after/afterAll (they will receive a ZIO that will be executed before the test)
   */
}
