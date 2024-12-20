package com.rockthejvm.part5testing

import zio._
import zio.test._

// Regular ZIOSpecDefault are ZIO applications, they are not embedded in JUnit

// First condition for running JUnit applications in ZIO tests is to have:
// The dependency in SBT: "dev.zio" %% "zio-test-junit" % zioVersion
// The test framework: testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

object JUnitCompatibleSpec extends zio.test.junit.JUnitRunnableSpec {

  def spec = suite("Full suite of tests")(
    // pass multiple tests as arguments
    test("simple test") {
      assertTrue(1 + 3 == 4)
    },
    test("a second test") {
      assertZIO(ZIO.succeed("Scala"))(Assertion.hasSizeString(Assertion.equalTo(5)) && Assertion.startsWithString("S"))
    },
    // sub-suites
    suite("a nested suite")(
      test("a nested test") {
        assert(List(1, 2, 3))(Assertion.isNonEmpty && Assertion.hasSameElements(List(3, 1, 2)))
      },
      test("another nested test") {
        assert(List(1, 2, 3).headOption)(Assertion.equalTo(Option(1)))
      }
      // test("a failed nested test") {
      //   assertTrue(1 + 1 == 100)
      // }
    )
  )
}
