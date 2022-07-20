package it.filippocavallari.view

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import it.filippocavallari.actor.{City, FireStation, PortCounter, Zone}
import it.filippocavallari.actor.City.CityCommand
import it.filippocavallari.actor.Zone.ZoneCommand
import it.filippocavallari.view.GUI.ZoneCommandWrapper
import it.filippocavallari.actor.Zone.*
import it.filippocavallari.actor.Device.DeviceCommand
import it.filippocavallari.actor.FireStation.FireStationCommand
import it.filippocavallari.{Size, startupWithRole}

import java.awt.event.ActionListener
import java.awt.{GridLayout, LayoutManager}
import javax.swing.{BorderFactory, BoxLayout, JButton, JFrame, JLabel, JPanel, WindowConstants}
import scala.language.postfixOps
import scala.swing.*
import scala.swing.event.ActionEvent

object GUI{
    def apply(city: ActorRef[CityCommand]): Behavior[CityCommand] =
        Behaviors.setup(context => new GUI(context, city))

    case class ZoneCommandWrapper(cmd: ZoneCommand) extends CityCommand
}

class GUI(context: ActorContext[CityCommand], val smartCity: ActorRef[CityCommand]) extends AbstractBehavior[CityCommand](context) {

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
    var initialPort = 2551
    startupWithRole("system", initialPort)(Behaviors.setup(ctx => {
        val portCounter = startupWithRole("portCounter", initialPort+1)(PortCounter(initialPort+4))
        val city = startupWithRole("city", initialPort + 2)(City(Size(200,100), 20, portCounter))
        startupWithRole("gui",initialPort + 3)(GUI(city))
        Behaviors.receiveMessage(msg => msg match
            case "stop" => Behaviors.stopped
        )}))
}