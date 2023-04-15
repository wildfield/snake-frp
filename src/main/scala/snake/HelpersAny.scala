package snake

import scala.annotation.targetName
import scala.language.implicitConversions

type Memory = Option[Any]
type ReactiveStreamAny[Input, Output] =
  (Input, Memory) => (Output, Memory)
type SourceAny[Output] =
  (Memory) => (Output, Memory)

type OldSource[Memory, Output] =
  (Option[Memory]) => (Output, (Option[Memory]))

trait Source[Output] extends SourceAny[Output] { self =>
  def map[T](
      mapFunc: Output => T
  ): Source[T] = {
    def _map(past: Memory): (T, Option[Any]) = {
      val value = self(past)
      (mapFunc(value._1), value._2)
    }
    toSource(_map)
  }

  def flatMap[T1, T2](
      map: Output => Reactive[T1, T2]
  ): Reactive[T1, T2] = {
    def _flatMap(
        argument: T1,
        past: Memory
    ): (T2, Memory) = {
      val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
        past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
          case None        => (None, None)
          case Some(value) => value
        }
      val fOutput = self(pastValueF)
      val mappedFOutput = map(fOutput._1)(argument, pastValueMapped)
      (mappedFOutput._1, Some(fOutput._2, mappedFOutput._2))
    }
    toReactive(_flatMap)
  }

  def flatMapSource[T1](
      map: Output => Source[T1]
  ): Source[T1] = {
    def _flatMap(
        past: Memory
    ): (T1, Memory) = {
      val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
        past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
          case None        => (None, None)
          case Some(value) => value
        }
      val fOutput = self(pastValueF)
      val mappedFOutput = map(fOutput._1)(pastValueMapped)
      (mappedFOutput._1, Some(fOutput._2, mappedFOutput._2))
    }
    toSource(_flatMap)
  }

  def withPastOutput(): Source[(Option[Output], Output)] = {
    def _withPastOutput(
        past: Memory
    ): ((Option[Output], Output), Memory) = {
      val (pastOutput: Option[Output], pastFValue: Option[Any]) =
        past.map(_.asInstanceOf[(Output, Option[Any])]) match {
          case None                 => (None, None)
          case Some(value1, value2) => (Some(value1), value2)
        }
      val output = self(pastFValue)
      ((pastOutput, output._1), Some(output._1, output._2))
    }
    toSource(_withPastOutput)
  }
}

implicit def toSource[Output](
    f: SourceAny[Output]
): Source[Output] = new Source[Output]() {
  def apply(m: Option[Any]) = f(m)
}

trait Reactive[Input, Output] extends ReactiveStreamAny[Input, Output] { self =>
  def applyValue(
      a: Input
  ): Source[Output] = {
    def _apply(
        past: Memory
    ): (Output, Memory) = {
      val value1 = self(a, past)
      value1
    }
    toSource(_apply)
  }

  def map[T](
      mapFunc: Output => T
  ): Reactive[Input, T] = {
    def _map(argument: Input, past: Memory): (T, Memory) = {
      val value = self(argument, past)
      (mapFunc(value._1), value._2)
    }
    toReactive(_map)
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
    toReactive(_flatMap)
  }

  def flatMapSource[T](
      map: Output => Source[T]
  ): Reactive[Input, T] = {
    def _flatMap(
        argument: Input,
        past: Memory
    ): (T, Memory) = {
      val (pastValueF: Option[Any], pastValueMapped: Option[Any]) =
        past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
          case None        => (None, None)
          case Some(value) => value
        }
      val fOutput = self(argument, pastValueF)
      val mappedFOutput = map(fOutput._1)(pastValueMapped)
      (mappedFOutput._1, Some(fOutput._2, mappedFOutput._2))
    }
    toReactive(_flatMap)
  }

  def clearMem(
  ): Reactive[(Boolean, Input), Output] = {
    def _clearMem(
        argument: (Boolean, Input),
        past: Memory
    ): (Output, Memory) = {
      if (argument._1) {
        self(argument._2, None)
      } else {
        self(argument._2, past)
      }
    }
    toReactive(_clearMem)
  }

  def ignoreInput[T](
  ): Reactive[(T, Input), Output] = {
    def _ignoreInput(
        argument: (T, Input),
        past: Memory
    ): (Output, Memory) = {
      self(argument._2, past)
    }
    toReactive(_ignoreInput)
  }

  def withPastOutput(
  ): Reactive[Input, (Option[Output], Output)] = {
    def _withPastOutput(
        argument: Input,
        past: Memory
    ): ((Option[Output], Output), Memory) = {
      val (pastOutput: Option[Output], pastFValue: Option[Any]) =
        past.map(_.asInstanceOf[(Output, Option[Any])]) match {
          case None                 => (None, None)
          case Some(value1, value2) => (Some(value1), value2)
        }
      val output = self(argument, pastFValue)
      ((pastOutput, output._1), Some(output._1, output._2))
    }
    _withPastOutput _
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
    val pastValue = pastValueAny.map(_.asInstanceOf[T1])
    val output = f(argument, pastValue)
    (output._1, output._2)
  }
  toReactive(_toAny)
}

