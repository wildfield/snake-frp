package snake

import scala.annotation.targetName
import scala.language.implicitConversions
import scala.Predef
import scala.NonEmptyTuple
import scala.util.NotGiven

type ReactiveStreamFunc[Input, Output, Memory] =
  (Input, Memory) => (Output, Memory)
type SourceFunc[Output, Memory] = ReactiveStreamFunc[Unit, Output, Memory]

extension [Output, Memory](s: ReactiveStreamFunc[Unit, Output, Memory])
  def apply(past: Memory): (Output, Memory) = s.apply((), past)

extension [Input, Output, Memory](f: Input => SourceFunc[Output, Memory])
  def toStream: ReactiveStreamFunc[Input, Output, Memory] =
    (input: Input, mem: Memory) => f(input)(mem)

extension [Input, Output](s: Input => Output)
  def applyStream[I1, M2](
      argumentStream: ReactiveStreamFunc[I1, Input, M2]
  ): ReactiveStreamFunc[I1, Output, M2] =
    (input: I1, past: M2) =>
      val argument = argumentStream(input, past)
      val output = s(argument(0))
      (output, argument(1))

extension [Input, Output, Memory](s: ReactiveStreamFunc[Input, Output, Memory])
  def applyInput(argument: Input): SourceFunc[Output, Memory] = (_: Unit, past: Memory) =>
    s(argument, past)

  def applyStream[I1, M2, M3](
      argumentStream: ReactiveStreamFunc[I1, Input, M2],
      pack: (M2, Memory) => M3,
      unpack: M3 => (M2, Memory)
  ): ReactiveStreamFunc[I1, Output, M3] =
    (input: I1, pastPacked: M3) =>
      val past = unpack(pastPacked)
      val argument = argumentStream(input, past(0))
      val output = s(argument(0), past(1))
      (output(0), pack(argument(1), output(1)))

  def applyStreamTupled[I1, M2](
      argumentStream: ReactiveStreamFunc[I1, Input, M2]
  ): ReactiveStreamFunc[I1, Output, (M2, Memory)] =
    s.applyStream(argumentStream, (_, _), identity)

  def memoryMap[M1](inF: M1 => Memory, outF: Memory => M1) =
    (input: Input, past: M1) =>
      val output = s(input, inF(past))
      (output(0), outF(output(1)))

  def feedback[I, O, S](
      store: (Output, Memory) => (O, S),
      read: (I, S) => (Input, Memory)
  ): ReactiveStreamFunc[I, O, S] =
    (input: I, mem: S) => store.tupled(s.tupled(read(input, mem)))

  def map[O2](f: Output => O2): ReactiveStreamFunc[Input, O2, Memory] =
    (input: Input, mem: Memory) =>
      val output = s(input, mem)
      (f(output(0)), output(1))

  def initializeMemory(
      initialValue: Memory
  ): ReactiveStreamFunc[Input, Output, Option[Memory]] =
    s.memoryMap(
      _ match {
        case Some(memory) => memory
        case None         => initialValue
      },
      Some(_)
    )

extension [I0, O0, M0, I1, O1, M1](
    t: Tuple2[
      ReactiveStreamFunc[I0, O0, M0],
      ReactiveStreamFunc[I1, O1, M1]
    ]
)
  def toStream: ReactiveStreamFunc[(I0, I1), (O0, O1), (M0, M1)] =
    (input: (I0, I1), mem: (M0, M1)) =>
      val v0 = t(0)(input(0), mem(0))
      val v1 = t(1)(input(1), mem(1))
      ((v0(0), v1(0)), (v0(1), v1(1)))

extension [I, O0, M0, O1, M1](
    t: Tuple2[
      ReactiveStreamFunc[I, O0, M0],
      ReactiveStreamFunc[I, O1, M1]
    ]
)
  def toMergedStream: ReactiveStreamFunc[I, (O0, O1), (M0, M1)] =
    (input: I, mem: (M0, M1)) =>
      val v0 = t(0)(input, mem(0))
      val v1 = t(1)(input, mem(1))
      ((v0(0), v1(0)), (v0(1), v1(1)))

