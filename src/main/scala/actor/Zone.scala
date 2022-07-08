package it.filippocavallari.actor

import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import Device.DeviceCommand
import FireStation.FireStationCommand
import Zone.{WrappedDeviceCommand, WrappedFireStationCommand, ZoneCommand}
import akka.actor.Cancellable
import it.filippocavallari.{Coordinate, PortCounter, Size, startupWithRole}

import concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.collection.mutable.Map
import scala.language.postfixOps
import concurrent.duration.{Duration, FiniteDuration, MILLISECONDS, SECONDS}

object Zone{
    def apply(zoneId: String, coordinate: Coordinate, size: Size): Behavior[ZoneCommand] = {
        Behaviors.setup(ctx => new Zone(ctx, zoneId, coordinate, size))
    }

    trait Command
    case class RegisterDevice(deviceId: String) extends Command
    case class RegistrationResponse(deviceId: String, success: Boolean)
    case class GetInfo(ref: ActorRef[ZoneCommand]) extends Command
    case class GetInfoResponse(zoneId: String, inAlarm: AlarmStatus, devices: Seq[ActorRef[DeviceCommand]]) extends Command
    case class GetAlarmStatus(ref: ActorRef[ZoneCommand]) extends Command
    case class GetAlarmStatusResponse(alarmStatus: AlarmStatus) extends Command
    case class WrappedDeviceCommand(command: Device.Command) extends Command
    case class WrappedFireStationCommand(command: FireStation.Command) extends Command
    case class GetFireStationAdapter(ref: ActorRef[ZoneCommand]) extends Command
    case class GetFireStationAdapterResponse(ref: ActorRef[FireStationCommand]) extends Command
    type ZoneCommand = Zone.Command

    enum AlarmStatus {
        case ALARM_OFF
        case ALARM_ON
        case ALARM_UNDER_MANAGEMENT
    }
}

class Zone(context: ActorContext[ZoneCommand], val zoneId: String, val coordinate: Coordinate, size: Size) extends AbstractBehavior[ZoneCommand](context){

    val deviceAdapter: ActorRef[Device.DeviceCommand] = context.messageAdapter(WrappedDeviceCommand.apply)
    val fireStationAdapter: ActorRef[FireStation.FireStationCommand] = context.messageAdapter(WrappedFireStationCommand.apply)

    val devices: mutable.Map[String, ActorRef[DeviceCommand]] = mutable.Map[String, ActorRef[DeviceCommand]]()
    val alarmedDevices: mutable.Set[String] = scala.collection.mutable.Set[String]()
    val fireStation: ActorRef[FireStationCommand] = startupWithRole(s"fireStation-${coordinate.x}-${coordinate.y}",PortCounter.nextPort())(FireStation(fireStationAdapter))
    var inAlarm: Zone.AlarmStatus = Zone.AlarmStatus.ALARM_OFF

    var cancellableTimer: Option[Cancellable] = None
    def onMessage(msg: ZoneCommand): Behavior[ZoneCommand] = {
        msg match {
            case Zone.RegisterDevice(deviceId) =>
                context.log.info(s"Registering device $deviceId")
                val deviceRef = startupWithRole(deviceId, PortCounter.nextPort())(Device(deviceId, coordinate, deviceAdapter))
                devices.put(deviceId, deviceRef)
                this
            case Zone.GetInfo(ref) =>
                ref ! Zone.GetInfoResponse(zoneId, inAlarm, devices.values.toSeq)
                this
            case Zone.GetAlarmStatus(ref) =>
                ref ! Zone.GetAlarmStatusResponse(inAlarm)
                this
            case Zone.GetFireStationAdapter(ref) =>
                ref ! Zone.GetFireStationAdapterResponse(fireStationAdapter)
                this
            case Zone.WrappedDeviceCommand(command) =>
                command match
                    case Device.NotifyAlarm(deviceId) =>
                        context.log.info(s"${deviceId} notified alarm")
                        alarmedDevices.add(deviceId)
                        if(alarmedDevices.size >= (devices.size/2)) {
                            context.log.info("The majority of devices notified alarm")
                            inAlarm = Zone.AlarmStatus.ALARM_ON
                            cancellableTimer = Some(context.system.scheduler.scheduleWithFixedDelay(FiniteDuration(1, SECONDS), FiniteDuration(1, SECONDS))(() =>{
                                context.log.info("Sending notification to fire station")
                                fireStation ! FireStation.NotifyAlarm()
                            })(scala.concurrent.ExecutionContext.global))
                        }
                        return this
                    case Device.NotifyRestart(deviceId, ref) =>
                        devices.put(deviceId, ref)
                        return this
                    case Device.NotifyStop(deviceId) =>
                        alarmedDevices.remove(deviceId)
                        devices.remove(deviceId)
                        return this
                this
            case Zone.WrappedFireStationCommand(command) =>
                command match
                    case FireStation.AlarmHandled() =>
                        inAlarm = Zone.AlarmStatus.ALARM_OFF
                        context.log.info("Fire station notified alarm")
                        devices.values.foreach(device => device ! Device.DisableAlarm())
                        return this
                    case FireStation.NotificationReceived() =>
                        inAlarm = Zone.AlarmStatus.ALARM_UNDER_MANAGEMENT
                        context.log.info("Fire station notified notification")
                        cancellableTimer.foreach(c => c.cancel())
                        return this
                this
            case _ =>
                context.log.info(s"Zone ${coordinate} received unknown message: $msg")
                this
        }
    }

}


