package snake

import scala.annotation.targetName

type Memory = Option[Any]
type ReactiveStreamAny[Input, Output] =
  (Input, Memory) => (Output, Memory)
type SourceAny[Output] =
  (Memory) => (Output, Memory)

trait Reactive[Input, Output] extends ReactiveStreamAny[Input, Output] { self =>
  def applyValue(
      a: Input
  ): SourceAny[Output] = {
    def _apply(
        past: Memory
    ): (Output, Memory) = {
      val value1 = self(a, past)
      value1
    }
    _apply
  }

  def map[T](
      mapFunc: Output => T
  ): Reactive[Input, T] = {
    def _map(argument: Input, past: Memory): (T, Memory) = {
      val value = self(argument, past)
      (mapFunc(value._1), value._2)
    }
    _map _
  }

  def flatMap[T1, T2](
      map: Output => Reactive[T1, T2]
  ): Reactive[(Input, T1), T2] = {
    def _flatMap(
        argument: (Input, T1),
        past: Memory
    ): (T2, Memory) = {
      val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
        past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
          case None        => (None, None)
          case Some(value) => value
        }
      val fOutput = self(argument._1, pastValueF)
      val mappedFOutput = map(fOutput._1)(argument._2, pastValueMapped)
      (mappedFOutput._1, Some((fOutput._2, mappedFOutput._2)))
    }
    _flatMap _
  }
}

implicit def toReactive[Input, Output](
    f: ReactiveStreamAny[Input, Output]
): Reactive[Input, Output] = new Reactive[Input, Output]() {
  def apply(i: Input, m: Option[Any]) = f(i, m)
}

def toAny[T1, T2, T3](f: ReactiveStream[T1, T2, T3]): Reactive[T2, T3] = {
  def _toAny(argument: T2, pastAny: Memory): (T3, Memory) = {
    val pastValueAny = pastAny
    val pastValue = pastValueAny.flatMap(_.asInstanceOf[Option[T1]])
    val output = f(argument, pastValue)
    (output._1, Some(output._2))
  }
  _toAny _
}

def toSourceAny[T1, T2](f: Source[T1, T2]): SourceAny[T2] = {
  def _toAny(pastAny: Memory): (T2, Memory) = {
    val pastValue = pastAny.flatMap(_.asInstanceOf[Option[T1]])
    val output = f(pastValue)
    (output._1, Some(output._2))
  }
  _toAny
}

def mapSource[T1, T2](
    f: SourceAny[T1],
    mapFunc: T1 => T2
): SourceAny[T2] = {
  def _map(past: Memory): (T2, Option[Any]) = {
    val value = f(past)
    (mapFunc(value._1), value._2)
  }
  _map
}

def flatMapSourceMap[T1, T2, T3](
    f: Reactive[T1, T2],
    map: T2 => SourceAny[T3]
): Reactive[T1, T3] = {
  def _flatMap(
      argument: T1,
      past: Memory
  ): (T3, Memory) = {
    val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
      past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val fOutput = f(argument, pastValueF)
    val mappedFOutput = map(fOutput._1)(pastValueMapped)
    (mappedFOutput._1, Some(fOutput._2, mappedFOutput._2))
  }
  _flatMap _
}

def flatMapFromSource[T1, T2, T3](
    f: SourceAny[T1],
    map: T1 => Reactive[T2, T3]
): Reactive[T2, T3] = {
  def _flatMap(
      argument: T2,
      past: Memory
  ): (T3, Memory) = {
    val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
      past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val fOutput = f(pastValueF)
    val mappedFOutput = map(fOutput._1)(argument, pastValueMapped)
    (mappedFOutput._1, Some(fOutput._2, mappedFOutput._2))
  }
  _flatMap _
}

def flatMapSource[T1, T2](
    f: SourceAny[T1],
    map: T1 => SourceAny[T2]
): SourceAny[T2] = {
  def _flatMap(
      past: Memory
  ): (T2, Memory) = {
    val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
      past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val fOutput = f(pastValueF)
    val mappedFOutput = map(fOutput._1)(pastValueMapped)
    (mappedFOutput._1, Some(fOutput._2, mappedFOutput._2))
  }
  _flatMap
}