def toSourceAny[T1, T2](f: OldSource[T1, T2]): Source[T2] = {
  def _toAny(pastAny: Memory): (T2, Memory) = {
    val pastValue = pastAny.map(_.asInstanceOf[T1])
    val output = f(pastValue)
    (output._1, output._2)
  }
  toSource(_toAny)
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
        case None                           => (None, None, None)
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

def cached[T1 <: Equals, T2](
    f: Reactive[T1, T2]
): Reactive[T1, T2] = {
  def _cached(
      argument: T1,
      past: Memory
  ): (T2, Option[Any]) = {
    val (pastInput: Option[T1], pastOutput: Option[T2], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T1, T2, Option[Any])]) match {
        case None                         => (None, None, None)
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
  toReactive(_cached)
}

def cachedSource[T1 <: Equals, T2](
    inputs: T1,
    f: Source[T2]
): Source[T2] = {
  def _cached(
      past: Memory
  ): (T2, Option[Any]) = {
    val (pastInput: Option[T1], pastOutput: Option[T2], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T1, T2, Option[Any])]) match {
        case None                         => (None, None, None)
        case Some(value1, value2, value3) => (Some(value1), Some(value2), value3)
      }
    (pastInput, pastOutput) match {
      case (Some(pastInput), Some(pastOutput)) => {
        val isInputSame = pastInput.equals(inputs)
        if (isInputSame) {
          (pastOutput, Some((inputs, pastOutput, pastFValue)))
        } else {
          val output = f(pastFValue)
          (output._1, Some((inputs, output._1, output._2)))
        }
      }
      case _ =>
        val output = f(pastFValue)
        (output._1, Some((inputs, output._1, output._2)))
    }
  }
  toSource(_cached)
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
  toReactive(_ifInputChanged)
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
  toReactive(_feedback)
}

def feedbackSource[T2](
    f: Reactive[Option[T2], T2]
): Source[T2] = {
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
  toSource(_feedbackSource)
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
  toReactive(_feedbackSource)
}

def feedbackChannelSource[T2, T3](
    f: Reactive[Option[T2], (T2, T3)]
): Source[T3] = {
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
  toSource(_feedbackSource)
}

def feedbackChannelSourceFlatMap[T1, T2](
    fMap: Option[T1] => Source[(T1, T2)]
): Source[T2] = {
  def _mapWithFeedback(
      past: Memory
  ): (T2, Memory) = {
    val (pastOutput: Option[T1], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T1, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value2) => (Some(value1), value2)
      }
    val output = fMap(pastOutput)(pastFValue)
    (output._1._2, Some(output._1._1, output._2))
  }
  toSource(_mapWithFeedback)
}

def feedbackSourceFlatMap[T2](
    fMap: Option[T2] => Source[T2]
): Source[T2] = {
  def _mapWithFeedback(
      past: Memory
  ): (T2, Memory) = {
    val (pastOutput: Option[T2], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T2, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value2) => (Some(value1), value2)
      }
    val output = fMap(pastOutput)(pastFValue)
    (output._1, Some(output._1, output._2))
  }
  toSource(_mapWithFeedback)
}

def feedbackSelectSourceFlatMap[T2, T3](
    fMap: Option[T2] => Source[T3],
    selectMap: T3 => T2
): Source[T3] = {
  def _mapWithFeedback(
      past: Memory
  ): (T3, Memory) = {
    val (pastOutput: Option[T2], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(T2, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value2) => (Some(value1), value2)
      }
    val output = fMap(pastOutput)(pastFValue)
    (output._1, Some(selectMap(output._1), output._2))
  }
  toSource(_mapWithFeedback)
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
  toReactive(_assume)
}

def assumeInputSource[T1, T2](
    f: T1 => Source[T2]
): Reactive[T1, T2] = {
  def _assumeSource(
      argument: T1,
      past: Memory
  ): (T2, Memory) = {
    f(argument)(past)
  }
  toReactive(_assumeSource)
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
  toReactive(_apply)
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
  toReactive(_apply)
}

def detectChange[T1 <: Equals](
    f: Source[T1]
): Source[(Boolean, T1)] = {
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
  toSource(_detectChange)
}

def identity[T1](): Reactive[T1, T1] = {
  def _identity(
      argument: T1,
      past: Memory
  ): (T1, Memory) = {
    (argument, past)
  }
  toReactive(_identity)
}

def identitySource[T1](value: T1): Source[T1] = {
  def _identity(
      past: Memory
  ): (T1, Memory) = {
    (value, past)
  }
  toSource(_identity)
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
  toReactive(_connect)
}

def pair[T1, T2, T3, T4](
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
  toReactive(_pairCombinator)
}

def sharedPair[T1, T2, T3](
    f1: Reactive[T1, T2],
    f2: Reactive[T1, T3]
): Reactive[T1, (T2, T3)] = {
  def _pairCombinator(
      argument: T1,
      past: Memory
  ): ((T2, T3), Option[Any]) = {
    val (pastValueF1: Option[Any], pastValueF2: Option[Any]) =
      past.map(_.asInstanceOf[(Option[Any], Option[Any])]) match {
        case None        => (None, None)
        case Some(value) => value
      }
    val value1 = f1(argument, pastValueF1)
    val value2 = f2(argument, pastValueF2)
    ((value1._1, value2._1), Some((value1._2, value2._2)))
  }
  toReactive(_pairCombinator)
}

def pair[T1, T2](
    f1: Source[T1],
    f2: Source[T2]
): Source[(T1, T2)] = {
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
  toSource(_pairCombinator)
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
  toReactive(_latch)
}

def latchValue[T1](
    defaultValue: T1,
    value: Option[T1]
): Source[T1] = {
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
  toSource(_latch)
}

def latchSource[T1](
    defaultValue: T1,
    f1: Source[Option[T1]]
): Source[T1] = {
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
  toSource(_latch)
}

def repeatPast[Output](input: Output): Source[(Option[Output])] = {
  def _repeatPast(
      past: Memory
  ): (Option[Output], Memory) = {
    val (pastOutput: Option[Output], pastFValue: Option[Any]) =
      past.map(_.asInstanceOf[(Output, Option[Any])]) match {
        case None                 => (None, None)
        case Some(value1, value2) => (Some(value1), value2)
      }
    (pastOutput, Some(input, pastFValue))
  }
  toSource(_repeatPast)
}
