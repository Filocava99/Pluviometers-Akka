package it.filippocavallari

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import City.{CityCommand, GetZones, GetZonesResponse, Start}
import Zone.ZoneCommand

import scala.collection.mutable

object City {

    val MAX_ZONES = 10

    def apply(size: Size, nDevices: Int): Behavior[CityCommand] =
        Behaviors.setup(context => new City(context, size, nDevices))

    trait Command
    case class GetZones(ref: ActorRef[CityCommand]) extends Command
    case class GetZonesResponse(seq: Seq[ActorRef[ZoneCommand]]) extends Command
    case class Start() extends Command
    type CityCommand = City.Command
}

class City(context: ActorContext[CityCommand], size: Size, nDevices: Int) extends AbstractBehavior[CityCommand](context) {

    val zonesSet: mutable.Set[ActorRef[ZoneCommand]] = mutable.Set[ActorRef[ZoneCommand]]()

    def init(): Set[ActorRef[ZoneCommand]] = {
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
                val zoneSize = Size(size.width / nCols, size.height / nRows)
                val zoneCoordinate = Coordinate(size.width / nCols * j, size.height / nRows * i)
                val zone = context.spawn(Zone(zoneCoordinate, zoneSize), s"zone-$i-$j")
                for (k <- 0 until nDevices / nZones) {
                    zone ! Zone.RegisterDevice(s"device-$totalDevices") //TODO fix exceeding number of devices
                    totalDevices += 1
                }
                zonesSet += zone
                context.log.info("zone-{}-{}: {} {} {} {}", i, j, zoneCoordinate.x, zoneCoordinate.y, zoneSize.width, zoneSize.height)
            }
        }
        collection.immutable.Set(zonesSet.toSeq: _*)
    }

    init()

    def onMessage(msg: CityCommand): Behavior[CityCommand] = {
        msg match
            case GetZones(ref) => ref ! GetZonesResponse(zonesSet.toSeq)
        this
    }

}