package it.filippocavallari

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import it.filippocavallari.Zone.ZoneCommand

object City {

  val MAX_ZONES = 10

  def apply(size: Size, nDevices: Int): Behavior[String] =
    Behaviors.setup(context => new City(context, size, nDevices))
}

class City(context: ActorContext[String], size: Size,  nDevices: Int) extends AbstractBehavior[String](context){

  def getZones(): Set[ActorRef[ZoneCommand]] = {
    val nZones = City.MAX_ZONES min nDevices
    val nRows = Math.floor(Math.sqrt(nZones)).toInt
    var totalColumns = 0
    val zonesSet = scala.collection.mutable.Set[ActorRef[ZoneCommand]]()
    var totalDevices = 0
    for(i <- 0 until nRows) {
      val nCols = if (i < nRows - 1) {
        Math.floor(Math.sqrt(nZones)).toInt
      } else {
        nZones - totalColumns
      }
      totalColumns += nCols
      for(j <- 0 until nCols) {
        val zoneSize = Size(size.width/nCols, size.height/nRows)
        val zoneCoordinate = Coordinate(size.width/nCols*j, size.height/nRows*i)
        val zone = context.spawn(Zone(zoneCoordinate, zoneSize), s"zone-$i-$j")
        for(k <- 0 until nDevices/nZones) {
          zone ! Zone.RegisterDevice(s"device-$totalDevices") //TODO fix exceeding number of devices
          totalDevices += 1
        }
        zonesSet += zone
        println(s"zone-$i-$j: ${zoneCoordinate.x} ${zoneCoordinate.y} ${zoneSize.width} ${zoneSize.height}")
      }
    }
    collection.immutable.Set(zonesSet.toSeq: _*)
  }

  def onMessage(msg: String): Behavior[String] = {
    getZones()
    this
  }

}

object Main{
  def main(args: Array[String]): Unit = {
    val totalDevices = 10
    val ref = ActorSystem(City(Size(200,100), totalDevices), "smart-city")
    ref ! "test"
  }
}