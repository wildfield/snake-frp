package snake

import scala.annotation.targetName

type Memory = Option[Any]
type ReactiveStreamAny[Input, Output] =
  (Input, Memory) => (Output, Memory)
type SourceAny[Output] =
  (Memory) => (Output, Memory)

def toAny[T1, T2, T3](f: ReactiveStream[T1, T2, T3]): ReactiveStreamAny[T2, T3] = {
  def _toAny(argument: T2, pastAny: Memory): (T3, Memory) = {
    val pastValueAny = pastAny
    val pastValue = pastValueAny.flatMap(_.asInstanceOf[Option[T1]])
    val output = f(0, argument, (Some(0), pastValue))
    (output._1, Some(output._2))
  }
  _toAny
}

def toAnySource[T1, T2](f: Source[T1, T2]): SourceAny[T2] = {
  def _toAny(pastAny: Memory): (T2, Option[Any]) = {
    val pastValue = pastAny.flatMap(_.asInstanceOf[Option[T1]])
    val output = f(0, (Some(0), pastValue))
    (output._1, Some(output._2))
  }
  _toAny
}

def map[T1, T2, T3](
    f: ReactiveStreamAny[T1, T2],
    mapFunc: T2 => T3
): ReactiveStreamAny[T1, T3] = {
  def _map(argument: T1, past: Memory): (T3, Memory) = {
    val value = f(argument, past)
    (mapFunc(value._1), value._2)
  }
  _map
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

def flatMap[T1, T2, T3, T4](
    f: ReactiveStreamAny[T1, T2],
    map: T2 => ReactiveStreamAny[T3, T4]
): ReactiveStreamAny[(T1, T3), T4] = {
  def _flatMap(
      argument: (T1, T3),
      past: Memory
  ): (T4, Memory) = {
    val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
      past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val fOutput = f(argument._1, pastValueF)
    val mappedFOutput = map(fOutput._1)(argument._2, pastValueMapped)
    (mappedFOutput._1, Some((fOutput._2, mappedFOutput._2)))
  }
  _flatMap
}

def flatMapSourceMap[T1, T2, T3](
    f: ReactiveStreamAny[T1, T2],
    map: T2 => SourceAny[T3]
): ReactiveStreamAny[T1, T3] = {
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
  _flatMap
}

def flatMapFromSource[T1, T2, T3](
    f: SourceAny[T1],
    map: T1 => ReactiveStreamAny[T2, T3]
): ReactiveStreamAny[T2, T3] = {
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
  _flatMap
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
    f: ReactiveStreamAny[T1, ReactiveStreamAny[T2, T3]]
): ReactiveStreamAny[(T1, T2), T3] = {
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
  _flatten
}

@targetName("flatten2")
def flatten[T1, T2, T3, T4](
    f: ReactiveStreamAny[T1, (T4, ReactiveStreamAny[(T4, T2), T3])]
): ReactiveStreamAny[(T1, T2), T3] = {
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
  _flatten
}

def branch[T1, T2](
    condition: Boolean,
    f1: ReactiveStreamAny[(Boolean, T1), T2],
    f2: ReactiveStreamAny[(Boolean, T1), T2]
): ReactiveStreamAny[T1, T2] = {
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
  _branch
}

def clearMem[T1, T2](
    f: ReactiveStreamAny[T1, T2]
): ReactiveStreamAny[(Boolean, T1), T2] = {
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
  _clearMem
}

def ignoreInput[T1, T2, T3](
    f: ReactiveStreamAny[T1, T2]
): ReactiveStreamAny[(T3, T1), T2] = {
  def _ignoreInput(
      argument: (T3, T1),
      past: Memory
  ): (T2, Memory) = {
    f(argument._2, past)
  }
  _ignoreInput
}

def cached[T1 <: Equals, T2](
    f: ReactiveStreamAny[T1, T2]
): ReactiveStreamAny[T1, T2] = {
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
  _cached
}

def ifInputChanged[T1 <: Equals, T2](
    f: ReactiveStreamAny[T1, T2]
): ReactiveStreamAny[T1, Option[T2]] = {
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
  _ifInputChanged
}

def withPastOutput[T1, T2](
    f: ReactiveStreamAny[T1, T2]
): ReactiveStreamAny[T1, (Option[T2], T2)] = {
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
  _withPastOutput
}

def feedback[T1, T2](
    f: ReactiveStreamAny[(Option[T2], T1), T2]
): ReactiveStreamAny[T1, T2] = {
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
  _feedback
}

def feedbackSource[T2](
    f: ReactiveStreamAny[Option[T2], T2]
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
    f: ReactiveStreamAny[(Option[T2], T1), (T2, T3)]
): ReactiveStreamAny[T1, T3] = {
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
  _feedbackSource
}

def feedbackChannelSource[T2, T3](
    f: ReactiveStreamAny[Option[T2], (T2, T3)]
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

def assumeStream[T1, T2, T3](
    f: T1 => ReactiveStreamAny[T2, T3]
): ReactiveStreamAny[(T1, T2), T3] = {
  def _assume(
      argument: (T1, T2),
      past: Memory
  ): (T3, Memory) = {
    f(argument._1)(argument._2, past)
  }
  _assume
}

def assumeSource[T1, T2](
    f: T1 => SourceAny[T2]
): ReactiveStreamAny[T1, T2] = {
  def _assumeSource(
      argument: T1,
      past: Memory
  ): (T2, Memory) = {
    f(argument)(past)
  }
  _assumeSource
}

def apply[T1, T2](
    f1: ReactiveStreamAny[T1, T2],
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
    f1: ReactiveStreamAny[(T1, T2), T3],
    a: T1
): ReactiveStreamAny[T2, T3] = {
  def _apply(
      argument: T2,
      past: Memory
  ): (T3, Memory) = {
    val value1 = f1((a, argument), past)
    value1
  }
  _apply
}

def applyPartial2[T1, T2, T3](
    f1: ReactiveStreamAny[(T1, T2), T3],
    a: T2
): ReactiveStreamAny[T1, T3] = {
  def _apply(
      argument: T1,
      past: Memory
  ): (T3, Memory) = {
    val value1 = f1((argument, a), past)
    value1
  }
  _apply
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

def identity[T1](): ReactiveStreamAny[T1, T1] = {
  def _identity(
      argument: T1,
      past: Memory
  ): (T1, Memory) = {
    (argument, past)
  }
  _identity
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
    f1: ReactiveStreamAny[T1, T2],
    f2: ReactiveStreamAny[T2, T3]
): ReactiveStreamAny[T1, T3] = {
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
  _connect
}

def pairAny[T1, T2, T3, T4](
    f1: ReactiveStreamAny[T1, T2],
    f2: ReactiveStreamAny[T3, T4]
): ReactiveStreamAny[(T1, T3), (T2, T4)] = {
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
  _pairCombinator
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
    f1: ReactiveStreamAny[T1, Option[T2]]
): ReactiveStreamAny[T1, T2] = {
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
  _latch
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
