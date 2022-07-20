package it.filippocavallari.actor

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import City.{CityCommand, GetZones, GetZonesResponse, PortCommandWrapper, Start}
import Zone.ZoneCommand
import it.filippocavallari.actor.PortCounter.PortCommand
import it.filippocavallari.{Coordinate, Size, startupWithRole}
import it.filippocavallari.actor.{City, Zone}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem

import scala.collection.immutable.HashMap
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object City {

    val MAX_ZONES = 10

    def apply(size: Size, nDevices: Int, portCounter: ActorRef[PortCommand]): Behavior[CityCommand] =
        Behaviors.setup(context => new City(context, size, nDevices, portCounter))

    trait Command
    case class GetZones(ref: ActorRef[CityCommand]) extends Command
    case class GetZonesResponse(map: Map[String, ActorRef[ZoneCommand]]) extends Command
    case class Start() extends Command
    case class PortCommandWrapper(command: PortCommand) extends Command
    type CityCommand = City.Command
}

class City(context: ActorContext[CityCommand], size: Size, nDevices: Int, portCounter: ActorRef[PortCommand]) extends AbstractBehavior[CityCommand](context) {

    var zonesMap: mutable.Map[String, ActorRef[ZoneCommand]] = mutable.HashMap[String, ActorRef[ZoneCommand]]()
    var portCounterAdapter: ActorRef[PortCommand] = context.messageAdapter(PortCommandWrapper.apply)

    def init(): Unit = {
        val nZones = City.MAX_ZONES min nDevices
        val nRows = Math.floor(Math.sqrt(nZones)).toInt
        var totalColumns = 0
        var totalDevices = 0
        for (i <- 0 until nRows) {
            val nCols = if (i < nRows - 1) {
                Math.floor(Math.sqrt(nZones)).toInt
            } else {
                nZones - totalColumns
            }
            totalColumns += nCols
            for (j <- 0 until nCols) {
                val zoneId: String = s"zone-$i-$j"
                val zoneSize = Size(size.width / nCols, size.height / nRows)
                val zoneCoordinate = Coordinate(size.width / nCols * j, size.height / nRows * i)
                implicit val timeout: akka.util.Timeout = Duration.create(10, "seconds")
                implicit val system: ActorSystem[Nothing] = context.system
                val requestedPorts: Int = 1 + nDevices / nZones
                val future = portCounter ? (replyTo => PortCounter.GetPorts(requestedPorts, replyTo))
                val result = Await.result(future, Duration.create(10, "seconds"))
                var zone = None: Option[ActorRef[ZoneCommand]]
                var ports = List[Int]()
                result match {
                    case PortCounter.GetPortsResponse(receivedPorts) =>
                        zone = Some(startupWithRole("zone", receivedPorts.head)(Zone(zoneId, zoneCoordinate, zoneSize, receivedPorts(1))))
                        ports = receivedPorts.slice(2, receivedPorts.length)
                }
                for (k <- ports.indices) {
                    zone.get ! Zone.RegisterDevice(s"device-$totalDevices", ports(k)) //TODO fix exceeding number of devices
                    totalDevices += 1
                }
                zonesMap = zonesMap.+((zoneId, zone.get))
                context.log.info("zone-{}-{}: {} {} {} {}", i, j, zoneCoordinate.x, zoneCoordinate.y, zoneSize.width, zoneSize.height)
            }
        }
    }

    init()

    def onMessage(msg: CityCommand): Behavior[CityCommand] = {
        msg match
            case GetZones(ref) => ref ! GetZonesResponse(zonesMap.map(kv => (kv._1,kv._2)).toMap)
        this
    }

}