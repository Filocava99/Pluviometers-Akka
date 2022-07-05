package it.filippocavallari

import FireStation.{FireStationCommand, NotifyAlarm, AlarmHandled}

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Signal, SupervisorStrategy}

object FireStation {
    def apply(zone: ActorRef[FireStationCommand]): Behavior[FireStationCommand] = {
        Behaviors.supervise(Behaviors.setup(context => new FireStation(context, zone))).onFailure(SupervisorStrategy.restart)
    }

    trait Command
    case class NotifyAlarm() extends Command
    case class AlarmHandled() extends Command
    case class NotificationReceived() extends Command
    type FireStationCommand = FireStation.Command
}

class FireStation(context: ActorContext[FireStationCommand], zone: ActorRef[FireStationCommand]) extends AbstractBehavior[FireStationCommand](context) {

    private var notified: Boolean = false

    override def onMessage(msg: FireStationCommand): Behavior[FireStationCommand] = {
        msg match {
            case NotifyAlarm() =>
                //Send again because response might have got lost
                notified = true
                zone ! FireStation.NotificationReceived()
                this
            case AlarmHandled() =>
                notified = false
                zone ! FireStation.AlarmHandled()
                this
        }
    }
}
