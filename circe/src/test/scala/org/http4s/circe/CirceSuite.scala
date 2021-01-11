/*
 * Copyright 2015 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package circe.test // Get out of circe package so we can import custom instances

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.laws.util.TestContext
import cats.syntax.all._
import fs2.Stream
import io.circe._
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import org.http4s.Status.Ok
import org.http4s.circe._
import org.http4s.syntax.all._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSuite

// Originally based on ArgonautSuite
class CirceSuite extends JawnDecodeSupportSuite[Json] {
  implicit val testContext = TestContext()

  val CirceInstancesWithCustomErrors = CirceInstances.builder
    .withEmptyBodyMessage(MalformedMessageBodyFailure("Custom Invalid JSON: empty body"))
    .withJawnParseExceptionMessage(_ => MalformedMessageBodyFailure("Custom Invalid JSON jawn"))
    .withCirceParseExceptionMessage(_ => MalformedMessageBodyFailure("Custom Invalid JSON circe"))
    .withJsonDecodeError { (json, failures) =>
      val failureStr = failures.mkString_("", ", ", "")
      InvalidMessageBodyFailure(
        s"Custom Could not decode JSON: ${json.noSpaces}, errors: $failureStr")
    }
    .build

  testJsonDecoder(jsonDecoder)
  testJsonDecoderError(CirceInstancesWithCustomErrors.jsonDecoderIncremental)(
    emptyBody = { case MalformedMessageBodyFailure("Custom Invalid JSON: empty body", _) => true },
    parseError = { case MalformedMessageBodyFailure("Custom Invalid JSON jawn", _) => true }
  )
  testJsonDecoderError(CirceInstancesWithCustomErrors.jsonDecoderByteBuffer)(
    emptyBody = { case MalformedMessageBodyFailure("Custom Invalid JSON: empty body", _) => true },
    parseError = { case MalformedMessageBodyFailure("Custom Invalid JSON circe", _) => true }
  )

  sealed case class Foo(bar: Int)
  val foo = Foo(42)
  // Beware of possible conflicting shapeless versions if using the circe-generic module
  // to derive these.
  implicit val FooDecoder: Decoder[Foo] =
    Decoder.forProduct1("bar")(Foo.apply)
  implicit val FooEncoder: Encoder[Foo] =
    Encoder.forProduct1("bar")(foo => foo.bar)

  sealed case class Bar(a: Int, b: String)
  implicit val barDecoder: Decoder[Bar] =
    Decoder.forProduct2("a", "b")(Bar.apply)
  implicit val barEncoder: Encoder[Bar] =
    Encoder.forProduct2("a", "b")(bar => (bar.a, bar.b))

  val json = Json.obj("test" -> Json.fromString("CirceSupport"))

  test("json encoder should have json content type") {
    assertEquals(
      jsonEncoder[IO].headers.get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.json)))
  }

  test("json encoder should write compact JSON") {
    writeToString(json).assertEquals("""{"test":"CirceSupport"}""")
  }

  test("json encoder should write JSON according to custom encoders") {
    val custom = CirceInstances.withPrinter(Printer.spaces2).build
    import custom._
    writeToString(json).assertEquals("""{
          |  "test" : "CirceSupport"
          |}""".stripMargin)
  }

  test("json encoder should write JSON according to explicit printer") {
    writeToString(json)(jsonEncoderWithPrinter(Printer.spaces2)).assertEquals("""{
          |  "test" : "CirceSupport"
          |}""".stripMargin)
  }

  test("jsonEncoderOf should have json content type") {
    assertEquals(
      jsonEncoderOf[IO, Foo].headers.get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.json)))
  }

  test("jsonEncoderOf should write compact JSON") {
    writeToString(foo)(jsonEncoderOf[IO, Foo]).assertEquals("""{"bar":42}""")
  }

  test("jsonEncoderOf should write JSON according to custom encoders") {
    val custom = CirceInstances.withPrinter(Printer.spaces2).build
    import custom._
    writeToString(foo)(jsonEncoderOf).assertEquals("""{
          |  "bar" : 42
          |}""".stripMargin)
  }

  test("jsonEncoder should write JSON according to explicit printer") {
    writeToString(foo)(jsonEncoderWithPrinterOf(Printer.spaces2)).assertEquals("""{
          |  "bar" : 42
          |}""".stripMargin)
  }

  val jsons = Stream(
    Json.obj("test1" -> Json.fromString("CirceSupport")),
    Json.obj("test2" -> Json.fromString("CirceSupport"))
  ).lift[IO]

  test("stream json array encoder should have json content type") {
    assertEquals(
      streamJsonArrayEncoder[IO].headers
        .get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.json)))
  }

  test("stream json array encoder should write compact JSON") {
    writeToString(jsons).assertEquals("""[{"test1":"CirceSupport"},{"test2":"CirceSupport"}]""")
  }

  test("stream json array encoder should write JSON according to custom encoders") {
    val custom = CirceInstances.withPrinter(Printer.spaces2).build
    import custom._
    writeToString(jsons).assertEquals("""[{
          |  "test1" : "CirceSupport"
          |},{
          |  "test2" : "CirceSupport"
          |}]""".stripMargin)
  }

  test("stream json array encoder should write JSON according to explicit printer") {
    writeToString(jsons)(streamJsonArrayEncoderWithPrinter(Printer.spaces2)).assertEquals("""[{
          |  "test1" : "CirceSupport"
          |},{
          |  "test2" : "CirceSupport"
          |}]""".stripMargin)
  }

  test("stream json array encoder should write a valid JSON array for an empty stream") {
    writeToString[Stream[IO, Json]](Stream.empty).assertEquals("[]")
  }

  val foos = Stream(
    Foo(42),
    Foo(350)
  ).lift[IO]

  test("stream json array encoder of should have json content type") {
    assertEquals(
      streamJsonArrayEncoderOf[IO, Foo].headers
        .get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.json)))
  }

  test("stream json array encoder of should write compact JSON") {
    writeToString(foos)(streamJsonArrayEncoderOf[IO, Foo]).assertEquals(
      """[{"bar":42},{"bar":350}]""")
  }

  test("stream json array encoder of should write JSON according to custom encoders") {
    val custom = CirceInstances.withPrinter(Printer.spaces2).build
    import custom._
    writeToString(foos)(streamJsonArrayEncoderOf).assertEquals("""[{
          |  "bar" : 42
          |},{
          |  "bar" : 350
          |}]""".stripMargin)
  }

  test("stream json array encoder of should write JSON according to explicit printer") {
    writeToString(foos)(streamJsonArrayEncoderWithPrinterOf(Printer.spaces2)).assertEquals("""[{
          |  "bar" : 42
          |},{
          |  "bar" : 350
          |}]""".stripMargin)
  }

  test("stream json array encoder of should write a valid JSON array for an empty stream") {
    writeToString[Stream[IO, Foo]](Stream.empty)(streamJsonArrayEncoderOf).assertEquals("[]")
  }

  test("json handle the optionality of asNumber") {
    // From ArgonautSuite, which tests similar things:
    // TODO Urgh.  We need to make testing these smoother.
    // https://github.com/http4s/http4s/issues/157
    def getBody(body: EntityBody[IO]): Array[Byte] = body.compile.toVector.unsafeRunSync().toArray
    val req = Request[IO]().withEntity(Json.fromDoubleOrNull(157))
    val body = req
      .decode { (json: Json) =>
        Response[IO](Ok)
          .withEntity(json.asNumber.flatMap(_.toLong).getOrElse(0L).toString)
          .pure[IO]
      }
      .map(_.body)
    body.map(b => new String(getBody(b), StandardCharsets.UTF_8)).assertEquals("157")
  }

  test("jsonOf should decode JSON from a Circe decoder") {
    val result = jsonOf[IO, Foo]
      .decode(Request[IO]().withEntity(Json.obj("bar" -> Json.fromDoubleOrNull(42))), strict = true)
    result.value.assertEquals(Right(Foo(42)))
  }

  // https://github.com/http4s/http4s/issues/514
  sealed case class Umlaut(wort: String)
  implicit val umlautDecoder: Decoder[Umlaut] = Decoder.forProduct1("wort")(Umlaut.apply)
  test("handle JSON with umlauts") {
    List("ärgerlich", """"ärgerlich"""").traverse { wort =>
      val json = Json.obj("wort" -> Json.fromString(wort))
      val result =
        jsonOf[IO, Umlaut].decode(Request[IO]().withEntity(json), strict = true)
      result.value.assertEquals(Right(Umlaut(wort)))
    }
  }

  test("jsonOf should fail with custom message from a decoder") {
    val result = CirceInstancesWithCustomErrors
      .jsonOf[IO, Bar]
      .decode(Request[IO]().withEntity(Json.obj("bar1" -> Json.fromInt(42))), strict = true)
    result.value.assertEquals(Left(InvalidMessageBodyFailure(
      "Custom Could not decode JSON: {\"bar1\":42}, errors: DecodingFailure at .a: Attempt to decode value on failed cursor")))
  }

  test("accumulatingJsonOf should decode JSON from a Circe decoder") {
    val result = accumulatingJsonOf[IO, Foo]
      .decode(Request[IO]().withEntity(Json.obj("bar" -> Json.fromDoubleOrNull(42))), strict = true)
    result.value.assertEquals(Right(Foo(42)))
  }

  test(
    "accumulatingJsonOf should return an InvalidMessageBodyFailure with a list of failures on invalid JSON messages") {
    val json = Json.obj("a" -> Json.fromString("sup"), "b" -> Json.fromInt(42))
    val result = accumulatingJsonOf[IO, Bar]
      .decode(Request[IO]().withEntity(json), strict = true)
    result.value
      .map {
        case Left(InvalidMessageBodyFailure(_, Some(DecodingFailures(NonEmptyList(_, _))))) => true
        case _ => false
      }
      .assertEquals(true)
  }

  test("stream json array encoder of should fail with custom message from a decoder") {
    val result = CirceInstancesWithCustomErrors
      .accumulatingJsonOf[IO, Bar]
      .decode(Request[IO]().withEntity(Json.obj("bar1" -> Json.fromInt(42))), strict = true)
    result.value.assertEquals(Left(InvalidMessageBodyFailure(
      "Custom Could not decode JSON: {\"bar1\":42}, errors: DecodingFailure at .a: Attempt to decode value on failed cursor, DecodingFailure at .b: Attempt to decode value on failed cursor")))
  }

  test("Uri codec round trip") {
    // TODO would benefit from Arbitrary[Uri]
    val uri = uri"http://www.example.com/"
    assertEquals(uri.asJson.as[Uri], Right(uri))
  }

  test("Message[F].decodeJson[A] should decode json from a message") {
    val req = Request[IO]().withEntity(foo.asJson)
    req.decodeJson[Foo].assertEquals(foo)
  }

  test("Message[F].decodeJson[A] should fail on invalid json") {
    val req = Request[IO]().withEntity(List(13, 14).asJson)
    req.decodeJson[Foo].attempt.map(_.isLeft).assertEquals(true)
  }

  test("CirceEntityEncDec should decode json without defining EntityDecoder") {
    import org.http4s.circe.CirceEntityDecoder._
    val request = Request[IO]().withEntity(Json.obj("bar" -> Json.fromDoubleOrNull(42)))
    val result = request.attemptAs[Foo]
    result.value.assertEquals(Right(Foo(42)))
  }

  test("CirceEntityEncDec should encode without defining EntityEncoder using default printer") {
    import org.http4s.circe.CirceEntityEncoder._
    writeToString(foo).assertEquals("""{"bar":42}""")
  }

  test("CirceInstances.builder should handle JSON parsing errors") {
    val req = Request[IO]()
      .withEntity("broken json")
      .withContentType(`Content-Type`(MediaType.application.json))

    val decoder = CirceInstances.builder.build.jsonOf[IO, Int]
    val result = decoder.decode(req, true).value

    result
      .map {
        case Left(_: MalformedMessageBodyFailure) => true
        case _ => false
      }
      .assertEquals(true)
  }

  test("CirceInstances.builder should handle JSON decoding errors") {
    val req = Request[IO]()
      .withEntity(Json.obj())

    val decoder = CirceInstances.builder.build.jsonOf[IO, Int]
    val result = decoder.decode(req, true).value

    result
      .map {
        case Left(_: InvalidMessageBodyFailure) => true
        case _ => false
      }
      .assertEquals(true)
  }

  // checkAll("EntityCodec[IO, Json]", EntityCodecTests[IO, Json].entityCodec)
}
