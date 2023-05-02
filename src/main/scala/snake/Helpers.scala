package snake

import scala.annotation.targetName
import scala.language.implicitConversions

type ReactiveStreamFunc[Input, Output, Memory] =
  (Input, Memory) => (Output, Memory)
type SourceAny[Output, Memory] =
  (Memory) => (Output, Memory)

trait Source[Output, Memory] extends SourceAny[Output, Memory] { self =>
  def map[T](
      mapFunc: Output => T
  ): Source[T, Memory] = {
    def _map(past: Memory): (T, Memory) = {
      val value = self(past)
      (mapFunc(value._1), value._2)
    }
    toSource(_map)
  }

  def flatMap[T1, T2, MappedMemory](
      map: Output => ReactiveStream[T1, T2, MappedMemory]
  ): ReactiveStream[T1, T2, (Memory, MappedMemory)] = {
    def _flatMap(
        argument: T1,
        past: (Memory, MappedMemory)
    ): (T2, (Memory, MappedMemory)) = {
      val (pastValueF, pastValueMapped) = past
      val fOutput = self(pastValueF)
      val mappedFOutput = map(fOutput._1)(argument, pastValueMapped)
      (mappedFOutput._1, (fOutput._2, mappedFOutput._2))
    }
    toReactiveStream(_flatMap)
  }

  def flatMapSource[T1, MappedMemory](
      map: Output => Source[T1, MappedMemory]
  ): Source[T1, (Memory, MappedMemory)] = {
    def _flatMap(
        past: (Memory, MappedMemory)
    ): (T1, (Memory, MappedMemory)) = {
      val (pastValueF, pastValueMapped) = past
      val fOutput = self(pastValueF)
      val mappedFOutput = map(fOutput._1)(pastValueMapped)
      (mappedFOutput._1, (fOutput._2, mappedFOutput._2))
    }
    toSource(_flatMap)
  }

  def withPastOutput(): Source[(Option[Output], Output), (Option[Output], Memory)] = {
    def _withPastOutput(
        past: (Option[Output], Memory)
    ): ((Option[Output], Output), (Option[Output], Memory)) = {
      val (pastOutput, pastFValue) = past
      val output = self(pastFValue)
      ((pastOutput, output._1), (Some(output._1), output._2))
    }
    toSource(_withPastOutput)
  }

  def mapWithMemory[T](
      f: (Output, Memory) => (T, Memory)
  ): Source[T, Memory] = {
    def _map(past: Memory): (T, Memory) = {
      val value = self(past)
      f(value._1, value._2)
    }
    toSource(_map)
  }

  def mapMemory[M1](
      inF: M1 => Memory,
      outF: Memory => M1
  ): Source[Output, M1] = {
    def _mapMemory(
        past: M1
    ): (Output, M1) = {
      val output = self(inF(past))
      (output._1, outF(output._2))
    }
    toSource(_mapMemory)
  }

  def withInitialMemory(
      initialValue: Memory
  ): Source[Output, Option[Memory]] =
    self.mapMemory(
      _ match {
        case Some(memory) => memory
        case None         => initialValue
      },
      Some(_)
    )
}

implicit def toSource[Output, Memory](
    f: SourceAny[Output, Memory]
): Source[Output, Memory] = new Source[Output, Memory]() {
  def apply(m: Memory) = f(m)
}

trait ReactiveStream[Input, Output, Memory] extends ReactiveStreamFunc[Input, Output, Memory] {
  self =>
  def applyValue(
      a: Input
  ): Source[Output, Memory] = {
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
  ): ReactiveStream[Input, T, Memory] = {
    def _map(argument: Input, past: Memory): (T, Memory) = {
      val value = self(argument, past)
      (mapFunc(value._1), value._2)
    }
    toReactiveStream(_map)
  }

  def mapWithMemory[T](
      mapFunc: (Output, Memory) => (T, Memory)
  ): ReactiveStream[Input, T, Memory] = {
    def _map(argument: Input, past: Memory): (T, Memory) = {
      val value = self(argument, past)
      mapFunc(value._1, value._2)
    }
    toReactiveStream(_map)
  }

  def flatMap[T1, T2, MappedMemory](
      map: Output => ReactiveStream[T1, T2, MappedMemory]
  ): ReactiveStream[(Input, T1), T2, (Memory, MappedMemory)] = {
    def _flatMap(
        argument: (Input, T1),
        past: (Memory, MappedMemory)
    ): (T2, (Memory, MappedMemory)) = {
      val (pastValueF, pastValueMapped) = past
      val fOutput = self(argument._1, pastValueF)
      val mappedFOutput = map(fOutput._1)(argument._2, pastValueMapped)
      (mappedFOutput._1, (fOutput._2, mappedFOutput._2))
    }
    toReactiveStream(_flatMap)
  }

