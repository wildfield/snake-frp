package snake

import scala.annotation.targetName

val DELTA_T = 25

type Time = Double

type ReactiveStream[Memory, Input, Output] =
  (Input, Option[Memory]) => (Output, Option[Memory])

def liftWithOutput[Memory, Input](
    f: (Input, Option[Memory]) => Memory
): ReactiveStream[Memory, Input, Memory] = {
  def _upgrade(argument: Input, past: Option[Memory]): (Memory, Option[Memory]) = {
    val value = f(argument, past)
    (value, Some(value))
  }
  _upgrade
}
