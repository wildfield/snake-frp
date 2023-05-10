package snake

type ReactiveStream[Input, Output, Memory] =
  (Input, Memory) => (Output, Memory)
type Source[Output, Memory] = ReactiveStream[Unit, Output, Memory]
type Mapping[Input, Output] = Input => Output
type Loop[Input, Memory] = ReactiveStream[Input, Unit, Memory]
type SourceLoop[Memory] = ReactiveStream[Unit, Unit, Memory]

extension [Output](s: Mapping[Output, Output])
  def looped: ReactiveStream[Unit, Unit, Output] =
    (input: Unit, mem: Output) =>
      val output = s(mem)
      ((), output)

extension [Output, Memory](s: ReactiveStream[Unit, Output, Memory])
  def apply(past: Memory): (Output, Memory) = s.apply((), past)

extension [Memory](s: Loop[Unit, Memory]) def apply(past: Memory): Memory = s.apply((), past)(1)

extension [Input, Output, Memory](f: Input => Source[Output, Memory])
  def toStream: ReactiveStream[Input, Output, Memory] =
    (input: Input, mem: Memory) => f(input)(mem)

extension [Input, Memory](f: (Input, Memory) => Memory)
  def toLoop: Loop[Input, Memory] =
    (input: Input, mem: Memory) => ((), f(input, mem))

extension [Input, Memory](f: Input => Loop[Unit, Memory])
  def toLoop: Loop[Input, Memory] =
    (input: Input, mem: Memory) => ((), f(input)(mem))

extension [Input, Memory](f: Loop[Input, Memory])
  def outputMemory: ReactiveStream[Input, Memory, Memory] =
    (input: Input, mem: Memory) => (mem, f(input, mem)(1))

extension [Input, Memory](f: (Input, Memory) => SourceLoop[Memory])
  def toFlatLoop: Loop[Input, Memory] =
    (input: Input, mem: Memory) => ((), f(input, mem)(mem))

extension [Input, Output, Memory](f: (Input, Memory) => Source[Output, Memory])
  def toFlatStream: ReactiveStream[Input, Output, Memory] =
    (input: Input, mem: Memory) =>
      val value = f(input, mem)(mem)
      (value(0), value(1))

extension [Input, Output](s: Input => Output)
  def applyStream[I1, M2](
      argumentStream: ReactiveStream[I1, Input, M2]
  ): ReactiveStream[I1, Output, M2] =
    (input: I1, past: M2) =>
      val argument = argumentStream(input, past)
      val output = s(argument(0))
      (output, argument(1))

extension [Input, Output, Memory](s: ReactiveStream[Input, Output, Memory])
  def toMapping: Input => Source[Output, Memory] =
    (input: Input) => s.applyInput(input)

  def applyInput(argument: Input): Source[Output, Memory] = (_: Unit, past: Memory) =>
    s(argument, past)

  def applyStream[I1, M2, M3](
      argumentStream: ReactiveStream[I1, Input, M2],
      pack: (M2, Memory) => M3,
      unpack: M3 => (M2, Memory)
  ): ReactiveStream[I1, Output, M3] =
    (input: I1, pastPacked: M3) =>
      val past = unpack(pastPacked)
      val argument = argumentStream(input, past(0))
      val output = s(argument(0), past(1))
      (output(0), pack(argument(1), output(1)))

  def applyStreamTupled[I1, M2](
      argumentStream: ReactiveStream[I1, Input, M2]
  ): ReactiveStream[I1, Output, (M2, Memory)] =
    s.applyStream(argumentStream, (_, _), identity)

  def memoryMap[M1](outF: Memory => M1, inF: M1 => Memory) =
    (input: Input, past: M1) =>
      val output = s(input, inF(past))
      (output(0), outF(output(1)))

  def memoryOutputMap[M1](outF: (Output, Memory) => M1, inF: M1 => Memory) =
    (input: Input, past: M1) =>
      val output = s(input, inF(past))
      (output(0), outF(output(0), output(1)))

  def map[O2](f: Output => O2): ReactiveStream[Input, O2, Memory] =
    (input: Input, mem: Memory) =>
      val output = s(input, mem)
      (f(output(0)), output(1))

  def memoryToOption(
      initialValue: Memory
  ): ReactiveStream[Input, Output, Option[Memory]] =
    s.memoryMap(
      Some(_),
      _ match {
        case Some(memory) => memory
        case None         => initialValue
      }
    )

  def memoryToOptionAny(
      initialValue: Memory
  ): ReactiveStream[Input, Output, Option[Any]] =
    s.memoryMap(
      Some(_),
      _ match {
        case Some(memory) => memory.asInstanceOf[Memory]
        case None         => initialValue
      }
    )

