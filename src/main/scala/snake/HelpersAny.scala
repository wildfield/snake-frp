package snake

import scala.annotation.targetName

type MemoryTupleAny = (Option[Time], Option[Any])
type ReactiveStreamAny[Input, Output] =
  (Time, Input, MemoryTupleAny) => (Output, Option[Any])
type SourceAny[Output] =
  (Time, MemoryTupleAny) => (Output, Any)

def toAny[T1, T2, T3](f: ReactiveStream[T1, T2, T3]): ReactiveStreamAny[T2, T3] = {
  def _toAny(time: Time, argument: T2, pastAny: MemoryTupleAny): (T3, Option[Any]) = {
    val (pastTime, pastValueAny) = pastAny
    val pastValue = pastValueAny.flatMap(_.asInstanceOf[Option[T1]])
    val output = f(time, argument, (pastTime, pastValue))
    (output._1, Some(output._2))
  }
  _toAny
}

def map[T1, T2, T3](
    f: ReactiveStreamAny[T1, T2],
    mapFunc: T2 => T3
): ReactiveStreamAny[T1, T3] = {
  def _map(time: Double, argument: T1, past: MemoryTupleAny): (T3, Option[Any]) = {
    val value = f(time, argument, past)
    (mapFunc(value._1), value._2)
  }
  _map
}

def flatMap[T1, T2, T3, T4](
    f: ReactiveStreamAny[T1, T2],
    map: T2 => ReactiveStreamAny[T3, T4]
): ReactiveStreamAny[(T1, T3), T4] = {
  def _flatMap(
      time: Double,
      argument: (T1, T3),
      past: MemoryTupleAny
  ): (T4, Option[Any]) = {
    val (pastTime, pastValueAny) = past
    val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
      pastValueAny.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val fOutput = f(time, argument._1, (pastTime, pastValueF))
    val mappedFOutput = map(fOutput._1)(time, argument._2, (pastTime, pastValueMapped))
    (mappedFOutput._1, Some((fOutput._2, mappedFOutput._2)))
  }
  _flatMap
}

def flatMapSource[T1, T2, T3](
    f: ReactiveStreamAny[T1, T2],
    map: T2 => SourceAny[T3]
): ReactiveStreamAny[T1, T3] = {
  def _flatMap(
      time: Double,
      argument: T1,
      past: MemoryTupleAny
  ): (T3, Option[Any]) = {
    val (pastTime, pastValueAny) = past
    val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
      pastValueAny.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val fOutput = f(time, argument, (pastTime, pastValueF))
    val mappedFOutput = map(fOutput._1)(time, (pastTime, pastValueMapped))
    (mappedFOutput._1, Some(fOutput._2, mappedFOutput._2))
  }
  _flatMap
}

def flatten[T1, T2, T3](
    f: ReactiveStreamAny[T1, ReactiveStreamAny[T2, T3]]
): ReactiveStreamAny[(T1, T2), T3] = {
  def _flatten(
      time: Double,
      argument: (T1, T2),
      past: MemoryTupleAny
  ): (T3, Option[Any]) = {
    val (pastTime, pastValueAny) = past
    val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
      pastValueAny.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val fOutput = f(time, argument._1, (pastTime, pastValueF))
    val mappedFOutput = fOutput._1(time, argument._2, (pastTime, pastValueMapped))
    (mappedFOutput._1, Some((fOutput._2, mappedFOutput._2)))
  }
  _flatten
}

@targetName("flatten2")
def flatten[T1, T2, T3, T4](
    f: ReactiveStreamAny[T1, (T4, ReactiveStreamAny[(T4, T2), T3])]
): ReactiveStreamAny[(T1, T2), T3] = {
  def _flatten(
      time: Double,
      argument: (T1, T2),
      past: MemoryTupleAny
  ): (T3, Option[Any]) = {
    val (pastTime, pastValueAny) = past
    val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
      pastValueAny.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val fOutput = f(time, argument._1, (pastTime, pastValueF))
    val mappedFOutput =
      fOutput._1._2(time, (fOutput._1._1, argument._2), (pastTime, pastValueMapped))
    (mappedFOutput._1, Some((fOutput._2, mappedFOutput._2)))
  }
  _flatten
}