  def flatMapSource[T, MappedMemory](
      map: Output => Source[T, MappedMemory]
  ): ReactiveStream[Input, T, (Memory, MappedMemory)] = {
    def _flatMap(
        argument: Input,
        past: (Memory, MappedMemory)
    ): (T, (Memory, MappedMemory)) = {
      val (pastValueF, pastValueMapped) = past
      val fOutput = self(argument, pastValueF)
      val mappedFOutput = map(fOutput._1)(pastValueMapped)
      (mappedFOutput._1, (fOutput._2, mappedFOutput._2))
    }
    toReactiveStream(_flatMap)
  }

  def ignoreInput[T](
  ): ReactiveStream[(T, Input), Output, Memory] = {
    def _ignoreInput(
        argument: (T, Input),
        past: Memory
    ): (Output, Memory) = {
      self(argument._2, past)
    }
    toReactiveStream(_ignoreInput)
  }

  def withPastOutput(
  ): ReactiveStream[Input, (Option[Output], Output), (Option[Output], Memory)] = {
    def _withPastOutput(
        argument: Input,
        past: (Option[Output], Memory)
    ): ((Option[Output], Output), (Option[Output], Memory)) = {
      val (pastOutput, pastFValue) = past
      val output = self(argument, pastFValue)
      ((pastOutput, output._1), (Some(output._1), output._2))
    }
    toReactiveStream(_withPastOutput)
  }

  def mapMemory[M1](
      inF: M1 => Memory,
      outF: Memory => M1
  ): ReactiveStream[Input, Output, M1] = {
    def _mapMemory(
        argument: Input,
        past: M1
    ): (Output, M1) = {
      val output = self(argument, inF(past))
      (output._1, outF(output._2))
    }
    toReactiveStream(_mapMemory)
  }

  def withInitialMemory(
      initialValue: Memory
  ): ReactiveStream[Input, Output, Option[Memory]] =
    self.mapMemory(
      _ match {
        case Some(memory) => memory
        case None         => initialValue
      },
      Some(_)
    )
}

implicit def toReactiveStream[Input, Output, Memory](
    f: ReactiveStreamFunc[Input, Output, Memory]
): ReactiveStream[Input, Output, Memory] = new ReactiveStream[Input, Output, Memory]() {
  def apply(i: Input, m: Memory) = f(i, m)
}

def flatten[T1, T2, T3, M1, M2](
    f: ReactiveStream[T1, ReactiveStream[T2, T3, M2], M1]
): ReactiveStream[(T1, T2), T3, (M1, M2)] = {
  def _flatten(
      argument: (T1, T2),
      past: (M1, M2)
  ): (T3, (M1, M2)) = {
    val (pastValueF, pastValueMapped) = past
    val fOutput = f(argument._1, pastValueF)
    val mappedFOutput = fOutput._1(argument._2, pastValueMapped)
    (mappedFOutput._1, (fOutput._2, mappedFOutput._2))
  }
  _flatten _
}

def cached[T1 <: Equals, T2, Memory](
    f: ReactiveStream[T1, T2, Memory]
): ReactiveStream[T1, T2, (Option[T1], Option[T2], Memory)] = {
  def _cached(
      argument: T1,
      past: (Option[T1], Option[T2], Memory)
  ): (T2, (Option[T1], Option[T2], Memory)) = {
    val (pastInput, pastOutput, pastFValue) = past
    (pastInput, pastOutput) match {
      case (Some(pastInput), Some(pastOutput)) => {
        val isInputSame = pastInput.equals(argument)
        if (isInputSame) {
          (pastOutput, (Some(argument), Some(pastOutput), pastFValue))
        } else {
          val output = f(argument, pastFValue)
          (output._1, (Some(argument), Some(output._1), output._2))
        }
      }
      case _ =>
        val output = f(argument, pastFValue)
        (output._1, (Some(argument), Some(output._1), output._2))
    }
  }
  toReactiveStream(_cached)
}

def cachedSource[T1 <: Equals, T2, Memory](
    inputs: T1,
    f: Source[T2, Memory]
): Source[T2, (Option[T1], Option[T2], Memory)] = {
  def _cached(
      past: (Option[T1], Option[T2], Memory)
  ): (T2, (Option[T1], Option[T2], Memory)) = {
    val (pastInput, pastOutput, pastFValue) = past
    (pastInput, pastOutput) match {
      case (Some(pastInput), Some(pastOutput)) => {
        val isInputSame = pastInput.equals(inputs)
        if (isInputSame) {
          (pastOutput, (Some(inputs), Some(pastOutput), pastFValue))
        } else {
          val output = f(pastFValue)
          (output._1, (Some(inputs), Some(output._1), output._2))
        }
      }
      case _ =>
        val output = f(pastFValue)
        (output._1, (Some(inputs), Some(output._1), output._2))
    }
  }
  toSource(_cached)
}