extension [I0, O0, M0, I1, O1, M1, I2, O2, M2](
    t: Tuple3[
      ReactiveStreamFunc[I0, O0, M0],
      ReactiveStreamFunc[I1, O1, M1],
      ReactiveStreamFunc[I2, O2, M2],
    ]
)
  def toStream: ReactiveStreamFunc[(I0, I1, I2), (O0, O1, O2), (M0, M1, M2)] =
    (input: (I0, I1, I2), mem: (M0, M1, M2)) =>
      val v0 = t(0)(input(0), mem(0))
      val v1 = t(1)(input(1), mem(1))
      val v2 = t(2)(input(2), mem(2))
      ((v0(0), v1(0), v2(0)), (v0(1), v1(1), v2(1)))

extension [I0, O0, M0, I1, O1, M1, I2, O2, M2, I3, O3, M3](
    t: Tuple4[
      ReactiveStreamFunc[I0, O0, M0],
      ReactiveStreamFunc[I1, O1, M1],
      ReactiveStreamFunc[I2, O2, M2],
      ReactiveStreamFunc[I3, O3, M3],
    ]
)
  def toStream: ReactiveStreamFunc[(I0, I1, I2, I3), (O0, O1, O2, O3), (M0, M1, M2, M3)] =
    (input: (I0, I1, I2, I3), mem: (M0, M1, M2, M3)) =>
      val v0 = t(0)(input(0), mem(0))
      val v1 = t(1)(input(1), mem(1))
      val v2 = t(2)(input(2), mem(2))
      val v3 = t(3)(input(3), mem(3))
      ((v0(0), v1(0), v2(0), v3(0)), (v0(1), v1(1), v2(1), v3(1)))

extension [I, O0, M0, O1, M1, O2, M2, O3, M3](
    t: Tuple4[
      ReactiveStreamFunc[I, O0, M0],
      ReactiveStreamFunc[I, O1, M1],
      ReactiveStreamFunc[I, O2, M2],
      ReactiveStreamFunc[I, O3, M3],
    ]
)
  def toMergedStream: ReactiveStreamFunc[I, (O0, O1, O2, O3), (M0, M1, M2, M3)] =
    (input: I, mem: (M0, M1, M2, M3)) =>
      val v0 = t(0)(input, mem(0))
      val v1 = t(1)(input, mem(1))
      val v2 = t(2)(input, mem(2))
      val v3 = t(3)(input, mem(3))
      ((v0(0), v1(0), v2(0), v3(0)), (v0(1), v1(1), v2(1), v3(1)))

extension [I, O0, M0, O1, M1, O2, M2](
    t: Tuple3[
      ReactiveStreamFunc[I, O0, M0],
      ReactiveStreamFunc[I, O1, M1],
      ReactiveStreamFunc[I, O2, M2],
    ]
)
  def toMergedStream: ReactiveStreamFunc[I, (O0, O1, O2), (M0, M1, M2)] =
    (input: I, mem: (M0, M1, M2)) =>
      val v0 = t(0)(input, mem(0))
      val v1 = t(1)(input, mem(1))
      val v2 = t(2)(input, mem(2))
      ((v0(0), v1(0), v2(0)), (v0(1), v1(1), v2(1)))

extension [Input, Output, Memory <: Tuple](s: ReactiveStreamFunc[Input, Output, Memory])
  def applyStreamPrepend[I1, M2](
      argumentStream: ReactiveStreamFunc[I1, Input, M2]
  ): ReactiveStreamFunc[I1, Output, M2 *: Memory] =
    s.applyStream(argumentStream, _ *: _, { case x *: xs => (x, xs) })

