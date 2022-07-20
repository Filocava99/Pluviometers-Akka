package it.filippocavallari

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import it.filippocavallari.actor.*
import it.filippocavallari.view.GUI
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.{AnyWordSpec, AnyWordSpecLike}

import scala.concurrent.duration.{FiniteDuration, HOURS}

class SmartCityTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

    import actor.Device._
    import actor.City._
    import actor.FireStation._
    import actor.Zone._

    val MAX_ZONES = 10
    val N_DEVICES = 10
    val expectedZones: Int = Math.min(MAX_ZONES, N_DEVICES)
    val initialPort = 2551
    var city : ActorRef[City.Command] = _
    startupWithRole("system", initialPort)(Behaviors.setup(ctx => {
        val portCounter = startupWithRole("portCounter", initialPort+1)(PortCounter(initialPort+4))
        city = startupWithRole("city", initialPort + 2)(City(Size(200,100), 20, portCounter))
        Behaviors.receiveMessage(msg => msg match
            case "stop" => Behaviors.stopped
        )}))
    Thread.sleep(2000)
    val cityProbe: TestProbe[CityCommand] = createTestProbe[CityCommand]()
    city ! GetZones(cityProbe.ref)
    val cityResponse: CityCommand = cityProbe.receiveMessage(FiniteDuration(1, HOURS))
    var zones: Seq[ActorRef[ZoneCommand]] = Seq.empty
    cityResponse match
        case City.GetZonesResponse(receivedZones) =>
            zones = receivedZones.values.toSeq

    val zoneProbe: TestProbe[ZoneCommand] = createTestProbe[ZoneCommand]()
    val firstZone: ActorRef[ZoneCommand] = zones.head
    firstZone ! GetInfo(zoneProbe.ref)
    var devices: Seq[ActorRef[DeviceCommand]] = Seq.empty
    var zoneResponse: ZoneCommand = zoneProbe.receiveMessage(FiniteDuration(1, HOURS))
    zoneResponse match
        case Zone.GetInfoResponse(zoneId, inAlarm, receivedDevices) =>
            devices = receivedDevices

    "A zone" must {
        "must have at least 1 device" in {
            devices.size should be >= 1
        }
        "have the alarm status set to true when" when {
            "the majority of devices triggered the alarm" in {
                devices.foreach(device => device ! Device.TriggerAlarm())
                var alarm = AlarmStatus.ALARM_OFF
                while(alarm == AlarmStatus.ALARM_OFF){ //busy waiting because the messages are asynchronous
                    firstZone ! Zone.GetAlarmStatus(zoneProbe.ref)
                    zoneProbe.receiveMessage(FiniteDuration(1, HOURS)) match
                        case Zone.GetAlarmStatusResponse(alarmStatus) =>
                            alarm = alarmStatus
                        case _ => fail("Unexpected response")
                }
                alarm should be(AlarmStatus.ALARM_ON)
            }
            "the alarm has not been disabled by the fire station even if the devices are not in alarm" in {
                devices.foreach(device => device ! Device.DisableAlarm())
                firstZone ! Zone.GetAlarmStatus(zoneProbe.ref)
                zoneProbe.receiveMessage(FiniteDuration(1, HOURS)) match
                    case Zone.GetAlarmStatusResponse(alarmStatus) =>
                        alarmStatus should be(AlarmStatus.ALARM_ON)
                    case _ => fail("Unexpected response")
            }
        }
        "have the alarm status set to false when" when {
            "the fire station has disabled the alarm" in {
                firstZone ! Zone.GetFireStationAdapter(zoneProbe.ref)
                zoneProbe.receiveMessage() match
                    case Zone.GetFireStationAdapterResponse(fireStationAdapter) =>
                        fireStationAdapter ! FireStation.AlarmHandled()
                        firstZone ! Zone.GetAlarmStatus(zoneProbe.ref)
                        zoneProbe.receiveMessage(FiniteDuration(1, HOURS)) match
                            case Zone.GetAlarmStatusResponse(alarmStatus) =>
                                alarmStatus should be(AlarmStatus.ALARM_OFF)
                            case _ => fail("Unexpected response")
            }
        }
    }
}
