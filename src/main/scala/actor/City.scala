package it.filippocavallari
package actor

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import City.{CityCommand, GetZones, GetZonesResponse, Start}
import Zone.ZoneCommand
import it.filippocavallari.actor.{City, Zone}

import scala.collection.immutable.HashMap
import scala.collection.mutable

object City {

    val MAX_ZONES = 10

    def apply(size: Size, nDevices: Int): Behavior[CityCommand] =
        Behaviors.setup(context => new City(context, size, nDevices))

    trait Command
    case class GetZones(ref: ActorRef[CityCommand]) extends Command
    case class GetZonesResponse(map: Map[String, ActorRef[ZoneCommand]]) extends Command
    case class Start() extends Command
    type CityCommand = City.Command
}

class City(context: ActorContext[CityCommand], size: Size, nDevices: Int) extends AbstractBehavior[CityCommand](context) {

    var zonesMap: mutable.Map[String, ActorRef[ZoneCommand]] = mutable.HashMap[String, ActorRef[ZoneCommand]]()

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
                val zone = context.spawn(Zone(zoneId, zoneCoordinate, zoneSize), s"zone-$i-$j")
                for (k <- 0 until nDevices / nZones) {
                    zone ! Zone.RegisterDevice(s"device-$totalDevices") //TODO fix exceeding number of devices
                    totalDevices += 1
                }
                zonesMap = zonesMap.+((zoneId, zone))
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