def branch[T1, T2](
    condition: Boolean,
    f1: ReactiveStreamAny[(Boolean, T1), T2],
    f2: ReactiveStreamAny[(Boolean, T1), T2]
): ReactiveStreamAny[T1, T2] = {
  def _branch(
      time: Double,
      argument: T1,
      past: MemoryTupleAny
  ): (T2, Option[Any]) = {
    val (pastTime, pastValueAny) = past
    val (pastConditionOption: Option[Boolean], pastValueF1: Option[Any], pastValueF2: Option[Any]) =
      pastValueAny.map(_.asInstanceOf[(Boolean, Option[Any], Option[Any])]) match {
        case None                           => (None, None)
        case Some((value1, value2, value3)) => (Some(value1), value2, value3)
      }
    if (condition) {
      val output = f1(
        time,
        (pastConditionOption.map(_ != condition).getOrElse(true), argument),
        (pastTime, pastValueF1)
      )
      (
        output._1,
        Some((condition, output._2, pastValueF2))
      )
    } else {
      val output = f2(
        time,
        (pastConditionOption.map(_ != condition).getOrElse(true), argument),
        (pastTime, pastValueF2)
      )
      (
        output._1,
        Some((condition, pastValueF1, output._2))
      )
    }
  }
  _branch
}

def clearMem[T1, T2](
    f: ReactiveStreamAny[T1, T2]
): ReactiveStreamAny[(Boolean, T1), T2] = {
  def _clearMem(
      time: Double,
      argument: (Boolean, T1),
      past: MemoryTupleAny
  ): (T2, Option[Any]) = {
    val (pastTime, pastValueAny) = past
    if (argument._1) {
      f(time, argument._2, (pastTime, None))
    } else {
      f(time, argument._2, (pastTime, pastValueAny))
    }
  }
  _clearMem
}

def ignoreInput[T1, T2, T3](
    f: ReactiveStreamAny[T1, T2]
): ReactiveStreamAny[(T3, T1), T2] = {
  def _ignoreInput(
      time: Double,
      argument: (T3, T1),
      past: MemoryTupleAny
  ): (T2, Option[Any]) = {
    f(time, argument._2, past)
  }
  _ignoreInput
}

def cached[T1 <: Equals, T2](
    f: ReactiveStreamAny[T1, T2]
): ReactiveStreamAny[T1, T2] = {
  def _cached(
      time: Double,
      argument: T1,
      past: MemoryTupleAny
  ): (T2, Option[Any]) = {
    val (pastTime, pastValueAny) = past
    val (pastInput: Option[T1], pastOutput: Option[T2], pastFValue: Option[Any]) =
      pastValueAny.map(_.asInstanceOf[(T1, T2, Option[Any])]) match {
        case None                         => (None, None)
        case Some(value1, value2, value3) => (Some(value1), Some(value2), value3)
      }
    (pastInput, pastOutput) match {
      case (Some(pastInput), Some(pastOutput)) => {
        val isInputSame = pastInput.equals(argument)
        if (isInputSame) {
          (pastOutput, Some((argument, pastOutput, pastFValue)))
        } else {
          val output = f(time, argument, (pastTime, pastFValue))
          (output._1, Some((argument, output._1, output._2)))
        }
      }
      case _ =>
        val output = f(time, argument, (pastTime, pastFValue))
        (output._1, Some((argument, output._1, output._2)))
    }
  }
  _cached
}

def ifInputChanged[T1 <: Equals, T2](
    f: ReactiveStreamAny[T1, T2]
): ReactiveStreamAny[T1, Option[T2]] = {
  def _ifInputChanged(
      time: Double,
      argument: T1,
      past: MemoryTupleAny
  ): (Option[T2], Option[Any]) = {
    val (pastTime, pastValueAny) = past
    val (pastInput: Option[T1], pastFValue: Option[Any]) =
      pastValueAny.map(_.asInstanceOf[(T1, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value3) => (Some(value1), value3)
      }
    pastInput match {
      case Some(pastInput) => {
        val isInputSame = pastInput.equals(argument)
        if (isInputSame) {
          (None, Some((argument, pastFValue)))
        } else {
          val output = f(time, argument, (pastTime, pastFValue))
          (Some(output._1), Some((argument, output._1, output._2)))
        }
      }
      case _ =>
        val output = f(time, argument, (pastTime, pastFValue))
        (Some(output._1), Some((argument, output._1, output._2)))
    }
  }
  _ifInputChanged
}

def withPastOutput[T1, T2](
    f: ReactiveStreamAny[T1, T2]
): ReactiveStreamAny[T1, (Option[T2], T2)] = {
  def _withPastOutput(
      time: Double,
      argument: T1,
      past: MemoryTupleAny
  ): ((Option[T2], T2), Option[Any]) = {
    val (pastTime, pastValueAny) = past
    val (pastOutput: Option[T2], pastFValue: Option[Any]) =
      pastValueAny.map(_.asInstanceOf[(T2, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value2) => (Some(value1), value2)
      }
    val output = f(time, argument, (pastTime, pastFValue))
    ((pastOutput, output._1), Some(output._1, output._2))
  }
  _withPastOutput
}