extension [Input, Output, Tup <: Tuple, Memory](
    s: ReactiveStreamFunc[Input, Output, Memory]
)(using Memory =:= Option[Tup])
  def applyStreamOptionPrepend[I1, M2](
      argumentStream: ReactiveStreamFunc[I1, Input, Option[M2]]
  ): ReactiveStreamFunc[I1, Output, Option[M2 *: Tup]] =
    s.applyStream(
      argumentStream,
      (m2, m) => m2.flatMap((m2: M2) => m.map((m: Tup) => (m2 *: m))),
      (m: Option[M2 *: Tup]) =>
        m match {
          case Some(m2 *: m) => (Some(m2), Option(m).asInstanceOf[Memory])
          case None          => (None, Option(None).asInstanceOf[Memory])
        }
    )

// trait Source[Output, Memory] extends SourceFunc[Output, Memory] { self =>
//   def map[T](
//       mapFunc: Output => T
//   ): Source[T, Memory] = {
//     def _map(past: Memory): (T, Memory) = {
//       val value = self(past)
//       (mapFunc(value._1), value._2)
//     }
//     toSource(_map)
//   }

//   def flatMap[T1, T2, MappedMemory](
//       map: Output => ReactiveStream[T1, T2, MappedMemory]
//   ): ReactiveStream[T1, T2, (Memory, MappedMemory)] = {
//     def _flatMap(
//         argument: T1,
//         past: (Memory, MappedMemory)
//     ): (T2, (Memory, MappedMemory)) = {
//       val (pastValueF, pastValueMapped) = past
//       val fOutput = self(pastValueF)
//       val mappedFOutput = map(fOutput._1)(argument, pastValueMapped)
//       (mappedFOutput._1, (fOutput._2, mappedFOutput._2))
//     }
//     toReactiveStream(_flatMap)
//   }

//   def sourceMap[T1, MappedMemory](
//       map: Output => Source[T1, MappedMemory]
//   ): Source[T1, (Memory, MappedMemory)] = {
//     def _flatMap(
//         past: (Memory, MappedMemory)
//     ): (T1, (Memory, MappedMemory)) = {
//       val (pastValueF, pastValueMapped) = past
//       val fOutput = self(pastValueF)
//       val mappedFOutput = map(fOutput._1)(pastValueMapped)
//       (mappedFOutput._1, (fOutput._2, mappedFOutput._2))
//     }
//     toSource(_flatMap)
//   }

//   def mapMemory[M1](
//       inF: M1 => Memory,
//       outF: Memory => M1
//   ): Source[Output, M1] = {
//     def _mapMemory(
//         past: M1
//     ): (Output, M1) = {
//       val output = self(inF(past))
//       (output._1, outF(output._2))
//     }
//     toSource(_mapMemory)
//   }

// def withInitialMemory(
//     initialValue: Memory
// ): Source[Output, Option[Memory]] =
//   self.mapMemory(
//     _ match {
//       case Some(memory) => memory
//       case None         => initialValue
//     },
//     Some(_)
//   )

//   def withInitialMemoryAny(
//       initialValue: Memory
//   ): Source[Output, Option[Any]] =
//     self.mapMemory(
//       _ match {
//         case Some(memory) => memory.asInstanceOf[Memory]
//         case None         => initialValue
//       },
//       Some(_)
//     )

//   def partial[O2, P1, P2](
//       get: Output => P1,
//       mapF: P1 => P2,
//       set: (Output, P2) => O2
//   ): Source[O2, Memory] =
//     self.map(t => set(t, mapF(get(t))))

//   def partialSource[O2, P1, P2, M1](
//       get: Output => P1,
//       mapF: P1 => Source[P2, M1],
//       set: (Output, P2) => O2
//   ): Source[O2, (Memory, M1)] =
//     self.sourceMap(t => mapF(get(t)).map(set(t, _)))
// }

// implicit def toSource[Output, Memory](
//     f: SourceFunc[Output, Memory]
// ): Source[Output, Memory] = new Source[Output, Memory]() {
//   def apply(m: Memory) = f(m)
// }

