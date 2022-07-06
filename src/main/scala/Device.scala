package it.filippocavallari

import Device.DeviceCommand
import Zone.ZoneCommand

import akka.actor.PreRestartException
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, PreRestart, Signal, SupervisorStrategy}

object Device{
    def apply(deviceId: String, coordinate: Coordinate, zone: ActorRef[Command]): Behavior[DeviceCommand] = {
        Behaviors.supervise(Behaviors.setup(context => new Device(context, deviceId, coordinate, zone))).onFailure(SupervisorStrategy.restart)
    }

    trait Command
    case class TriggerAlarm() extends Command
    case class DisableAlarm() extends Command
    case class NotifyAlarm(deviceId: String) extends Command
    case class NotifyRestart(deviceId: String, ref: ActorRef[DeviceCommand]) extends Command
    case class NotifyStop(deviceId: String) extends Command
    type DeviceCommand = Device.Command
}


class Device(context: ActorContext[DeviceCommand], val deviceId: String, val coordinate: Coordinate, val zone:  ActorRef[Device.Command]) extends AbstractBehavior[DeviceCommand](context) {

    var alarmEnabled = false
    
    override def onMessage(msg: DeviceCommand): Behavior[DeviceCommand] = {
        msg match {
            case Device.TriggerAlarm() =>
                alarmEnabled = true
                context.log.info(s"Device $deviceId triggered alarm")
                zone ! Device.NotifyAlarm(deviceId)
                return this
            case Device.DisableAlarm() =>
                alarmEnabled = false
                context.log.info(s"Device $deviceId disabled alarm")
                return this
        }
        this
    }

    override def onSignal: PartialFunction[Signal, Behavior[DeviceCommand]] = {
        signal => {
            signal match
                case PreRestart =>
                    context.log.info(s"Device $deviceId restarted")
                    zone ! Device.NotifyRestart(deviceId, context.self)
                    this
                case PostStop =>
                    context.log.info(s"Device $deviceId stopped")
                    zone ! Device.NotifyStop(deviceId)
                    this
                case _ => super.onSignal(signal)
        }
    }
}
