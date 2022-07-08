package it.filippocavallari

import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory

import java.util.concurrent.atomic.AtomicInteger

case class Coordinate(x: Int, y: Int)

case class Size(width: Int, height: Int)


val seeds = List(2551, 2552) // seed used in the configuration

def startup[X](file: String = "cluster", port: Int)(root: => Behavior[X]): ActorSystem[X] =
    // Override the configuration of the port
    val config = ConfigFactory
      .parseString(s"""akka.remote.artery.canonical.port=$port""")
      .withFallback(ConfigFactory.load(file))

    // Create an Akka system
    ActorSystem(root, "ClusterSystem", config)

def startupWithRole[X](role: String, port: Int)(root: => Behavior[X]): ActorSystem[X] =
    val config = ConfigFactory
      .parseString(s"""
      akka.remote.artery.canonical.port=$port
      akka.cluster.roles = [$role]
      """)
      .withFallback(ConfigFactory.load("cluster"))

    // Create an Akka system
    ActorSystem(root, "ClusterSystem", config)

object PortCounter{
    @volatile var port: AtomicInteger = new AtomicInteger(2551)

    def nextPort(): Int = {
        this.synchronized{
            port.getAndIncrement()
        }
    }
}