// trait ReactiveStream[Input, Output, Memory] extends ReactiveStreamFunc[Input, Output, Memory] {
//   self =>
//   def applyValue(
//       a: Input
//   ): Source[Output, Memory] = {
//     def _apply(
//         past: Memory
//     ): (Output, Memory) =
//       self(a, past)
//     toSource(_apply)
//   }

//   def applySource[M1](
//       a: Source[Input, M1]
//   ): Source[Output, (Memory, M1)] = {
//     def _apply(
//         past: (Memory, M1)
//     ): (Output, (Memory, M1)) = {
//       val (memoryMain, memorySource) = past
//       val valueSource = a(memorySource)
//       val value = self(valueSource._1, memoryMain)
//       (value._1, (value._2, valueSource._2))
//     }
//     toSource(_apply)
//   }

//   def map[T](
//       mapFunc: Output => T
//   ): ReactiveStream[Input, T, Memory] = {
//     def _map(argument: Input, past: Memory): (T, Memory) = {
//       val value = self(argument, past)
//       (mapFunc(value._1), value._2)
//     }
//     toReactiveStream(_map)
//   }

//   def sourceMap[T, MappedMemory](
//       map: Output => Source[T, MappedMemory]
//   ): ReactiveStream[Input, T, (Memory, MappedMemory)] = {
//     def _flatMap(
//         argument: Input,
//         past: (Memory, MappedMemory)
//     ): (T, (Memory, MappedMemory)) = {
//       val (pastValueF, pastValueMapped) = past
//       val fOutput = self(argument, pastValueF)
//       val mappedFOutput = map(fOutput._1)(pastValueMapped)
//       (mappedFOutput._1, (fOutput._2, mappedFOutput._2))
//     }
//     toReactiveStream(_flatMap)
//   }

//   def mapMemory[M1](
//       inF: M1 => Memory,
//       outF: Memory => M1
//   ): ReactiveStream[Input, Output, M1] = {
//     def _mapMemory(
//         argument: Input,
//         past: M1
//     ): (Output, M1) = {
//       val output = self(argument, inF(past))
//       (output._1, outF(output._2))
//     }
//     toReactiveStream(_mapMemory)
//   }

//   def withInitialMemory(
//       initialValue: Memory
//   ): ReactiveStream[Input, Output, Option[Memory]] =
//     self.mapMemory(
//       _ match {
//         case Some(memory) => memory
//         case None         => initialValue
//       },
//       Some(_)
//     )

//   def withInitialMemoryAny(
//       initialValue: Memory
//   ): ReactiveStream[Input, Output, Option[Any]] =
//     self.mapMemory(
//       _ match {
//         case Some(memory) => memory.asInstanceOf[Memory]
//         case None         => initialValue
//       },
//       Some(_)
//     )

//   def inputMap[T1](
//       mapF: (T1) => Input
//   ): ReactiveStream[T1, Output, Memory] = {
//     def _inputMap(
//         argument: T1,
//         past: Memory
//     ): (Output, Memory) = {
//       self(mapF(argument), past)
//     }
//     toReactiveStream(_inputMap)
//   }

//   def inputSourceMap[T1, M1](
//       mapF: (T1) => Source[Input, M1]
//   ): ReactiveStream[T1, Output, (M1, Memory)] = {
//     def _inputMap(
//         argument: T1,
//         past: (M1, Memory)
//     ): (Output, (M1, Memory)) = {
//       val (pastSource, pastMemory) = past
//       val sourceOutput = mapF(argument)(pastSource)
//       val output = self(sourceOutput._1, pastMemory)
//       (output._1, (sourceOutput._2, output._2))
//     }
//     toReactiveStream(_inputMap)
//   }

