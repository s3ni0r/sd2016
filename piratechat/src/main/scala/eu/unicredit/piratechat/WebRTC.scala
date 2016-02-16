package eu.unicredit.piratechat

import org.scalajs.dom

import scala.scalajs.js
import js.Dynamic.{global => g}
import js.Dynamic.literal
import js.DynamicImplicits._

import akka.actor._

object WebRTCMsgs {

  class Command
  case object Create extends Command

  case class Join(token: String) extends Command

  case class CreateAnsware(token: String)
  case class JoinAnsware(token: String)
  case class ChannelEstabilished(channel: js.Dynamic)

  class Status
  case object Connected extends Status
  case object Disconnected extends Status

  case class ICECandidate(connStr: String)

  class ChannelMessage
  class MessageFromBus(val txt: String) extends ChannelMessage
  trait MessageToBus extends ChannelMessage {
    val txt: String
  }

}

class WebRTCConnection extends Actor with StunServers {
  import WebRTCMsgs._

  val uuid = java.util.UUID.randomUUID.toString

  val cfg = literal(
      iceServers = stuns.map(s => literal(urls = s"stun:$s"))
    )

  val channelOptions = literal(
      offerToReceiveAudio = false,
      offerToReceiveVideo = false
    )

  val connection: js.Dynamic = 
    js.Dynamic.newInstance(g.RTCPeerConnection)(cfg)

  connection.onicecandidate = (e: js.Dynamic) => {
    if (e.candidate == null)
      self ! ICECandidate(js.JSON.stringify(connection.localDescription))   
  }
  
  def receive = {
    case Create =>
      context.become(connecting)
    case Join(str) =>
      context.become(join(str))
  }

  def connecting: Receive = {

    val channel = 
      connection.createDataChannel(uuid, channelOptions)

    bindChannel(channel)
  
    connection.createOffer(
      (desc: js.Dynamic) => {
        connection.setLocalDescription(desc, () => {}, () => {})
      },
      (err: js.Dynamic) => println(s"Couldn't create offer $err"),
      channelOptions
    )
    
    ;{
      case ICECandidate(txt) =>
        context.parent ! CreateAnsware(txt)
        context.become(waitingForClient(txt, channel))
    }
  }

  def waitingForClient(token: String, channel: js.Dynamic): Receive = {
    case Join(peerToken) =>
      val answerDesc =
        js.Dynamic.newInstance(g.RTCSessionDescription)(js.JSON.parse(peerToken))
      connection.setRemoteDescription(answerDesc)
      context.become(connected(channel))
  } 

  def connected(channel: js.Dynamic): Receive = {
    context.parent ! Connected

    ;{
      case msg: MessageToBus =>
        println("sending on bus "+msg.txt)
        channel.send(msg.txt)
      case msg: MessageFromBus =>
        println("receiving from bus "+msg.txt)
        context.parent ! msg
    }
  }

  def bindChannel(channel: js.Dynamic) = {
    channel.onopen = (e: js.Dynamic) => ()

    channel.onclose = (e: js.Dynamic) => {
      context.parent ! Disconnected
      self ! PoisonPill
    }

    channel.onerror = (e: js.Dynamic) => {
      context.parent ! Disconnected
      self ! PoisonPill
    }

    channel.onmessage = (e: js.Dynamic) => {
      self ! new MessageFromBus(e.data.toString)
    }
  }

  def join(str: String): Receive = {

    val offerDesc =
      js.Dynamic.newInstance(g.RTCSessionDescription)(js.JSON.parse(str))
 
    connection.ondatachannel = (e: js.Dynamic) => {
      bindChannel(e.channel)

      self ! ChannelEstabilished(e.channel)
    }

    connection.setRemoteDescription(offerDesc)

    connection.createAnswer(
      (answerDesc: js.Dynamic) => {
        connection.setLocalDescription(answerDesc)
      },
      (err: js.Dynamic) => println(s"Couldn't create answer $err"),
      channelOptions
    )

    ;{
      case ICECandidate(txt) =>
        context.parent ! JoinAnsware(txt)
      case ChannelEstabilished(channel) =>
        context.become(connected(channel))
    }
  }

}

trait StunServers {

  val stuns: js.Array[String] =
  //debug mode
  js.Array(
    "localhost"
    )
  /*
  //real world
  js.Array(
    "stun.l.google.com:19302",
    "stun1.l.google.com:19302",
    "stun2.l.google.com:19302",
    "stun3.l.google.com:19302",
    "stun4.l.google.com:19302",
    "stun01.sipphone.com",
    "stun.ekiga.net",
    "stun.fwdnet.netv",
    "stun.ideasip.com",
    "stun.iptel.org",
    "stun.rixtelecom.se",
    "stun.schlund.de",
    "stunserver.org",
    "stun.softjoys.com",
    "stun.voiparound.com",
    "stun.voipbuster.com",
    "stun.voipstunt.com",
    "stun.voxgratia.org",
    "stun.xten.com"
    )
  */
}