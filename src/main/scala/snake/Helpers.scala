package snake

import scala.annotation.targetName
import scala.language.implicitConversions
import scala.Predef

type ReactiveStreamFunc[Input, Output, Memory] =
  (Input, Memory) => (Output, Memory)
type SourceFunc[Output, Memory] =
  (Memory) => (Output, Memory)
type MapFunc[Input, Output] =
  (Input) => (Output)

trait Source[Output, Memory] extends SourceFunc[Output, Memory] { self =>
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

  def sourceMap[T1, MappedMemory](
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

  def withInitialMemoryAny(
      initialValue: Memory
  ): Source[Output, Option[Any]] =
    self.mapMemory(
      _ match {
        case Some(memory) => memory.asInstanceOf[Memory]
        case None         => initialValue
      },
      Some(_)
    )

  def rightChannelExtendSource[T1, M1](
      f: (Output) => Source[T1, M1]
  ): Source[(Output, T1), (Memory, M1)] =
    self.sourceMap(output => f(output).map((output, _)))

  def duplicate: Source[(Output, Output), Memory] =
    self.map(value => (value, value))

  def getSetMap[O2, P1, P2](
      get: Output => P1,
      mapF: P1 => P2,
      set: (Output, P2) => O2
  ): Source[O2, Memory] =
    self.map(t => set(t, mapF(get(t))))

  def getSetSourceMap[O2, P1, P2, M1](
      get: Output => P1,
      mapF: P1 => Source[P2, M1],
      set: (Output, P2) => O2
  ): Source[O2, (Memory, M1)] =
    self.sourceMap(t => mapF(get(t)).map(set(t, _)))
}

implicit def toSource[Output, Memory](
    f: SourceFunc[Output, Memory]
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

  def applySource[M1](
      a: Source[Input, M1]
  ): Source[Output, (Memory, M1)] = {
    def _apply(
        past: (Memory, M1)
    ): (Output, (Memory, M1)) = {
      val (memoryMain, memorySource) = past
      val valueSource = a(memorySource)
      val value = self(valueSource._1, memoryMain)
      (value._1, (value._2, valueSource._2))
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

  def sourceMap[T, MappedMemory](
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

  def withInitialMemoryAny(
      initialValue: Memory
  ): ReactiveStream[Input, Output, Option[Any]] =
    self.mapMemory(
      _ match {
        case Some(memory) => memory.asInstanceOf[Memory]
        case None         => initialValue
      },
      Some(_)
    )

  def inputMap[T1](
      mapF: (T1) => Input
  ): ReactiveStream[T1, Output, Memory] = {
    def _inputMap(
        argument: T1,
        past: Memory
    ): (Output, Memory) = {
      self(mapF(argument), past)
    }
    toReactiveStream(_inputMap)
  }

  def inputMapSource[T1, M1](
      mapF: (T1) => Source[Input, M1]
  ): ReactiveStream[T1, Output, (M1, Memory)] = {
    def _inputMap(
        argument: T1,
        past: (M1, Memory)
    ): (Output, (M1, Memory)) = {
      val (pastSource, pastMemory) = past
      val sourceOutput = mapF(argument)(pastSource)
      val output = self(sourceOutput._1, pastMemory)
      (output._1, (sourceOutput._2, output._2))
    }
    toReactiveStream(_inputMap)
  }

  def inputFlatMap[T1, T2, M1](
      mapF: (T1) => ReactiveStream[T2, Input, M1]
  ): ReactiveStream[(T1, T2), Output, (M1, Memory)] = {
    def _inputFlatMap(
        argument: (T1, T2),
        past: (M1, Memory)
    ): (Output, (M1, Memory)) = {
      val (pastMapped, pastMemory) = past
      val mappedOutput = mapF(argument._1)(argument._2, pastMapped)
      val output = self(mappedOutput._1, pastMemory)
      (output._1, (mappedOutput._2, output._2))
    }
    toReactiveStream(_inputFlatMap)
  }

  def withDefaultInput(
      default: Input
  ): ReactiveStream[Option[Input], Output, Memory] =
    self.inputMap((value: Option[Input]) => value.getOrElse(default))

  def cachedIfNoInput(): ReactiveStream[Option[Input], Output, (Memory, Output)] = {
    def _cachedIfNoInput(
        argument: Option[Input],
        past: (Memory, Output)
    ): (Output, (Memory, Output)) = {
      val (pastMemory, pastOutput) = past
      argument match {
        case None => (pastOutput, (pastMemory, pastOutput))
        case Some(argument) =>
          val output = self(argument, pastMemory)
          (output._1, (output._2, output._1))
      }
    }
    toReactiveStream(_cachedIfNoInput)
  }

  def duplicate: ReactiveStream[Input, (Output, Output), Memory] =
    self.map(value => (value, value))

  def getSetMap[O2, P1, P2](
      get: Output => P1,
      mapF: P1 => P2,
      set: (Output, P2) => O2
  ): ReactiveStream[Input, O2, Memory] =
    self.map(t => set(t, mapF(get(t))))

  def getSetSourceMap[O2, P1, P2, M1](
      get: Output => P1,
      mapF: P1 => Source[P2, M1],
      set: (Output, P2) => O2
  ): ReactiveStream[Input, O2, (Memory, M1)] =
    self.sourceMap(t => mapF(get(t)).map(set(t, _)))
}

implicit def toReactiveStream[Input, Output, Memory](
    f: ReactiveStreamFunc[Input, Output, Memory]
): ReactiveStream[Input, Output, Memory] = new ReactiveStream[Input, Output, Memory]() {
  def apply(i: Input, m: Memory) = f(i, m)
}

trait Mapping[Input, Output] extends MapFunc[Input, Output] { self =>
  def sourceMap[T1, MappedMemory](
      map: Output => Source[T1, MappedMemory]
  ): ReactiveStream[Input, T1, MappedMemory] = {
    def _flatMap(
        argument: Input,
        past: MappedMemory
    ): (T1, MappedMemory) = {
      val fOutput = self(argument)
      val mappedFOutput = map(fOutput)(past)
      (mappedFOutput._1, mappedFOutput._2)
    }
    toReactiveStream(_flatMap)
  }

  def getSetSourceMap[O2, P1, P2, M1](
      get: Output => P1,
      mapF: P1 => Source[P2, M1],
      set: (Output, P2) => O2
  ): ReactiveStream[Input, O2, M1] =
    self.sourceMap(t => mapF(get(t)).map(set(t, _)))

  def connect[O2, M1](
      f: ReactiveStream[Output, O2, M1]
  ): ReactiveStream[Input, O2, M1] =
    self.sourceMap(f.applyValue)
}

implicit def toMapping[Input, Output](
    f: MapFunc[Input, Output]
): Mapping[Input, Output] = new Mapping[Input, Output]() {
  def apply(i: Input) = f(i)
}

def identityMapping[Output] = toMapping(identity[Output])

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

def isEqualToPast[T1 <: Equals, Memory](
    f: Source[T1, Memory]
): Source[(T1, Boolean), (Memory, T1)] =
  f.getSetSourceMap(
    identity,
    value => repeatPast(value).map(_ == value),
    (_, _)
  )

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

def pair[I1, I2, O1, O2, M](
    fMap: Mapping[I2, O2],
    stream: ReactiveStream[I1, O1, M]
): ReactiveStream[(I2, I1), (O2, O1), M] = {
  def _pairCombinator(
      argument: (I2, I1),
      past: M
  ): ((O2, O1), M) = {
    val value = stream(argument._2, past)
    val valueFMap = fMap(argument._1)
    ((valueFMap, value._1), value._2)
  }
  toReactiveStream(_pairCombinator)
}

def pair[I1, I2, O1, O2, M](
    stream: ReactiveStream[I1, O1, M],
    fMap: Mapping[I2, O2]
): ReactiveStream[(I1, I2), (O1, O2), M] = {
  def _pairCombinator(
      argument: (I1, I2),
      past: M
  ): ((O1, O2), M) = {
    val value = stream(argument._1, past)
    val valueFMap = fMap(argument._2)
    ((value._1, valueFMap), value._2)
  }
  toReactiveStream(_pairCombinator)
}

def pair[I1, O1, O2, M, M2](
    source: Source[O2, M2],
    stream: ReactiveStream[I1, O1, M]
): ReactiveStream[I1, (O2, O1), (M2, M)] = {
  def _pairCombinator(
      argument: I1,
      past: (M2, M)
  ): ((O2, O1), (M2, M)) = {
    val value = stream(argument, past._2)
    val valueFMap = source(past._1)
    ((valueFMap._1, value._1), (valueFMap._2, value._2))
  }
  toReactiveStream(_pairCombinator)
}

def pair[I1, O1, O2, M, M2](
    stream: ReactiveStream[I1, O1, M],
    source: Source[O2, M2]
): ReactiveStream[I1, (O1, O2), (M, M2)] = {
  def _pairCombinator(
      argument: I1,
      past: (M, M2)
  ): ((O1, O2), (M, M2)) = {
    val value = stream(argument, past._1)
    val valueFMap = source(past._2)
    ((value._1, valueFMap._1), (value._2, valueFMap._2))
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

def mergeInput[I, O, M](
    stream: ReactiveStream[(I, I), O, M]
): ReactiveStream[I, O, M] =
  stream.inputMap(value => (value, value))

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

def repeatPast[Output](
    input: Output
): Source[Output, Output] = {
  def _repeatPast(
      past: Output
  ): (Output, Output) = {
    (past, input)
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

def withDefaultOutput[Input, Output, Memory](
    default: Output,
    f: ReactiveStream[Input, Option[Output], Memory]
): ReactiveStream[Input, Output, Memory] =
  f.map((value: Option[Output]) => value.getOrElse(default))

def flattenSource[Output, Memory, M1](
    f: Memory => Source[(Output, Memory), M1]
): Source[Output, (Memory, M1)] = {
  def _flattenSource(
      past: (Memory, M1)
  ): (Output, (Memory, M1)) = {
    val source = f(past._1)
    val output = source(past._2)
    (output._1._1, (output._1._2, output._2))
  }
  toSource(_flattenSource)
}
