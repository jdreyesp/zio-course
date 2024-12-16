package com.rockthejvm.part2effects

import zio._
import java.util.concurrent.TimeUnit

object ZIODependencies extends ZIOAppDefault {

  // Real world scenario could be a user subscription:
  import ZIODependenciesModel.*

  // Regular Dependency Injection
  val subscriptionService = ZIO.succeed(
    new UserSubscription(
      new EmailService(),
      new UserDatabase(new ConnectionPool(10))
    )
  )

  /* Drawbacks:
    - does not scale for many services
    - DI can be 100x worse
        - If we want to pass dependencies partially, we can't with this setup
        - Not having the deps in the same place
        - we could pass dependencies multiple times (we could leak resources and other side effects...)
   */

  def subscribe(user: User): ZIO[Any, Throwable, Unit] = for {
    sub <- subscriptionService // service is instantiated at the point of call
    _ <- sub.subscribeUser(user)
  } yield ()

  // Example of leaking resources
  // Here we're creating the service twice, and we don't know what side effects that could cause. In case of database creation
  // for instance, we can exhaust resources, etc. So it's an anti pattern to do this.
  val program = for {
    _ <- subscribe(User("Diego", "diego@example.com"))
    _ <- subscribe(User("Maria", "maria@example.com"))
  } yield ()

  // Alternative
  // See the return type. By wrapping the UserSubscription into a Service, we now require that whoever uses subscribe_v2
  // will require to inject a UserSubscription
  def subscribe_v2(user: User): ZIO[UserSubscription, Throwable, Unit] = for {
    // This returns a URIO[UserSubscription, UserSubscription] == ZIO[UserSubscription, Nothing, UserSubscription]
    sub <- ZIO.service[UserSubscription]
    _ <- sub.subscribeUser(user)
  } yield ()

  val program_v2: ZIO[UserSubscription, Throwable, Unit] = for {
    _ <- subscribe_v2(User("Diego", "diego@example.com"))
    _ <- subscribe_v2(User("Maria", "maria@example.com"))
  } yield ()

  /* Advantages of this approach:
    - We don't need to care about dependencies until we're configuring the application
    - All ZIOs requiring this dependency will use the same instance
    - We can use different instances of the same type for different needs (e.g. testing
    - Layers can be created and composed much like regular ZIO + rich API
   */

//   override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
//     program_v2.provideLayer(
//       ZLayer.succeed(
//         new UserSubscription(
//           new EmailService(),
//           new UserDatabase(new ConnectionPool(10))
//         )
//       )
//     )

  // Using ZLayers, the program can be rewritten as:
  val connectionPoolLayer: ZLayer[Any, Nothing, ConnectionPool] =
    ZLayer.succeed(new ConnectionPool(10))

  // This takes the function arguments and pass the corresponding needed layer
  // UserDatabase.create receieves a ConnectionPool, that it's passed through:
  val databaseLayer: ZLayer[ConnectionPool, Nothing, UserDatabase] =
    ZLayer.fromFunction(UserDatabase.create _)

  val emailService: ZLayer[Any, Nothing, EmailService] =
    ZLayer.succeed(EmailService.create())

  // fun fact: The way ZIO inspects for layers by using the fromFunction and inspects for arguments of the function is by
  // using macros.
  val userSubscriptionServiceLayer
      : ZLayer[UserDatabase & EmailService, Nothing, UserSubscription] =
    ZLayer.fromFunction(UserSubscription.create _)

  // Composing layers
  // >>> will compose the layers by saying that the right layer is on top of the second layer. Example:
  // This is called vertical composition (>>>)
  val databaseLayerFull: ZLayer[Any, Nothing, UserDatabase] =
    connectionPoolLayer >>> databaseLayer

  // horizontal composition (++): combines the dependencies of both layers AND the values of both layers (it also combines the error channel with the lowest ancestor of both)
  // The return type will be in this example ZLayer[Any, Nothing, UserDatabase & EmailService] (or in Scala2 notation
  // UserDatabase with EmailService)
  val subscriptionRequirementsLayer
      : ZLayer[Any, Nothing, UserDatabase & EmailService] =
    databaseLayerFull ++ emailService

  val userSubscriptionLayer: ZLayer[Any, Nothing, UserSubscription] =
    subscriptionRequirementsLayer >>> userSubscriptionServiceLayer

  // best practice: define factory methods for the layers on each of the companion objects of the services.
  // Then we replace the factory methods in the creation of the layers above, so that the code is much cleaner

  val runnableProgram = program_v2.provideLayer(userSubscriptionLayer)

  // macro magic that will traverse the dependency graph finding the layers and injecting them to the corresponding
  // ones
  // This can't be run in scala3 if the classes are in the same file (known bug)
  val runnableProgram_v2 = program_v2.provide(
    UserSubscription.live,
    EmailService.live,
    UserDatabase.live,
    ConnectionPool.live(10),
    // ZIO will tell you if you miss a layer
    // and if you have multiple layers of the same type
    // and tell you the dependency graph!
    // ZLayer.Debug.tree
    ZLayer.Debug.mermaid
  )

  // magic v2
  val userSubscriptionLayer_v2: ZLayer[Any, Nothing, UserSubscription] =
    ZLayer.make[UserSubscription](
      UserSubscription.live,
      EmailService.live,
      UserDatabase.live,
      ConnectionPool.live(10)
    )

  // Other utility methods
  val passthrough
      : ZLayer[ConnectionPool, Nothing, ConnectionPool & UserDatabase] =
    UserDatabase.live.passthrough

  // service pattern => Push the layer forward to wherever the layer is used
  val dbService = ZLayer.service[UserDatabase]

  // launch = creates a ZIO that uses the services and never finishes (specially useful for an application that runs
  // a web server or infinite loop on purpose)
  val subscriptionLaunch
      : ZIO[EmailService & UserDatabase, Nothing, UserSubscription] =
    UserSubscription.live.launch

  // memoization = active by default (when we define a ZLayer, that's reused along the code), unless we declare a ZLayer
  // like a 'fresh' copy (e.g. EmailService.live.fresh)

  /*
    Already provided Services: Clock, Random, System, Console
   */
  val getTime = Clock.currentTime(TimeUnit.SECONDS)
  val randomValues = Random.nextInt
  val sysVariable = System.env("HADOOP_HOME")
  val printlnEffect = Console.print("This is ZIO")

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    runnableProgram_v2

}