def flatten[T1, T2, T3](
    f: Reactive[T1, Reactive[T2, T3]]
): Reactive[(T1, T2), T3] = {
  def _flatten(
      argument: (T1, T2),
      past: Memory
  ): (T3, Memory) = {
    val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
      past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val fOutput = f(argument._1, pastValueF)
    val mappedFOutput = fOutput._1(argument._2, pastValueMapped)
    (mappedFOutput._1, Some((fOutput._2, mappedFOutput._2)))
  }
  _flatten _
}

@targetName("flatten2")
def flatten[T1, T2, T3, T4](
    f: Reactive[T1, (T4, Reactive[(T4, T2), T3])]
): Reactive[(T1, T2), T3] = {
  def _flatten(
      argument: (T1, T2),
      past: Memory
  ): (T3, Memory) = {
    val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
      past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val fOutput = f(argument._1, pastValueF)
    val mappedFOutput =
      fOutput._1._2((fOutput._1._1, argument._2), pastValueMapped)
    (mappedFOutput._1, Some((fOutput._2, mappedFOutput._2)))
  }
  _flatten _
}

def branch[T1, T2](
    condition: Boolean,
    f1: Reactive[(Boolean, T1), T2],
    f2: Reactive[(Boolean, T1), T2]
): Reactive[T1, T2] = {
  def _branch(
      argument: T1,
      past: Memory
  ): (T2, Memory) = {
    val (pastConditionOption: Option[Boolean], pastValueF1: Option[Any], pastValueF2: Option[Any]) =
      past.map(_.asInstanceOf[(Boolean, Option[Any], Option[Any])]) match {
        case None                           => (None, None)
        case Some((value1, value2, value3)) => (Some(value1), value2, value3)
      }
    if (condition) {
      val output = f1(
        (pastConditionOption.map(_ != condition).getOrElse(true), argument),
        pastValueF1
      )
      (
        output._1,
        Some((condition, output._2, pastValueF2))
      )
    } else {
      val output = f2(
        (pastConditionOption.map(_ != condition).getOrElse(true), argument),
        pastValueF2
      )
      (
        output._1,
        Some((condition, pastValueF1, output._2))
      )
    }
  }
  _branch _
}

def clearMem[T1, T2](
    f: Reactive[T1, T2]
): Reactive[(Boolean, T1), T2] = {
  def _clearMem(
      argument: (Boolean, T1),
      past: Memory
  ): (T2, Memory) = {
    if (argument._1) {
      f(argument._2, None)
    } else {
      f(argument._2, past)
    }
  }
  _clearMem _
}

def ignoreInput[T1, T2, T3](
    f: Reactive[T1, T2]
): Reactive[(T3, T1), T2] = {
  def _ignoreInput(
      argument: (T3, T1),
      past: Memory
  ): (T2, Memory) = {
    f(argument._2, past)
  }
  _ignoreInput _
}

def cached[T1 <: Equals, T2](
    f: Reactive[T1, T2]
): Reactive[T1, T2] = {
  def _cached(
      argument: T1,
      past: Memory
  ): (T2, Option[Any]) = {
    val (pastInput: Option[T1], pastOutput: Option[T2], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T1, T2, Option[Any])]) match {
        case None                         => (None, None)
        case Some(value1, value2, value3) => (Some(value1), Some(value2), value3)
      }
    (pastInput, pastOutput) match {
      case (Some(pastInput), Some(pastOutput)) => {
        val isInputSame = pastInput.equals(argument)
        if (isInputSame) {
          (pastOutput, Some((argument, pastOutput, pastFValue)))
        } else {
          val output = f(argument, pastFValue)
          (output._1, Some((argument, output._1, output._2)))
        }
      }
      case _ =>
        val output = f(argument, pastFValue)
        (output._1, Some((argument, output._1, output._2)))
    }
  }
  _cached _
}

def ifInputChanged[T1 <: Equals, T2](
    f: Reactive[T1, T2]
): Reactive[T1, Option[T2]] = {
  def _ifInputChanged(
      argument: T1,
      past: Memory
  ): (Option[T2], Option[Any]) = {
    val (pastInput: Option[T1], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T1, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value3) => (Some(value1), value3)
      }
    pastInput match {
      case Some(pastInput) => {
        val isInputSame = pastInput.equals(argument)
        if (isInputSame) {
          (None, Some((argument, pastFValue)))
        } else {
          val output = f(argument, pastFValue)
          (Some(output._1), Some((argument, output._1, output._2)))
        }
      }
      case _ =>
        val output = f(argument, pastFValue)
        (Some(output._1), Some((argument, output._1, output._2)))
    }
  }
  _ifInputChanged _
}

