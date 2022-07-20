package it.filippocavallari.actor

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import it.filippocavallari.actor.PortCounter.{GetPorts, GetPortsResponse, PortCommand}

import java.util.concurrent.atomic.AtomicInteger


object PortCounter{
    def apply(startingPort: Int): Behavior[PortCounter.Command] = {
        Behaviors.setup(ctx => new PortCounter(ctx, startingPort))
    }

    trait Command
    case class GetPorts(amount: Int, replyTo: ActorRef[Command]) extends Command
    case class GetPortsResponse(ports: List[Int]) extends Command
    type PortCommand = Command
}

class PortCounter(context: ActorContext[PortCommand], startingPort: Int) extends AbstractBehavior[PortCommand](context) {
  private var portCounter = new AtomicInteger(startingPort)

  override def onMessage(msg: PortCommand): Behavior[PortCommand] = {
      msg match{
          case GetPorts(amount, replyTo) =>
              val ports = List.fill(amount)(portCounter.getAndIncrement())
              println(ports)
              replyTo ! GetPortsResponse(ports)
              this
      }
  }
}