//   def inputFlatMap[T1, T2, M1](
//       mapF: (T1) => ReactiveStream[T2, Input, M1]
//   ): ReactiveStream[(T1, T2), Output, (M1, Memory)] = {
//     def _inputFlatMap(
//         argument: (T1, T2),
//         past: (M1, Memory)
//     ): (Output, (M1, Memory)) = {
//       val (pastMapped, pastMemory) = past
//       val mappedOutput = mapF(argument._1)(argument._2, pastMapped)
//       val output = self(mappedOutput._1, pastMemory)
//       (output._1, (mappedOutput._2, output._2))
//     }
//     toReactiveStream(_inputFlatMap)
//   }

//   def withDefaultInput(
//       default: Input
//   ): ReactiveStream[Option[Input], Output, Memory] =
//     self.inputMap((value: Option[Input]) => value.getOrElse(default))

//   def cachedIfNoInput(): ReactiveStream[Option[Input], Output, (Memory, Output)] = {
//     def _cachedIfNoInput(
//         argument: Option[Input],
//         past: (Memory, Output)
//     ): (Output, (Memory, Output)) = {
//       val (pastMemory, pastOutput) = past
//       argument match {
//         case None => (pastOutput, (pastMemory, pastOutput))
//         case Some(argument) =>
//           val output = self(argument, pastMemory)
//           (output._1, (output._2, output._1))
//       }
//     }
//     toReactiveStream(_cachedIfNoInput)
//   }

//   def partial[O2, P1, P2](
//       get: Output => P1,
//       mapF: P1 => P2,
//       set: (Output, P2) => O2
//   ): ReactiveStream[Input, O2, Memory] =
//     self.map(t => set(t, mapF(get(t))))

//   def partialSource[O2, P1, P2, M1](
//       get: Output => P1,
//       mapF: P1 => Source[P2, M1],
//       set: (Output, P2) => O2
//   ): ReactiveStream[Input, O2, (Memory, M1)] =
//     self.sourceMap(t => mapF(get(t)).map(set(t, _)))

//   def connect[T3, M2](
//       f2: ReactiveStream[Output, T3, M2]
//   ): ReactiveStream[Input, T3, (Memory, M2)] = {
//     def _connect(
//         argument: Input,
//         past: (Memory, M2)
//     ): (T3, (Memory, M2)) = {
//       val (pastValueF1, pastValueF2) = past
//       val f1Output = self(argument, pastValueF1)
//       val f2Output = f2(f1Output._1, pastValueF2)
//       (f2Output._1, (f1Output._2, f2Output._2))
//     }
//     toReactiveStream(_connect)
//   }

//   def bypass[O2, Intermediate](
//       mapF: (Input, Output) => Intermediate,
//       combine: (Output, Intermediate) => O2
//   ): ReactiveStream[Input, O2, Memory] =
//     identityMapping[Input]
//       .sourceMap(input =>
//         self
//           .applyValue(input)
//           .map(output => (output, mapF(input, output)))
//           .map(combine.tupled)
//       )

//   def sourceBypass[O2, Intermediate, M1](
//       mapF: Input => Source[Intermediate, M1],
//       combine: (Output, Intermediate) => O2
//   ): ReactiveStream[Input, O2, (Memory, M1)] =
//     identityMapping[Input]
//       .sourceMap(input =>
//         self
//           .applyValue(input)
//           .sourceMap(output =>
//             mapF(input)
//               .map(combine(output, _))
//           )
//       )
// }

// implicit def toReactiveStream[Input, Output, Memory](
//     f: ReactiveStreamFunc[Input, Output, Memory]
// ): ReactiveStream[Input, Output, Memory] = new ReactiveStream[Input, Output, Memory]() {
//   def apply(i: Input, m: Memory) = f(i, m)
// }

// trait Mapping[Input, Output] extends MapFunc[Input, Output] { self =>
//   def sourceMap[T1, MappedMemory](
//       map: Output => Source[T1, MappedMemory]
//   ): ReactiveStream[Input, T1, MappedMemory] = {
//     def _flatMap(
//         argument: Input,
//         past: MappedMemory
//     ): (T1, MappedMemory) = {
//       val fOutput = self(argument)
//       val mappedFOutput = map(fOutput)(past)
//       (mappedFOutput._1, mappedFOutput._2)
//     }
//     toReactiveStream(_flatMap)
//   }

