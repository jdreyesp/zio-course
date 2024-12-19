package com.rockthejvm.part3concurrency

import zio._
import utils.given
import com.rockthejvm.utils._

object Schedules extends ZIOAppDefault {
  
  val aZIO = Random.nextBoolean.flatMap { flag =>
    if (flag) ZIO.succeed("Fetched value!").debugThread
    else ZIO.succeed("Failure...").debugThread *> ZIO.fail("error")
  }

  val aRetriedZIO = aZIO.retry(Schedule.recurs(10)) // tries 10 times, return the first success, last failure

  // schedules are data structures that describe HOW effects should be timed
  val oneTimeSchedule = Schedule.once
  val recurrentSchedule = Schedule.recurs(10)
  val fixedIntervalSchedule = Schedule.spaced(1.second) // retries every second until a success

  // exponential backoff
  val exponentialBackoffSchedule = Schedule.exponential(1.second, 2.0) // 1 , 2 , 4 , 8, ...
  val fiboSchedule = Schedule.fibonacci(1.seconds) // 1s, 2s, 3s, 5s, 8s, 13s, ...

  // combinators
  val recurrentAndSpaced = Schedule.recurs(3) && Schedule.spaced(1.second) // every attempt is 1s apart, 3 attempts total

  // sequencing scheduling
  val recurrentThenSpaced = Schedule.recurs(3) ++ Schedule.spaced(1.second) // THis retries 3 times AND THEN spaced every 1 second

  // Schedules have type arguments 
  // [R] = environment, 
  // [I] = input (the errors in the case of retry), 
  // [O] = output (values for the next schedule so that you can do something with it)
  // example: this schedule will print the time elapsed of the retries
  val totalElapsed = Schedule.spaced(1.second) >>> Schedule.elapsed.map(time => println(s"Total time elapsed: $time"))
  
  def run = aZIO.retry(totalElapsed)
}