def feedback[T1, T2, M1](
    f: ReactiveStream[(Option[T2], T1), T2, M1]
): ReactiveStream[T1, T2, (Option[T2], M1)] = {
  def _feedback(
      argument: T1,
      past: (Option[T2], M1)
  ): (T2, (Option[T2], M1)) = {
    val (pastOutput, pastFValue) = past
    val output = f((pastOutput, argument), pastFValue)
    (output._1, (Some(output._1), output._2))
  }
  toReactiveStream(_feedback)
}

def feedbackSource[T2, Memory](
    f: ReactiveStream[Option[T2], T2, Memory]
): Source[T2, (Option[T2], Memory)] = {
  def _feedbackSource(
      past: (Option[T2], Memory)
  ): (T2, (Option[T2], Memory)) = {
    val (pastOutput, pastFValue) = past
    val output = f(pastOutput, pastFValue)
    (output._1, (Some(output._1), output._2))
  }
  toSource(_feedbackSource)
}

def feedbackChannel[T1, T2, T3, Memory](
    f: ReactiveStream[(Option[T2], T1), (T2, T3), Memory]
): ReactiveStream[T1, T3, (Option[T2], Memory)] = {
  def _feedbackSource(
      argument: T1,
      past: (Option[T2], Memory)
  ): (T3, (Option[T2], Memory)) = {
    val (pastOutput, pastFValue) = past
    val output = f((pastOutput, argument), pastFValue)
    (output._1._2, (Some(output._1._1), output._2))
  }
  toReactiveStream(_feedbackSource)
}

def feedbackChannelSource[T2, T3, Memory](
    f: ReactiveStream[Option[T2], (T2, T3), Memory]
): Source[T3, (Option[T2], Memory)] = {
  def _feedbackSource(
      past: (Option[T2], Memory)
  ): (T3, (Option[T2], Memory)) = {
    val (pastOutput, pastFValue) = past
    val output = f(pastOutput, pastFValue)
    (output._1._2, (Some(output._1._1), output._2))
  }
  toSource(_feedbackSource)
}

def assumeInput[T1, T2, T3, Memory](
    f: T1 => ReactiveStream[T2, T3, Memory]
): ReactiveStream[(T1, T2), T3, Memory] = {
  def _assume(
      argument: (T1, T2),
      past: Memory
  ): (T3, Memory) = {
    f(argument._1)(argument._2, past)
  }
  toReactiveStream(_assume)
}

def assumeInputSource[T1, T2, Memory](
    f: T1 => Source[T2, Memory]
): ReactiveStream[T1, T2, Memory] = {
  def _assumeSource(
      argument: T1,
      past: Memory
  ): (T2, Memory) = {
    f(argument)(past)
  }
  toReactiveStream(_assumeSource)
}

def applyPartial[T1, T2, T3, Memory](
    f1: ReactiveStream[(T1, T2), T3, Memory],
    a: T1
): ReactiveStream[T2, T3, Memory] = {
  def _apply(
      argument: T2,
      past: Memory
  ): (T3, Memory) = {
    f1((a, argument), past)
  }
  toReactiveStream(_apply)
}

def applyPartial2[T1, T2, T3, Memory](
    f1: ReactiveStream[(T1, T2), T3, Memory],
    a: T2
): ReactiveStream[T1, T3, Memory] = {
  def _apply(
      argument: T1,
      past: Memory
  ): (T3, Memory) = {
    val value1 = f1((argument, a), past)
    value1
  }
  toReactiveStream(_apply)
}

def detectChange[T1 <: Equals, Memory](
    f: Source[T1, Memory]
): Source[(Boolean, T1), (Option[T1], Memory)] = {
  def _detectChange(
      past: (Option[T1], Memory)
  ): ((Boolean, T1), (Option[T1], Memory)) = {
    val (pastOutput, pastFValue) = past
    val output = f(pastFValue)
    val didOutputChange = pastOutput.map(_.equals(output._1)).getOrElse(true)
    ((didOutputChange, output._1), (Some(output._1), output._2))
  }
  toSource(_detectChange)
}

def identity[T1](): ReactiveStream[T1, T1, Unit] = {
  def _identity(
      argument: T1,
      past: Unit
  ): (T1, Unit) = {
    (argument, past)
  }
  toReactiveStream(_identity)
}

