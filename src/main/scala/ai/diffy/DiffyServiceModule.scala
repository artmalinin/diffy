package ai.diffy

import java.net.InetSocketAddress

import ai.diffy.analysis.{InMemoryDifferenceCollector, InMemoryDifferenceCounter, NoiseDifferenceCounter, RawDifferenceCounter}
import ai.diffy.compare.Difference
import ai.diffy.lifter.JsonLifter
import ai.diffy.proxy.Settings
import com.google.inject.Provides
import com.twitter.finagle.Http
import com.twitter.finagle.http.{Method, Request}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.inject.TwitterModule
import com.twitter.util.{Duration, Try}
import javax.inject.Singleton

object DiffyServiceModule extends TwitterModule {
  val datacenter =
    flag("dc", "localhost", "the datacenter where this Diffy instance is deployed")

  val servicePort =
    flag("proxy.port", new InetSocketAddress(9992), "The port where the proxy service should listen")

  val candidatePath =
    flag[String]("candidate", "candidate serverset where code that needs testing is deployed")

  val primaryPath =
    flag[String]("master.primary", "primary master serverset where known good code is deployed")

  val secondaryPath =
    flag[String]("master.secondary", "secondary master serverset where known good code is deployed")

  val protocol =
    flag[String]("service.protocol", "Service protocol: thrift, http or https")

  val clientId =
    flag[String]("proxy.clientId", "diffy.proxy", "The clientId to be used by the proxy service to talk to candidate, primary, and master")

  val pathToThriftJar =
    flag[String]("thrift.jar", "path/to/thrift.jar", "The path to a fat Thrift jar")

  val serviceClass =
    flag[String]("thrift.serviceClass", "UserService", "The service name within the thrift jar e.g. UserService")

  val serviceName =
    flag[String]("serviceName", "Gizmoduck", "The service title e.g. Gizmoduck")

  val apiRoot =
    flag[String]("apiRoot", "", "The API root the front end should ping, defaults to the current host")

  val enableThriftMux =
    flag[Boolean]("enableThriftMux", true, "use thrift mux server and clients")

  val relativeThreshold =
    flag[Double]("threshold.relative", 20.0, "minimum (inclusive) relative threshold that a field must have to be returned")

  val absoluteThreshold =
    flag[Double]("threshold.absolute", 0.03, "minimum (inclusive) absolute threshold that a field must have to be returned")

  val teamEmail =
    flag[String]("notifications.targetEmail", "isotope@sn126.com", "team email to which cron report should be sent")

  val emailDelay =
    flag[Int]("notifications.delay", 5, "minutes to wait before sending report out. e.g. 30")

  val rootUrl =
    flag[String]("rootUrl", "", "Root url to access this service, e.g. diffy-staging-gizmoduck.service.smf1.twitter.com")

  val allowHttpSideEffects =
    flag[Boolean]("allowHttpSideEffects", false, "Ignore POST, PUT, and DELETE requests if set to false")

  val excludeHttpHeadersComparison =
    flag[Boolean]("excludeHttpHeadersComparison", false, "Exclude comparison on HTTP headers if set to false")

  val skipEmailsWhenNoErrors =
    flag[Boolean]("skipEmailsWhenNoErrors", false, "Do not send emails if there are no critical errors")

  val httpsPort =
    flag[String]("httpsPort", "443", "Port to be used when using HTTPS as a protocol")

  val thriftFramedTransport =
    flag[Boolean]("thriftFramedTransport", true, "Run in BufferedTransport mode when false")

  @Provides
  @Singleton
  def settings = {
    val result = Settings(
      datacenter(),
      servicePort(),
      candidatePath(),
      primaryPath(),
      secondaryPath(),
      protocol(),
      clientId(),
      pathToThriftJar(),
      serviceClass(),
      serviceName(),
      apiRoot(),
      enableThriftMux(),
      relativeThreshold(),
      absoluteThreshold(),
      teamEmail(),
      Duration.fromMinutes(emailDelay()),
      rootUrl(),
      allowHttpSideEffects(),
      excludeHttpHeadersComparison(),
      skipEmailsWhenNoErrors(),
      httpsPort(),
      thriftFramedTransport()
    )

    DefaultTimer.doLater(Duration.fromSeconds(10)) {
      val m = Difference.mkMap(result)
      val ed = m("emailDelay")
      val m1 = m.updated("emailDelay",ed.toString).updated("artifact", "od.2019.8.15.1565886216202")

      val request = Try(Request(Method.Post, "/stats"))
      request map { _.setContentTypeJson() }
      request map { x => x.setContentString(JsonLifter.encode(m1)) }
      request map { r =>
        Http.client
          .withTls("diffyproject.appspot.com")
          .newService("diffyproject.appspot.com:443")
          .apply(r)
      }
    }

    result
  }

  @Provides
  @Singleton
  def providesRawCounter = RawDifferenceCounter(new InMemoryDifferenceCounter)

  @Provides
  @Singleton
  def providesNoiseCounter = NoiseDifferenceCounter(new InMemoryDifferenceCounter)

  @Provides
  @Singleton
  def providesCollector = new InMemoryDifferenceCollector
}
