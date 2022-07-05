package it.filippocavallari

import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import Device.DeviceCommand
import FireStation.FireStationCommand
import Zone.{WrappedDeviceCommand, WrappedFireStationCommand, ZoneCommand}

import akka.actor.Cancellable

import concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.collection.mutable.Map
import scala.language.postfixOps
import concurrent.duration.{Duration, FiniteDuration, MILLISECONDS, SECONDS}

object Zone{
    def apply(coordinate: Coordinate, size: Size): Behavior[ZoneCommand] = {
        Behaviors.setup(ctx => new Zone(ctx, coordinate, size))
    }

    trait Command
    case class RegisterDevice(deviceId: String) extends Command
    case class RegistrationResponse(deviceId: String, success: Boolean)
    case class WrappedDeviceCommand(command: Device.Command) extends Command
    case class WrappedFireStationCommand(command: FireStation.Command) extends Command
    type ZoneCommand = Zone.Command
}


class Zone(context: ActorContext[ZoneCommand], val coordinate: Coordinate, size: Size) extends AbstractBehavior[ZoneCommand](context){

    val deviceAdapter: ActorRef[Device.DeviceCommand] = context.messageAdapter(WrappedDeviceCommand.apply)
    val fireStationAdapter: ActorRef[FireStation.FireStationCommand] = context.messageAdapter(WrappedFireStationCommand.apply)

    val devices: mutable.Map[String, ActorRef[DeviceCommand]] = mutable.Map[String, ActorRef[DeviceCommand]]()
    val alarmedDevices: mutable.Set[String] = scala.collection.mutable.Set[String]()
    val fireStation: ActorRef[FireStationCommand] = context.spawn(FireStation(fireStationAdapter), s"fireStation-${coordinate.x}-${coordinate.y}")

    context.self ! WrappedDeviceCommand(Device.NotifyAlarm("test"))

    var cancellableTimer: Option[Cancellable] = None

    def onMessage(msg: ZoneCommand): Behavior[ZoneCommand] = {
        msg match {
            case Zone.RegisterDevice(deviceId) =>
                println(s"Registering device $deviceId")
                val deviceRef = context.spawn(Device(deviceId, coordinate, deviceAdapter), deviceId)
                devices.put(deviceId, deviceRef)
                this
            case Zone.WrappedDeviceCommand(command) =>
                command match
                    case Device.NotifyAlarm(deviceId) =>
                        println(s"${deviceId} notified alarm")
                        alarmedDevices.add(deviceId)
                        if(alarmedDevices.size >= (devices.size/2)) {
                            println("The majority of devices notified alarm")
                            cancellableTimer = Some(context.system.scheduler.scheduleWithFixedDelay(FiniteDuration(1, SECONDS), FiniteDuration(1, SECONDS))(() =>{
                                println("Sending notification to fire station")
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
                        println("Fire station notified alarm")
                        devices.values.foreach(device => device ! Device.NotifyAlarm("fire station"))
                        return this
                    case FireStation.NotificationReceived() =>
                        println("Fire station notified notification")
                        cancellableTimer.foreach(c => c.cancel())
                        return this
                this
            case _ =>
                println(s"Zone ${coordinate} received unknown message: $msg")
                this
        }
    }

}


