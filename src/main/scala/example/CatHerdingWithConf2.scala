package example

import java.util.UUID


import exceptions._
import io.circe.{Decoder, Json}
import model.Microchip._
import model._

import scala.util.Try

object CatHerdingWithConf2 {

  import Syntax2._
  // Ties together all of the other methods

  def getCatWithConf(dBConf: DBConf, microchipJson: Json): Result[Cat] =
    for {
       microchip <-  parseMicrochip(microchipJson).liftResult
       uuid <- stringToUUID(microchip.id)
       cat <- getCatById(uuid).run(dBConf)
    } yield cat


  def parseMicrochip(json: Json): Decoder.Result[Microchip] =
    json.as[Microchip]

  // Requires failure handling
  def stringToUUID(s: String): Either[Throwable, UUID] = {
    val triedUuid = Try(UUID.fromString(s))
    triedUuid.fold[Either[Throwable, UUID]](t => Left(t), id => Right(id))
  }

  // Requires failure handling AND configuration
  def getCatById(id: UUID): ConfiguredResult[DBConf, Cat] =
    ConfiguredResult[DBConf, Cat] {
      case CrazyCatLadyDb => Cat.allCats.find(_.id == id).liftResult
      case _ => Left(UnknownDbException)
    }
}

object Syntax2 {

  import cats.data.Kleisli

  type Result[A] = Either[Throwable, A]
  // the first type needs to be a type constructor
  type ConfiguredResult[DBConf, A] = Kleisli[Result, DBConf, A]

  object ConfiguredResult {
    def apply[DBConf, A](run: DBConf => Result[A]): ConfiguredResult[DBConf, A] =
      Kleisli[Result, DBConf, A](run)
  }

  implicit class ResultOps[A](dr: Decoder.Result[A]) {
    // converts a DecodeResult to a pure disjunction, then maps across the either
    // for both success and failure values
    def liftResult: Result[A] = dr.fold(fail => Left(CirceDecodeException), success => Right(success))

    def liftConfiguredResult: ConfiguredResult[DBConf, A] =
      Kleisli[Result, DBConf, A] { (u: DBConf) => dr.liftResult }
  }

  implicit class OptionOps[A](option: Option[A]) {
    // folds over the option, converting this in to a Result[A] with a failure if none
    def liftResult: Result[A] = option.fold[Result[A]](Left(NoValueException))(value => Right(value))
  }

  implicit class ResultWithDBConfOps[A](result: Result[A]) {
    def liftConfiguredResult: ConfiguredResult[DBConf, A] =
      Kleisli[Result, DBConf, A] { (conf: DBConf) => result }
  }


}