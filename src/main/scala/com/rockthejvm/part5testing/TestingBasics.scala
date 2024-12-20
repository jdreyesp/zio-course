package com.rockthejvm.part5testing

import zio._
import zio.test._

case class Person(name: String, age: Int) {
  def spellName: String = name.toUpperCase()
  def saySomething: UIO[String] = ZIO.succeed(s"Hi, I'm $name")
}

object MyTestSpec extends ZIOSpecDefault {

  // fundamental method of any ZIO spec
  override def spec: Spec[TestEnvironment & Scope, Any] =
    // This function `test` needs to return an assertion
    test("First Test") {

      val aPerson = Person("Daniel", 99)

      // an assertion
      // this receives: the method under test, and the assertion that will be needed to succeed
      assert(aPerson.spellName)(Assertion.equalTo("DANIEL"))
      // same
      assertTrue(aPerson.spellName == "DANIEL")
    }
}

object MyFirstEffectTestSpec extends ZIOSpecDefault {
  def spec = test("First Effect test") {
    val person = Person("Daniel", 101)
    assertZIO(effect = person.saySomething)(Assertion.equalTo("Hi, I'm Daniel"))
    // this does not work with assertTrue
    /* Assertion examples:
      - Assertion.equalTo => tests for equality
      - Assertion.assertion => tests any truth value. The most general assertion
        example: assertZIO(effect = person.saySomething)(Assertion.assertion("should be a greeting")(gr => gr == "Hi, I'm Daniel"))
      - Assertion.fails/failsCause = tests that the effect should fail with the EXACT failure/cause you specify
      - Assertion.dies => expects the effect to die with a Throwable, can run an assertion on that Throwable
      - Assertion.isInterrupted => Purpose: This assertion checks whether the effect has been interrupted, regardless of the cause of the interruption.
      - Assetion.isJustInterrupted => Purpose: This assertion checks specifically for an interruption that is the sole reason for the effect's failure.
      - Specialized assertions:
          - isLeft/isRight for Either  (Assertion.isLeft, Assertion.isRight)
          - isSome/isNone for Option
          - isSuccess/isFailure for Try
          - isEmpty/nonEmpty, contains, has* (hasSize, hasAt...) for iterables (Assertion.isEmpty / Assertion.nonEmpty)
          - isEmptyString/nonEmptyString/startsWithString/matchesRegex for Strings
          - isLessThan/isGreaterThan for numbers
     */
  }
}

// you can add multiple tests in a suite
object ASuiteSpec extends ZIOSpecDefault {
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
      },
      test("a failed nested test") {
        assertTrue(1 + 1 == 100)
      }
    )
  )
}