//   def partialSource[O2, P1, P2, M1](
//       get: Output => P1,
//       mapF: P1 => Source[P2, M1],
//       set: (Output, P2) => O2
//   ): ReactiveStream[Input, O2, M1] =
//     self.sourceMap(t => mapF(get(t)).map(set(t, _)))

//   def connect[O2, M1](
//       f: ReactiveStream[Output, O2, M1]
//   ): ReactiveStream[Input, O2, M1] =
//     self.sourceMap(f.applyValue)

//   def sourceBypass[O2, Intermediate, M1](
//       mapF: Input => Source[Intermediate, M1],
//       combine: (Output, Intermediate) => O2
//   ): ReactiveStream[Input, O2, M1] =
//     identityMapping[Input]
//       .sourceMap(input =>
//         val output = self(input)
//         mapF(input).map(combine(output, _))
//       )
// }

// implicit def toMapping[Input, Output](
//     f: MapFunc[Input, Output]
// ): Mapping[Input, Output] = new Mapping[Input, Output]() {
//   def apply(i: Input) = f(i)
// }

// def identityMapping[Output] = toMapping(identity[Output])

// def pair[T1, T2, T3, T4, M1, M2](
//     f1: ReactiveStream[T1, T2, M1],
//     f2: ReactiveStream[T3, T4, M2]
// ): ReactiveStream[(T1, T3), (T2, T4), (M1, M2)] = {
//   def _pairCombinator(
//       argument: (T1, T3),
//       past: (M1, M2)
//   ): ((T2, T4), (M1, M2)) = {
//     val (pastValueF1, pastValueF2) = past
//     val value1 = f1(argument._1, pastValueF1)
//     val value2 = f2(argument._2, pastValueF2)
//     ((value1._1, value2._1), (value1._2, value2._2))
//   }
//   toReactiveStream(_pairCombinator)
// }

// def pair[I1, I2, O1, O2, M](
//     fMap: Mapping[I2, O2],
//     stream: ReactiveStream[I1, O1, M]
// ): ReactiveStream[(I2, I1), (O2, O1), M] = {
//   def _pairCombinator(
//       argument: (I2, I1),
//       past: M
//   ): ((O2, O1), M) = {
//     val value = stream(argument._2, past)
//     val valueFMap = fMap(argument._1)
//     ((valueFMap, value._1), value._2)
//   }
//   toReactiveStream(_pairCombinator)
// }

// def pair[I1, I2, O1, O2, M](
//     stream: ReactiveStream[I1, O1, M],
//     fMap: Mapping[I2, O2]
// ): ReactiveStream[(I1, I2), (O1, O2), M] = {
//   def _pairCombinator(
//       argument: (I1, I2),
//       past: M
//   ): ((O1, O2), M) = {
//     val value = stream(argument._1, past)
//     val valueFMap = fMap(argument._2)
//     ((value._1, valueFMap), value._2)
//   }
//   toReactiveStream(_pairCombinator)
// }

// def pair[I1, O1, O2, M, M2](
//     source: Source[O2, M2],
//     stream: ReactiveStream[I1, O1, M]
// ): ReactiveStream[I1, (O2, O1), (M2, M)] = {
//   def _pairCombinator(
//       argument: I1,
//       past: (M2, M)
//   ): ((O2, O1), (M2, M)) = {
//     val value = stream(argument, past._2)
//     val valueFMap = source(past._1)
//     ((valueFMap._1, value._1), (valueFMap._2, value._2))
//   }
//   toReactiveStream(_pairCombinator)
// }

