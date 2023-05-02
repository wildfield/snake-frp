package snake

import scala.annotation.targetName
import scala.language.implicitConversions

type ReactiveStreamAny[Input, Output, Memory] =
  (Input, Memory) => (Output, Memory)
type SourceAny[Output, Memory] =
  (Memory) => (Output, Memory)

type OldSource[Memory, Output] =
  (Option[Memory]) => (Output, (Option[Memory]))

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
      map: Output => Reactive[T1, T2, MappedMemory]
  ): Reactive[T1, T2, (Memory, MappedMemory)] = {
    def _flatMap(
        argument: T1,
        past: (Memory, MappedMemory)
    ): (T2, (Memory, MappedMemory)) = {
      val (pastValueF, pastValueMapped) = past
      val fOutput = self(pastValueF)
      val mappedFOutput = map(fOutput._1)(argument, pastValueMapped)
      (mappedFOutput._1, (fOutput._2, mappedFOutput._2))
    }
    toReactive(_flatMap)
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

  def rightChannelExtendSource[T1](
      f: (Output) => Source[T1]
  ): Source[(Output, T1)] =
    self.flatMapSource(output => f(output).map((output, _)))
}

implicit def toSource[Output, Memory](
    f: SourceAny[Output, Memory]
): Source[Output, Memory] = new Source[Output, Memory]() {
  def apply(m: Memory) = f(m)
}