extension [I0, O0, M0, I1, O1, M1](
    t: Tuple2[
      ReactiveStream[I0, O0, M0],
      ReactiveStream[I1, O1, M1]
    ]
)
  def toStream: ReactiveStream[(I0, I1), (O0, O1), (M0, M1)] =
    (input: (I0, I1), mem: (M0, M1)) =>
      val v0 = t(0)(input(0), mem(0))
      val v1 = t(1)(input(1), mem(1))
      ((v0(0), v1(0)), (v0(1), v1(1)))

extension [I, O0, M0, O1, M1](
    t: Tuple2[
      ReactiveStream[I, O0, M0],
      ReactiveStream[I, O1, M1]
    ]
)
  def toSharedInputStream: ReactiveStream[I, (O0, O1), (M0, M1)] =
    (input: I, mem: (M0, M1)) =>
      val v0 = t(0)(input, mem(0))
      val v1 = t(1)(input, mem(1))
      ((v0(0), v1(0)), (v0(1), v1(1)))

extension [I0, M0, I1, M1](
    t: Tuple2[
      ReactiveStream[I0, Unit, M0],
      ReactiveStream[I1, Unit, M1]
    ]
)
  def toLoop: ReactiveStream[(I0, I1), Unit, (M0, M1)] =
    (input: (I0, I1), mem: (M0, M1)) =>
      val v0 = t(0)(input(0), mem(0))
      val v1 = t(1)(input(1), mem(1))
      ((), (v0(1), v1(1)))

extension [I, M0, M1](
    t: Tuple2[
      ReactiveStream[I, Unit, M0],
      ReactiveStream[I, Unit, M1]
    ]
)
  def toSharedInputLoop: ReactiveStream[I, Unit, (M0, M1)] =
    (input: I, mem: (M0, M1)) =>
      val v0 = t(0)(input, mem(0))
      val v1 = t(1)(input, mem(1))
      ((), (v0(1), v1(1)))

extension [I0, O0, M0, I1, O1, M1, I2, O2, M2](
    t: Tuple3[
      ReactiveStream[I0, O0, M0],
      ReactiveStream[I1, O1, M1],
      ReactiveStream[I2, O2, M2],
    ]
)
  def toStream: ReactiveStream[(I0, I1, I2), (O0, O1, O2), (M0, M1, M2)] =
    (input: (I0, I1, I2), mem: (M0, M1, M2)) =>
      val v0 = t(0)(input(0), mem(0))
      val v1 = t(1)(input(1), mem(1))
      val v2 = t(2)(input(2), mem(2))
      ((v0(0), v1(0), v2(0)), (v0(1), v1(1), v2(1)))

extension [I, O0, M0, O1, M1, O2, M2](
    t: Tuple3[
      ReactiveStream[I, O0, M0],
      ReactiveStream[I, O1, M1],
      ReactiveStream[I, O2, M2],
    ]
)
  def toSharedInputStream: ReactiveStream[I, (O0, O1, O2), (M0, M1, M2)] =
    (input: I, mem: (M0, M1, M2)) =>
      val v0 = t(0)(input, mem(0))
      val v1 = t(1)(input, mem(1))
      val v2 = t(2)(input, mem(2))
      ((v0(0), v1(0), v2(0)), (v0(1), v1(1), v2(1)))