def withPastOutput[T1, T2](
    f: Reactive[T1, T2]
): Reactive[T1, (Option[T2], T2)] = {
  def _withPastOutput(
      argument: T1,
      past: Memory
  ): ((Option[T2], T2), Memory) = {
    val (pastOutput: Option[T2], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T2, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value2) => (Some(value1), value2)
      }
    val output = f(argument, pastFValue)
    ((pastOutput, output._1), Some(output._1, output._2))
  }
  _withPastOutput _
}

def withPastOutputSource[T1](
    f: SourceAny[T1]
): SourceAny[(Option[T1], T1)] = {
  def _withPastOutput(
      past: Memory
  ): ((Option[T1], T1), Memory) = {
    val (pastOutput: Option[T1], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T1, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value2) => (Some(value1), value2)
      }
    val output = f(pastFValue)
    ((pastOutput, output._1), Some(output._1, output._2))
  }
  _withPastOutput
}

def feedback[T1, T2](
    f: Reactive[(Option[T2], T1), T2]
): Reactive[T1, T2] = {
  def _feedback(
      argument: T1,
      past: Memory
  ): (T2, Memory) = {
    val (pastOutput: Option[T2], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T2, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value2) => (Some(value1), value2)
      }
    val output = f((pastOutput, argument), pastFValue)
    (output._1, Some(output._1, output._2))
  }
  _feedback _
}

def feedbackSource[T2](
    f: Reactive[Option[T2], T2]
): SourceAny[T2] = {
  def _feedbackSource(
      past: Memory
  ): (T2, Memory) = {
    val (pastOutput: Option[T2], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T2, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value2) => (Some(value1), value2)
      }
    val output = f(pastOutput, pastFValue)
    (output._1, Some(output._1, output._2))
  }
  _feedbackSource
}

def feedbackChannel[T1, T2, T3](
    f: Reactive[(Option[T2], T1), (T2, T3)]
): Reactive[T1, T3] = {
  def _feedbackSource(
      argument: T1,
      past: Memory
  ): (T3, Option[Any]) = {
    val (pastOutput: Option[T2], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T2, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value2) => (Some(value1), value2)
      }
    val output = f((pastOutput, argument), pastFValue)
    (output._1._2, Some(output._1._1, output._2))
  }
  _feedbackSource _
}

def feedbackChannelSource[T2, T3](
    f: Reactive[Option[T2], (T2, T3)]
): SourceAny[T3] = {
  def _feedbackSource(
      past: Memory
  ): (T3, Memory) = {
    val (pastOutput: Option[T2], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T2, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value2) => (Some(value1), value2)
      }
    val output = f(pastOutput, pastFValue)
    (output._1._2, Some(output._1._1, output._2))
  }
  _feedbackSource
}

def assumeInput[T1, T2, T3](
    f: T1 => Reactive[T2, T3]
): Reactive[(T1, T2), T3] = {
  def _assume(
      argument: (T1, T2),
      past: Memory
  ): (T3, Memory) = {
    f(argument._1)(argument._2, past)
  }
  _assume _
}

def assumeInputSource[T1, T2](
    f: T1 => SourceAny[T2]
): Reactive[T1, T2] = {
  def _assumeSource(
      argument: T1,
      past: Memory
  ): (T2, Memory) = {
    f(argument)(past)
  }
  _assumeSource _
}

def applyValue[T1, T2](
    f1: Reactive[T1, T2],
    a: T1
): SourceAny[T2] = {
  def _apply(
      past: Memory
  ): (T2, Memory) = {
    val value1 = f1(a, past)
    value1
  }
  _apply
}

def applyPartial[T1, T2, T3](
    f1: Reactive[(T1, T2), T3],
    a: T1
): Reactive[T2, T3] = {
  def _apply(
      argument: T2,
      past: Memory
  ): (T3, Memory) = {
    val value1 = f1((a, argument), past)
    value1
  }
  _apply _
}

def applyPartial2[T1, T2, T3](
    f1: Reactive[(T1, T2), T3],
    a: T2
): Reactive[T1, T3] = {
  def _apply(
      argument: T1,
      past: Memory
  ): (T3, Memory) = {
    val value1 = f1((argument, a), past)
    value1
  }
  _apply _
}