trait Reactive[Input, Output, Memory] extends ReactiveStreamAny[Input, Output, Memory] { self =>
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
  ): Reactive[Input, T, Memory] = {
    def _map(argument: Input, past: Memory): (T, Memory) = {
      val value = self(argument, past)
      (mapFunc(value._1), value._2)
    }
    toReactive(_map)
  }

  def flatMap[T1, T2, MappedMemory](
      map: Output => Reactive[T1, T2, MappedMemory]
  ): Reactive[(Input, T1), T2, (Memory, MappedMemory)] = {
    def _flatMap(
        argument: (Input, T1),
        past: (Memory, MappedMemory)
    ): (T2, (Memory, MappedMemory)) = {
      val (pastValueF, pastValueMapped) = past
      val fOutput = self(argument._1, pastValueF)
      val mappedFOutput = map(fOutput._1)(argument._2, pastValueMapped)
      (mappedFOutput._1, (fOutput._2, mappedFOutput._2))
    }
    toReactive(_flatMap)
  }

  def flatMapSource[T, MappedMemory](
      map: Output => Source[T, MappedMemory]
  ): Reactive[Input, T, (Memory, MappedMemory)] = {
    def _flatMap(
        argument: Input,
        past: (Memory, MappedMemory)
    ): (T, (Memory, MappedMemory)) = {
      val (pastValueF, pastValueMapped) = past
      val fOutput = self(argument, pastValueF)
      val mappedFOutput = map(fOutput._1)(pastValueMapped)
      (mappedFOutput._1, (fOutput._2, mappedFOutput._2))
    }
    toReactive(_flatMapSource)
  }

  def ignoreInput[T](
  ): Reactive[(T, Input), Output, Memory] = {
    def _ignoreInput(
        argument: (T, Input),
        past: Memory
    ): (Output, Memory) = {
      self(argument._2, past)
    }
    toReactive(_ignoreInput)
  }

  def withPastOutput(
  ): Reactive[Input, (Option[Output], Output), (Option[Output], Memory)] = {
    def _withPastOutput(
        argument: Input,
        past: (Option[Output], Memory)
    ): ((Option[Output], Output), (Option[Output], Memory)) = {
      val (pastOutput, pastFValue) = past
      val output = self(argument, pastFValue)
      ((pastOutput, output._1), (Some(output._1), output._2))
    }
    toReactive(_withPastOutput)
  }

  def inputMap[T1](
      mapF: (T1) => Input
  ): Reactive[T1, Output] = {
    def _inputMap(
        argument: T1,
        past: Memory
    ): (Output, Memory) = {
      self(mapF(argument), past)
    }
    toReactive(_inputMap)
  }

  def inputMapSource[T1](
      mapF: (T1) => Source[Input]
  ): Reactive[T1, Output] = {
    def _inputMap(
        argument: T1,
        past: Memory
    ): (Output, Memory) = {
      val (pastSource: Memory, pastMemory: Memory) =
        past.flatMap(_.asInstanceOf[Option[(Memory, Memory)]]) match {
          case None                            => (None, None)
          case Some((sourceValue, pastMemory)) => (sourceValue, pastMemory)
        }
      val sourceOutput = mapF(argument)(pastSource)
      val output = self(sourceOutput._1, pastMemory)
      (output._1, Some(sourceOutput._2, output._2))
    }
    toReactive(_inputMap)
  }

  def inputFlatMap[T1, T2](
      mapF: (T1) => Reactive[T2, Input]
  ): Reactive[(T1, T2), Output] = {
    def _inputFlatMap(
        argument: (T1, T2),
        past: Memory
    ): (Output, Memory) = {
      val (pastMapped: Memory, pastMemory: Memory) =
        past.flatMap(_.asInstanceOf[Option[(Memory, Memory)]]) match {
          case None                           => (None, None)
          case Some((pastMapped, pastMemory)) => (pastMapped, pastMemory)
        }
      val mappedOutput = mapF(argument._1)(argument._2, pastMapped)
      val output = self(mappedOutput._1, pastMemory)
      (output._1, Some(mappedOutput._2, output._2))
    }
    toReactive(_inputFlatMap)
  }

  def withDefaultInput(
      default: Input
  ): Reactive[Option[Input], Output] =
    self.inputMap((value: Option[Input]) => value.getOrElse(default))

  def cachedIfNoInput(): Reactive[Option[Input], Option[Output]] = {
    def _cachedIfNoInput(
        argument: Option[Input],
        past: Memory
    ): (Option[Output], Memory) = {
      val (pastOutput: Option[Output], pastMemory: Option[Any]) =
        past.map(_.asInstanceOf[(Option[Output], Option[Any])]) match {
          case None                 => (None, None)
          case Some(value1, value2) => (value1, value2)
        }
      argument match {
        case None => (pastOutput, Some(pastOutput, pastMemory))
        case Some(argument) =>
          val output = self(argument, pastMemory)
          (Some(output._1), Some(Some(output._1), output._2))
      }
    }
    toReactive(_cachedIfNoInput)
  }

  def cachedChannel(): Reactive[(Boolean, Input), Option[Output]] = {
    def _cachedChannel(
        argument: (Boolean, Input),
        past: Memory
    ): (Option[Output], Memory) = {
      val (pastOutput: Option[Output], pastMemory: Option[Any]) =
        past.map(_.asInstanceOf[(Output, Option[Any])]) match {
          case None                 => (None, None)
          case Some(value1, value2) => (Some(value1), value2)
        }
      val (shouldReturnCached, input) = argument
      if (shouldReturnCached) {
        (pastOutput, Some(pastOutput, pastMemory))
      } else {
        val output = self(input, pastMemory)
        (Some(output._1), Some(output._1, output._2))
      }
    }
    toReactive(_cachedChannel)
  }

  def cachedChannelIfCached(): Reactive[(Boolean, Input), Output] = {
    def _cachedChannel(
        argument: (Boolean, Input),
        past: Memory
    ): (Output, Memory) = {
      val (pastOutput: Option[Output], pastMemory: Option[Any]) =
        past.map(_.asInstanceOf[(Output, Option[Any])]) match {
          case None                 => (None, None)
          case Some(value1, value2) => (Some(value1), value2)
        }
      val (shouldReturnCached, input) = argument
      (shouldReturnCached, pastOutput) match {
        case (true, Some(pastOutput)) => (pastOutput, Some(pastOutput, pastMemory))
        case _ => {
          val output = self(input, pastMemory)
          (output._1, Some(output._1, output._2))
        }
      }
    }
    toReactive(_cachedChannel)
  }

  def leftChannelExtendSource[T1](
      f: (Output) => Source[T1]
  ): Reactive[Input, (T1, Output)] =
    self.flatMapSource(output => f(output).map((_, output)))

  def leftChannelExtend[T1](
      f: (Output) => T1
  ): Reactive[Input, (T1, Output)] =
    self.map(output => (f(output), output))

  def leftChannelMap[T1, T2](
      mapF: T1 => T2
  ): Reactive[(T1, Input), (T2, Output)] = {
    def _leftChannelMap(
        argument: (T1, Input),
        past: Memory
    ): ((T2, Output), Memory) = {
      val output = self(argument._2, past)
      ((mapF(argument._1), output._1), Some(output._2))
    }
    toReactive(_leftChannelMap)
  }

  def rightChannelExtendSource[T1](
      f: (Output) => Source[T1]
  ): Reactive[Input, (Output, T1)] =
    self.flatMapSource(output => f(output).map((output, _)))

  def rightChannelExtend[T1](
      f: (Output) => T1
  ): Reactive[Input, (Output, T1)] =
    self.map(output => (output, f(output)))

  def assumeLeftInput[T1]: Reactive[(T1, Input), (T1, Output)] =
    assumeIdentity[T1].flatMap(input => self.map((input, _)))

  def assumeRightInput[T1]: Reactive[(Input, T1), (Output, T1)] =
    assumeIdentity[T1]
      .flatMap(input => self.map((_, input)))
      .inputMap { case (t1, input) => (input, t1) }

  def connectLeft[T1, T2](
      f: Reactive[T1, T2]
  ): Reactive[(T1, Input), (T2, Output)] =
    pair(f, self)

  def connectLeftSource[T1](
      f: Source[T1]
  ): Reactive[Input, (T1, Output)] =
    self.flatMapSource(output => f.map((_, output)))

  def connectRight[T1, T2](
      f: Reactive[T1, T2]
  ): Reactive[(Input, T1), (Output, T2)] =
    pair(self, f)

  def connectRightSource[T1](
      f: Source[T1]
  ): Reactive[Input, (Output, T1)] =
    self.flatMapSource(output => f.map((output, _)))
}

