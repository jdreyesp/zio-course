package com.rockthejvm.part1recap

import scala.concurrent.ExecutionContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Essentials extends App {

  // side effect - actions that do not evaluate to any meaningful value and yet it does something (e.g. showing something on the screen) like:
  val myVal = println("Hello, Scala!")

  // instruction - Unit (void) return
  // expression - Code that are evaluated to a value, like:
  val anIfExpression = if (2 > 3) "bigger" else "smaller"

  // class/trait vs object difference
  trait Animal
  trait Carnivore {
    def eat(animal: Animal): Unit
  }

  object Carnivore
  // So definitions in Carnivore trait or class are attached to the instance of the element they represent. However, definitions in Carnivore companion
  // object are attached to the type itself.

  // Method notation

  // Every single 1 param method can be infixed, like:
  val three = 1 + 2 // infixed version

  // same as:
  val otherThree = 1.+(2) // non infixed version

  // functional programming: Using functions the same way we use the rest of elements (like primitiv etyprs, etc.)

  // Futures
  import scala.concurrent.Future
  given executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

  val future = Future {
    println("Hello!")
  }
}
