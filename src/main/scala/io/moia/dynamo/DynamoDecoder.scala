package io.moia.dynamo

import java.time.Instant

import cats.data.{EitherNec, NonEmptyChain}
import cats.instances.either._
import cats.instances.vector._
import cats.kernel.Eq
import cats.syntax.either._
import cats.syntax.traverse._
import io.moia.dynamo.DynamoType._
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

abstract class DynamoDecodeError                                                             extends Product with Serializable
case class MissingField(fieldName: String, attributeValue: AttributeValue)                   extends DynamoDecodeError
case class MissingFieldInMap(fieldName: String, hmap: java.util.Map[String, AttributeValue]) extends DynamoDecodeError
case class TypeMismatch(expected: DynamoType, attributeValue: AttributeValue)                extends DynamoDecodeError
case class InvalidCase(hmap: java.util.Map[String, AttributeValue])                          extends DynamoDecodeError
case class ParseError(message: String, cause: Option[Throwable])                             extends DynamoDecodeError
case class InvalidTypeTag(attributeValue: AttributeValue)                                    extends DynamoDecodeError

object DynamoDecodeError {
  implicit val dynamoDecodeErrorEq: Eq[DynamoDecodeError] = Eq.fromUniversalEquals[DynamoDecodeError]
}

trait DynamoDecoder[A] { self =>
  def decode(attributeValue: AttributeValue): EitherNec[DynamoDecodeError, A]

  def map[B](f: A => B): DynamoDecoder[B] = value => self.decode(value).map(f)
}

object DynamoDecoder extends DynamoDecoderInstances with DynamoDecoderFunctions {
  def apply[A](implicit instance: DynamoDecoder[A]): DynamoDecoder[A] = instance
}

trait DynamoDecoderInstances extends DynamoDecoderFunctions {
  import io.moia.dynamo.attributevalue.dsl._

  private[this] def convert[A, B](s: A)(convertor: A => B): EitherNec[DynamoDecodeError, B] =
    try {
      Right(convertor(s))
    } catch {
      case NonFatal(e) => Either.leftNec(ParseError(s"Could not convert: ${e.getMessage}", Some(e)))
    }

  implicit def stringDecoder: DynamoDecoder[String] = attributeValue => accessOrTypeMismatch(attributeValue, DynamoString)(_.sOpt)

  implicit def numericDecoder[A](implicit N: Numeric[A]): DynamoDecoder[A] =
    attributeValue =>
      for {
        nString <- accessOrTypeMismatch(attributeValue, DynamoNumber)(_.nOpt)
        n       <- convert(nString)(s => N.parseString(s).get)
      } yield n

  implicit def booleanDecoder: DynamoDecoder[Boolean] =
    attributeValue => accessOrTypeMismatch(attributeValue, DynamoBool)(_.boolOpt)

  implicit def instantDecoder: DynamoDecoder[Instant] =
    attributeValue =>
      for {
        string <- accessOrTypeMismatch(attributeValue, DynamoString)(_.sOpt)
        result <- convert(string)(Instant.parse)
      } yield result

  implicit def seqDecoder[A: DynamoDecoder]: DynamoDecoder[Seq[A]] =
    attributeValue =>
      for {
        list   <- accessOrTypeMismatch(attributeValue, DynamoList)(_.lOpt)
        result <- list.iterator.asScala.toVector.traverse(DynamoDecoder[A].decode)
      } yield result
}

trait DynamoDecoderFunctions {
  private[dynamo] def accessOrTypeMismatch[A](attributeValue: AttributeValue, typ: DynamoType)(
      access: AttributeValue => Option[A]
  ): Either[NonEmptyChain[TypeMismatch], A] =
    access(attributeValue) match {
      case None        => Either.leftNec(TypeMismatch(typ, attributeValue))
      case Some(value) => Right(value)
    }
}