implicit def toReactive[Input, Output, Memory](
    f: ReactiveStreamAny[Input, Output, Memory]
): Reactive[Input, Output, Memory] = new Reactive[Input, Output, Memory]() {
  def apply(i: Input, m: Memory) = f(i, m)
}

def flatten[T1, T2, T3, M1, M2](
    f: Reactive[T1, Reactive[T2, T3, M2], M1]
): Reactive[(T1, T2), T3, (M1, M2)] = {
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
    f: Reactive[T1, T2, Memory]
): Reactive[T1, T2, (Option[T1], Option[T2], Memory)] = {
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
  toReactive(_cached)
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
    f: Reactive[(Option[T2], T1), T2, M1]
): Reactive[T1, T2, (Option[T2], M1)] = {
  def _feedback(
      argument: T1,
      past: (Option[T2], M1)
  ): (T2, (Option[T2], M1)) = {
    val (pastOutput, pastFValue) = past
    val output = f((pastOutput, argument), pastFValue)
    (output._1, (Some(output._1), output._2))
  }
  toReactive(_feedback)
}

def feedbackSource[T2, Memory](
    f: Reactive[Option[T2], T2, Memory]
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
    f: Reactive[(Option[T2], T1), (T2, T3), Memory]
): Reactive[T1, T3, (Option[T2], Memory)] = {
  def _feedbackSource(
      argument: T1,
      past: (Option[T2], Memory)
  ): (T3, (Option[T2], Memory)) = {
    val (pastOutput, pastFValue) = past
    val output = f((pastOutput, argument), pastFValue)
    (output._1._2, (Some(output._1._1), output._2))
  }
  toReactive(_feedbackSource)
}

