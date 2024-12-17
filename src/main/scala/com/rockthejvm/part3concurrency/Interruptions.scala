package com.rockthejvm.part3concurrency

import zio._

object Interruptions extends ZIOAppDefault {

  def run = ZIO.succeed(println("hello2"))
}
