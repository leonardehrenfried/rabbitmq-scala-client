import amqptest.EmbeddedAMQPBroker
import io.relayr.amqp.Event.ChannelEvent
import io.relayr.amqp._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}

import scala.concurrent.duration._

class TransmissionIntegrationSpec  extends FlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with EmbeddedAMQPBroker with MockFactory {

  override def beforeAll() {
    initializeBroker()
  }

  def connection(eventListener: Event ⇒ Unit) = ConnectionHolder.Builder(amqpUri)
    .eventHooks(EventHooks(eventListener))
    .reconnectionStrategy(ReconnectionStrategy.JavaClientFixedReconnectDelay(1 second))
    .build()

  val serverEventListener = mockFunction[Event, Unit]
  val clientEventListener = mockFunction[Event, Unit]
  
  var serverConnection: ConnectionHolder = null
  var clientConnection: ConnectionHolder = null

  override def beforeEach() = {
    serverEventListener expects * // connection established event
    clientEventListener expects *
    
    serverConnection = connection(serverEventListener)
    clientConnection = connection(clientEventListener)
  }
  
  val testMessage: Message = Message.String("test")

  "" should "send and receive messages" in {
    // create server connection and bind mock handler to queue
    val receiver = mockFunction[Message, Unit]
    val serverCloser = {
      serverEventListener expects ChannelEvent.ChannelOpened(1, None)
      val queue: QueueDeclare = QueueDeclare(Some("test.queue"))
      serverConnection.newChannel().addConsumer(queue, receiver)
    }

    // create client connection and bind to routing key
    clientEventListener expects ChannelEvent.ChannelOpened(1, None)
    val senderChannel: ChannelOwner = clientConnection.newChannel()
    val destinationDescriptor = ExchangePassive("").route("test.queue", DeliveryMode.NotPersistent)

    // define expectations
    receiver expects * onCall { message: Message ⇒
      ()
    }

    // send message
    senderChannel.send(destinationDescriptor, testMessage)
    
    Thread.sleep(1000)
    
    serverCloser.close()
  }
  
  override def afterEach() = {
    // close
    serverConnection.close()
    clientConnection.close()
  }

  override def afterAll() = {
    shutdownBroker()
  }
}