// def pair[I1, O1, O2, M, M2](
//     stream: ReactiveStream[I1, O1, M],
//     source: Source[O2, M2]
// ): ReactiveStream[I1, (O1, O2), (M, M2)] = {
//   def _pairCombinator(
//       argument: I1,
//       past: (M, M2)
//   ): ((O1, O2), (M, M2)) = {
//     val value = stream(argument, past._1)
//     val valueFMap = source(past._2)
//     ((value._1, valueFMap._1), (value._2, valueFMap._2))
//   }
//   toReactiveStream(_pairCombinator)
// }

// def sharedPair[T1, T2, T3, M1, M2](
//     f1: ReactiveStream[T1, T2, M1],
//     f2: ReactiveStream[T1, T3, M2]
// ): ReactiveStream[T1, (T2, T3), (M1, M2)] = {
//   def _pairCombinator(
//       argument: T1,
//       past: (M1, M2)
//   ): ((T2, T3), (M1, M2)) = {
//     val (pastValueF1, pastValueF2) = past
//     val value1 = f1(argument, pastValueF1)
//     val value2 = f2(argument, pastValueF2)
//     ((value1._1, value2._1), (value1._2, value2._2))
//   }
//   toReactiveStream(_pairCombinator)
// }

// def mergeInput[I, O, M](
//     stream: ReactiveStream[(I, I), O, M]
// ): ReactiveStream[I, O, M] =
//   stream.inputMap(value => (value, value))

// def pair[T1, T2, M1, M2](
//     f1: Source[T1, M1],
//     f2: Source[T2, M2]
// ): Source[(T1, T2), (M1, M2)] = {
//   def _pairCombinator(
//       past: (M1, M2)
//   ): ((T1, T2), (M1, M2)) = {
//     val (pastValueF1, pastValueF2) = past
//     val value1 = f1(pastValueF1)
//     val value2 = f2(pastValueF2)
//     ((value1._1, value2._1), (value1._2, value2._2))
//   }
//   toSource(_pairCombinator)
// }

// def repeatPast[Output](
//     input: Output
// ): Source[Output, Output] = {
//   def _repeatPast(
//       past: Output
//   ): (Output, Output) = {
//     (past, input)
//   }
//   toSource(_repeatPast)
// }

// def anyMemory[Input, Output, Memory](
//     stream: ReactiveStream[Input, Output, Option[Memory]]
// ): ReactiveStream[Input, Output, Option[Any]] =
//   stream.mapMemory(_.map(_.asInstanceOf[Memory]), _.map(_.asInstanceOf[Any]))

// def anyMemory[Output, Memory](
//     source: Source[Output, Option[Memory]]
// ): Source[Output, Option[Any]] =
//   source.mapMemory(_.map(_.asInstanceOf[Memory]), _.map(_.asInstanceOf[Any]))

// def withDefaultOutput[Input, Output, Memory](
//     default: Output,
//     f: ReactiveStream[Input, Option[Output], Memory]
// ): ReactiveStream[Input, Output, Memory] =
//   f.map((value: Option[Output]) => value.getOrElse(default))

// def flattenSource[Output, Memory, M1](
//     f: Memory => Source[(Output, Memory), M1]
// ): Source[Output, (Memory, M1)] = {
//   def _flattenSource(
//       past: (Memory, M1)
//   ): (Output, (Memory, M1)) = {
//     val source = f(past._1)
//     val output = source(past._2)
//     (output._1._1, (output._1._2, output._2))
//   }
//   toSource(_flattenSource)
// }

// def flattenStream[Input, Output, Memory, M1](
//     f: (Input, Memory) => Source[(Output, Memory), M1]
// ): ReactiveStream[Input, Output, (Memory, M1)] = {
//   def _flattenSource(
//       input: Input,
//       past: (Memory, M1)
//   ): (Output, (Memory, M1)) = {
//     val source = f(input, past._1)
//     val output = source(past._2)
//     (output._1._1, (output._1._2, output._2))
//   }
//   toReactiveStream(_flattenSource)
// }