def identityWithPast[T1]: ReactiveStream[T1, (Option[T1], T1), Option[T1]] = {
  def _identity(
      argument: T1,
      past: Option[T1]
  ): ((Option[T1], T1), Option[T1]) = {
    ((past, argument), Some(argument))
  }
  toReactiveStream(_identity)
}

def identityWithMemory[T1, M1]: ReactiveStream[T1, T1, M1] = {
  def _identity(
      argument: T1,
      past: M1
  ): (T1, M1) = {
    (argument, past)
  }
  toReactiveStream(_identity)
}

def identitySource[T1](value: T1): Source[T1, Unit] = {
  def _identity(
      past: Unit
  ): (T1, Unit) = {
    (value, past)
  }
  toSource(_identity)
}

def connect[T1, T2, T3, M1, M2](
    f1: ReactiveStream[T1, T2, M1],
    f2: ReactiveStream[T2, T3, M2]
): ReactiveStream[T1, T3, (M1, M2)] = {
  def _connect(
      argument: T1,
      past: (M1, M2)
  ): (T3, (M1, M2)) = {
    val (pastValueF1, pastValueF2) = past
    val f1Output = f1(argument, pastValueF1)
    val f2Output = f2(f1Output._1, pastValueF2)
    (f2Output._1, (f1Output._2, f2Output._2))
  }
  toReactiveStream(_connect)
}

def pair[T1, T2, T3, T4, M1, M2](
    f1: ReactiveStream[T1, T2, M1],
    f2: ReactiveStream[T3, T4, M2]
): ReactiveStream[(T1, T3), (T2, T4), (M1, M2)] = {
  def _pairCombinator(
      argument: (T1, T3),
      past: (M1, M2)
  ): ((T2, T4), (M1, M2)) = {
    val (pastValueF1, pastValueF2) = past
    val value1 = f1(argument._1, pastValueF1)
    val value2 = f2(argument._2, pastValueF2)
    ((value1._1, value2._1), (value1._2, value2._2))
  }
  toReactiveStream(_pairCombinator)
}

def sharedPair[T1, T2, T3, M1, M2](
    f1: ReactiveStream[T1, T2, M1],
    f2: ReactiveStream[T1, T3, M2]
): ReactiveStream[T1, (T2, T3), (M1, M2)] = {
  def _pairCombinator(
      argument: T1,
      past: (M1, M2)
  ): ((T2, T3), (M1, M2)) = {
    val (pastValueF1, pastValueF2) = past
    val value1 = f1(argument, pastValueF1)
    val value2 = f2(argument, pastValueF2)
    ((value1._1, value2._1), (value1._2, value2._2))
  }
  toReactiveStream(_pairCombinator)
}

def pair[T1, T2, M1, M2](
    f1: Source[T1, M1],
    f2: Source[T2, M2]
): Source[(T1, T2), (M1, M2)] = {
  def _pairCombinator(
      past: (M1, M2)
  ): ((T1, T2), (M1, M2)) = {
    val (pastValueF1, pastValueF2) = past
    val value1 = f1(pastValueF1)
    val value2 = f2(pastValueF2)
    ((value1._1, value2._1), (value1._2, value2._2))
  }
  toSource(_pairCombinator)
}

def latch[T1, T2](
    defaultValue: T2,
    f1: ReactiveStream[T1, Option[T2], Option[T2]]
): ReactiveStream[T1, T2, Option[T2]] = {
  def _latch(
      argument: T1,
      past: Option[T2]
  ): (T2, Option[T2]) = {
    val pastValue = past
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
  toReactiveStream(_latch)
}

def latchValue[T1](
    defaultValue: T1,
    value: Option[T1]
): Source[T1, Option[T1]] = {
  def _latch(
      past: Option[T1]
  ): (T1, Option[T1]) = {
    val pastValue = past
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
    f1: Source[Option[T1], Option[T1]]
): Source[T1, Option[T1]] = {
  def _latch(
      past: Option[T1]
  ): (T1, Option[T1]) = {
    val pastValue = past
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

def repeatPast[Output](
    input: Output
): Source[(Option[Output]), Option[Output]] = {
  def _repeatPast(
      past: Option[Output]
  ): (Option[Output], Option[Output]) = {
    (past, Some(input))
  }
  toSource(_repeatPast)
}

def anyMemory[Input, Output, Memory](
    stream: ReactiveStream[Input, Output, Option[Memory]]
): ReactiveStream[Input, Output, Option[Any]] =
  stream.mapMemory(_.map(_.asInstanceOf[Memory]), _.map(_.asInstanceOf[Any]))

def anyMemory[Output, Memory](
    source: Source[Output, Option[Memory]]
): Source[Output, Option[Any]] =
  source.mapMemory(_.map(_.asInstanceOf[Memory]), _.map(_.asInstanceOf[Any]))