extension [I0, M0, I1, M1, I2, M2](
    t: Tuple3[
      ReactiveStream[I0, Unit, M0],
      ReactiveStream[I1, Unit, M1],
      ReactiveStream[I2, Unit, M2],
    ]
)
  def toLoop: ReactiveStream[(I0, I1, I2), Unit, (M0, M1, M2)] =
    (input: (I0, I1, I2), mem: (M0, M1, M2)) =>
      val v0 = t(0)(input(0), mem(0))
      val v1 = t(1)(input(1), mem(1))
      val v2 = t(2)(input(2), mem(2))
      ((), (v0(1), v1(1), v2(1)))

extension [I, M0, M1, M2](
    t: Tuple3[
      ReactiveStream[I, Unit, M0],
      ReactiveStream[I, Unit, M1],
      ReactiveStream[I, Unit, M2],
    ]
)
  def toSharedInputLoop: ReactiveStream[I, Unit, (M0, M1, M2)] =
    (input: I, mem: (M0, M1, M2)) =>
      val v0 = t(0)(input, mem(0))
      val v1 = t(1)(input, mem(1))
      val v2 = t(2)(input, mem(2))
      ((), (v0(1), v1(1), v2(1)))

extension [I0, O0, M0, I1, O1, M1, I2, O2, M2, I3, O3, M3](
    t: Tuple4[
      ReactiveStream[I0, O0, M0],
      ReactiveStream[I1, O1, M1],
      ReactiveStream[I2, O2, M2],
      ReactiveStream[I3, O3, M3],
    ]
)
  def toStream: ReactiveStream[(I0, I1, I2, I3), (O0, O1, O2, O3), (M0, M1, M2, M3)] =
    (input: (I0, I1, I2, I3), mem: (M0, M1, M2, M3)) =>
      val v0 = t(0)(input(0), mem(0))
      val v1 = t(1)(input(1), mem(1))
      val v2 = t(2)(input(2), mem(2))
      val v3 = t(3)(input(3), mem(3))
      ((v0(0), v1(0), v2(0), v3(0)), (v0(1), v1(1), v2(1), v3(1)))

extension [I, O0, M0, O1, M1, O2, M2, O3, M3](
    t: Tuple4[
      ReactiveStream[I, O0, M0],
      ReactiveStream[I, O1, M1],
      ReactiveStream[I, O2, M2],
      ReactiveStream[I, O3, M3],
    ]
)
  def toSharedInputStream: ReactiveStream[I, (O0, O1, O2, O3), (M0, M1, M2, M3)] =
    (input: I, mem: (M0, M1, M2, M3)) =>
      val v0 = t(0)(input, mem(0))
      val v1 = t(1)(input, mem(1))
      val v2 = t(2)(input, mem(2))
      val v3 = t(3)(input, mem(3))
      ((v0(0), v1(0), v2(0), v3(0)), (v0(1), v1(1), v2(1), v3(1)))

extension [I0, M0, I1, M1, I2, M2, I3, M3](
    t: Tuple4[
      ReactiveStream[I0, Unit, M0],
      ReactiveStream[I1, Unit, M1],
      ReactiveStream[I2, Unit, M2],
      ReactiveStream[I3, Unit, M3],
    ]
)
  def toLoop: ReactiveStream[(I0, I1, I2, I3), Unit, (M0, M1, M2, M3)] =
    (input: (I0, I1, I2, I3), mem: (M0, M1, M2, M3)) =>
      val v0 = t(0)(input(0), mem(0))
      val v1 = t(1)(input(1), mem(1))
      val v2 = t(2)(input(2), mem(2))
      val v3 = t(3)(input(3), mem(3))
      ((), (v0(1), v1(1), v2(1), v3(1)))

extension [I, M0, M1, M2, M3](
    t: Tuple4[
      ReactiveStream[I, Unit, M0],
      ReactiveStream[I, Unit, M1],
      ReactiveStream[I, Unit, M2],
      ReactiveStream[I, Unit, M3],
    ]
)
  def toSharedInputLoop: ReactiveStream[I, Unit, (M0, M1, M2, M3)] =
    (input: I, mem: (M0, M1, M2, M3)) =>
      val v0 = t(0)(input, mem(0))
      val v1 = t(1)(input, mem(1))
      val v2 = t(2)(input, mem(2))
      val v3 = t(3)(input, mem(3))
      ((), (v0(1), v1(1), v2(1), v3(1)))
