package scynamo.generic

import scynamo.{ObjectScynamoCodec, ObjectScynamoEncoder, ScynamoDecoder}
import shapeless.Lazy

package object semiauto extends Semiauto

trait Semiauto {
  def deriveScynamoEncoder[A](implicit genericEncoder: Lazy[GenericScynamoEncoder[A]]): ObjectScynamoEncoder[A] = genericEncoder.value

  def derivescynamoDecoder[A](implicit genericDecoder: Lazy[GenericScynamoDecoder[A]]): ScynamoDecoder[A] = genericDecoder.value

  def deriveDynamoCodec[A](
      implicit
      genericEncoder: GenericScynamoEncoder[A],
      genericDecoder: GenericScynamoDecoder[A]
  ): ObjectScynamoCodec[A] = ObjectScynamoCodec.fromEncoderAndDecoder(genericEncoder, genericDecoder)
}