def feedbackChannelSource[T2, T3, Memory](
    f: Reactive[Option[T2], (T2, T3), Memory]
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
    f: T1 => Reactive[T2, T3, Memory]
): Reactive[(T1, T2), T3, Memory] = {
  def _assume(
      argument: (T1, T2),
      past: Memory
  ): (T3, Memory) = {
    f(argument._1)(argument._2, past)
  }
  toReactive(_assume)
}

def assumeInputSource[T1, T2, Memory](
    f: T1 => Source[T2, Memory]
): Reactive[T1, T2, Memory] = {
  def _assumeSource(
      argument: T1,
      past: Memory
  ): (T2, Memory) = {
    f(argument)(past)
  }
  toReactive(_assumeSource)
}

def applyPartial[T1, T2, T3, Memory](
    f1: Reactive[(T1, T2), T3, Memory],
    a: T1
): Reactive[T2, T3, Memory] = {
  def _apply(
      argument: T2,
      past: Memory
  ): (T3, Memory) = {
    f1((a, argument), past)
  }
  toReactive(_apply)
}

def applyPartial2[T1, T2, T3, Memory](
    f1: Reactive[(T1, T2), T3, Memory],
    a: T2
): Reactive[T1, T3, Memory] = {
  def _apply(
      argument: T1,
      past: Memory
  ): (T3, Memory) = {
    val value1 = f1((argument, a), past)
    value1
  }
  toReactive(_apply)
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

def identity[T1](): Reactive[T1, T1, Unit] = {
  def _identity(
      argument: T1,
      past: Unit
  ): (T1, Unit) = {
    (argument, past)
  }
  toReactive(_identity)
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
    f1: Reactive[T1, T2, M1],
    f2: Reactive[T2, T3, M2]
): Reactive[T1, T3, (M1, M2)] = {
  def _connect(
      argument: T1,
      past: (M1, M2)
  ): (T3, (M1, M2)) = {
    val (pastValueF1, pastValueF2) = past
    val f1Output = f1(argument, pastValueF1)
    val f2Output = f2(f1Output._1, pastValueF2)
    (f2Output._1, (f1Output._2, f2Output._2))
  }
  toReactive(_connect)
}

def pair[T1, T2, T3, T4, M1, M2](
    f1: Reactive[T1, T2, M1],
    f2: Reactive[T3, T4, M2]
): Reactive[(T1, T3), (T2, T4), (M1, M2)] = {
  def _pairCombinator(
      argument: (T1, T3),
      past: (M1, M2)
  ): ((T2, T4), (M1, M2)) = {
    val (pastValueF1, pastValueF2) = past
    val value1 = f1(argument._1, pastValueF1)
    val value2 = f2(argument._2, pastValueF2)
    ((value1._1, value2._1), (value1._2, value2._2))
  }
  toReactive(_pairCombinator)
}

def sharedPair[T1, T2, T3, M1, M2](
    f1: Reactive[T1, T2, M1],
    f2: Reactive[T1, T3, M2]
): Reactive[T1, (T2, T3), (M1, M2)] = {
  def _pairCombinator(
      argument: T1,
      past: (M1, M2)
  ): ((T2, T3), (M1, M2)) = {
    val (pastValueF1, pastValueF2) = past
    val value1 = f1(argument, pastValueF1)
    val value2 = f2(argument, pastValueF2)
    ((value1._1, value2._1), (value1._2, value2._2))
  }
  toReactive(_pairCombinator)
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
    f1: Reactive[T1, Option[T2], Option[T2]]
): Reactive[T1, T2, Option[T2]] = {
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
  toReactive(_latch)
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

def repeatPast[Output, Memory](
    input: Output
): Source[(Option[Output]), (Option[Output], Memory)] = {
  def _repeatPast(
      past: (Option[Output], Memory)
  ): (Option[Output], (Option[Output], Memory)) = {
    val (pastOutput, pastFValue) = past
    (pastOutput, (Some(input), pastFValue))
  }
  toSource(_repeatPast)
}
