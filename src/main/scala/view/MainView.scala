package it.filippocavallari
package view

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import it.filippocavallari.actor.{City, FireStation, Zone}
import it.filippocavallari.actor.City.CityCommand
import it.filippocavallari.actor.Zone.ZoneCommand
import it.filippocavallari.view.MainView.ZoneCommandWrapper
import it.filippocavallari.actor.Zone.*
import it.filippocavallari.actor.Device.DeviceCommand
import it.filippocavallari.actor.FireStation.FireStationCommand

import java.awt.event.ActionListener
import java.awt.{GridLayout, LayoutManager}
import javax.swing.{BorderFactory, BoxLayout, JButton, JFrame, JLabel, JPanel, WindowConstants}
import scala.swing.*
import scala.swing.event.ActionEvent

object MainView{
    def apply(city: ActorRef[CityCommand]): Behavior[CityCommand] =
        Behaviors.setup(context => new MainView(context, city))

    case class ZoneCommandWrapper(cmd: ZoneCommand) extends CityCommand
}

class MainView(context: ActorContext[CityCommand], val smartCity: ActorRef[CityCommand]) extends AbstractBehavior[CityCommand](context) {

    val view = new View()

    val zoneAdapter: ActorRef[ZoneCommand] = context.messageAdapter(ZoneCommandWrapper.apply)

    val zoneDevicesMap: Map[String, Seq[ActorRef[DeviceCommand]]] = Map[String, Seq[ActorRef[DeviceCommand]]]()

    var zones: Map[String, ActorRef[ZoneCommand]] = Map[String, ActorRef[ZoneCommand]]()

    var zoneViews: Map[String, ZoneView] = Map[String, ZoneView]()

    override def onMessage(msg: CityCommand): Behavior[CityCommand] =
        msg match
            case City.GetZonesResponse(receivedZones) => {
                zones = receivedZones
                zones.foreach((_, v) => {
                    v ! GetInfo(zoneAdapter)
                })
            }
            case ZoneCommandWrapper(cmd) => cmd match
                case GetInfoResponse(zoneId, inAlarm, devices) => {
                    val zoneView = new ZoneView(zoneId, inAlarm, devices.size)
                    zoneViews += (zoneId -> zoneView)
                    view.updateContent()
                }
                case GetFireStationAdapterResponse(fireStationAdapter) => {
                    fireStationAdapter ! FireStation.AlarmHandled()
                }
        this

    def disableZoneAlarm(zoneId: String): Unit = {
        zones(zoneId) ! GetFireStationAdapter(zoneAdapter)
    }

    sealed class View() extends JFrame {

        this.setTitle("Smart City")
        val refreshButton = new JButton("Refresh")
        refreshButton.addActionListener(e => {
            smartCity ! City.GetZones(context.self)
        })
        //this.setSize(500,300)
        val mainPanel = new JPanel()
        val boxLayout = new BoxLayout(mainPanel, BoxLayout.X_AXIS)
        mainPanel.setLayout(boxLayout)
        mainPanel.add(refreshButton)
        this.getContentPane.add(mainPanel)
        def updateContent(): Unit ={
            mainPanel.removeAll()
            mainPanel.add(refreshButton)
            zoneViews.foreach((_, v) => {
                mainPanel.add(v)
            })
            this.pack()
            this.setVisible(true)
        }
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
        this.pack()
        this.setVisible(true)
    }

    sealed class ZoneView(val zoneId: String, val inAlarm: Zone.AlarmStatus, val devices: Int) extends JPanel(new GridLayout(5,1)) {
        this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(java.awt.Color.BLACK), BorderFactory.createEmptyBorder(10,10,10,10)))
        this.add(new JLabel(s"Zone: $zoneId"))
        this.add(new JLabel(s"Devices: $devices"))
        this.add(new JLabel(s"In alarm: $inAlarm"))
        val button = new JButton("Disable alarm")
        val fireStationState = new JLabel(s"FireStation: ${if(inAlarm == Zone.AlarmStatus.ALARM_UNDER_MANAGEMENT) "Busy" else "Free"}")
        this.add(fireStationState)
        button.addActionListener(e => {
            zones(zoneId) ! GetFireStationAdapter(zoneAdapter)
        })
        this.add(button)
    }
}

@main def main(): Unit ={

   // val system = ActorSystem[CityCommand](City(Size(200,100), 10), "city")
    val system : ActorSystem[Unit] = ActorSystem(Behaviors.setup(ctx => {
        val city = ctx.spawn(City(Size(200,100), 20), "city")
        val mainFrame = ctx.spawn(MainView(city), "mainView")
        Behaviors.receiveMessage(msg => msg match
            case "stop" => Behaviors.stopped
        )
    }), "smart-city")
}