def detectChange[T1 <: Equals](
    f: SourceAny[T1]
): SourceAny[(Boolean, T1)] = {
  def _detectChange(
      past: Memory
  ): ((Boolean, T1), Option[Any]) = {
    val (pastOutput: Option[T1], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T1, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value3) => (Some(value1), value3)
      }
    val output = f(pastFValue)
    val didOutputChange = pastOutput.map(_.equals(output._1)).getOrElse(true)
    ((didOutputChange, output._1), Some(output._1, output._2))
  }
  _detectChange
}

def identity[T1](): Reactive[T1, T1] = {
  def _identity(
      argument: T1,
      past: Memory
  ): (T1, Memory) = {
    (argument, past)
  }
  _identity _
}

def identitySource[T1](value: T1): SourceAny[T1] = {
  def _identity(
      past: Memory
  ): (T1, Memory) = {
    (value, past)
  }
  _identity
}

def connect[T1, T2, T3](
    f1: Reactive[T1, T2],
    f2: Reactive[T2, T3]
): Reactive[T1, T3] = {
  def _connect(
      argument: T1,
      past: Memory
  ): (T3, Option[Any]) = {
    val (pastValueF1: Option[Any], pastValueF2: Option[Any]) =
      past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val f1Output = f1(argument, pastValueF1)
    val f2Output = f2(f1Output._1, pastValueF2)
    (f2Output._1, Some((f1Output._2, f2Output._2)))
  }
  _connect _
}

def pairAny[T1, T2, T3, T4](
    f1: Reactive[T1, T2],
    f2: Reactive[T3, T4]
): Reactive[(T1, T3), (T2, T4)] = {
  def _pairCombinator(
      argument: (T1, T3),
      past: Memory
  ): ((T2, T4), Option[Any]) = {
    val (pastValueF1: Option[Any], pastValueF2: Option[Any]) =
      past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val value1 = f1(argument._1, pastValueF1)
    val value2 = f2(argument._2, pastValueF2)
    ((value1._1, value2._1), Some((value1._2, value2._2)))
  }
  _pairCombinator _
}

def pairSourceAny[T1, T2](
    f1: SourceAny[T1],
    f2: SourceAny[T2]
): SourceAny[(T1, T2)] = {
  def _pairCombinator(
      past: Memory
  ): ((T1, T2), Option[Any]) = {
    val (pastValueF1: Option[Any], pastValueF2: Option[Any]) =
      past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val value1 = f1(pastValueF1)
    val value2 = f2(pastValueF2)
    ((value1._1, value2._1), Some((value1._2, value2._2)))
  }
  _pairCombinator
}

def latch[T1, T2](
    defaultValue: T2,
    f1: Reactive[T1, Option[T2]]
): Reactive[T1, T2] = {
  def _latch(
      argument: T1,
      past: Memory
  ): (T2, Memory) = {
    val pastValue: Option[T2] = past.map(_.asInstanceOf[T2])
    val value = f1(argument, pastValue)
    value._1 match {
      case Some(value) =>
        (value, Some(value))
      case None =>
        pastValue match {
          case Some(pastValue) =>
            (pastValue, Some(pastValue))
          case None =>
            (defaultValue, Some(defaultValue))
        }
    }
  }
  _latch _
}

def latchValue[T1](
    defaultValue: T1,
    value: Option[T1]
): SourceAny[T1] = {
  def _latch(
      past: Memory
  ): (T1, Option[Any]) = {
    val pastValue: Option[T1] = past.map(_.asInstanceOf[T1])
    value match {
      case Some(value) =>
        (value, Some(value))
      case None =>
        pastValue match {
          case Some(pastValue) =>
            (pastValue, Some(pastValue))
          case None =>
            (defaultValue, Some(defaultValue))
        }
    }
  }
  _latch
}

def latchSource[T1](
    defaultValue: T1,
    f1: SourceAny[Option[T1]]
): SourceAny[T1] = {
  def _latch(
      past: Memory
  ): (T1, Option[Any]) = {
    val pastValue: Option[T1] = past.map(_.asInstanceOf[T1])
    val value = f1(pastValue)
    value._1 match {
      case Some(value) =>
        (value, Some(value))
      case None =>
        pastValue match {
          case Some(pastValue) =>
            (pastValue, Some(pastValue))
          case None =>
            (defaultValue, Some(defaultValue))
        }
    }
  }
  _latch
}
