package snake

type ReactiveStreamFunc[Input, Output, Memory] =
  (Input, Memory) => (Output, Memory)
type SourceFunc[Output, Memory] = ReactiveStreamFunc[Unit, Output, Memory]
type MappingFunc[Input, Output] = Input => Output

extension [Output](s: MappingFunc[Output, Output])
  def looped: ReactiveStreamFunc[Unit, Output, Output] =
    (input: Unit, mem: Output) =>
      val output = s(mem)
      (output, output)

extension [Output, Memory](s: ReactiveStreamFunc[Unit, Output, Memory])
  def apply(past: Memory): (Output, Memory) = s.apply((), past)

extension [Input, Output, Memory](f: Input => SourceFunc[Output, Memory])
  def toStream: ReactiveStreamFunc[Input, Output, Memory] =
    (input: Input, mem: Memory) => f(input)(mem)

extension [Input, Output, Memory, M2](f: (Input, Memory) => SourceFunc[(Output, Memory), M2])
  def toFlatStream: ReactiveStreamFunc[Input, Output, (Memory, M2)] =
    (input: Input, mem: (Memory, M2)) =>
      val value = f(input, mem(0))(mem(1))
      (value(0)(0), (value(0)(1), value(1)))

extension [Input, Output](s: Input => Output)
  def applyStream[I1, M2](
      argumentStream: ReactiveStreamFunc[I1, Input, M2]
  ): ReactiveStreamFunc[I1, Output, M2] =
    (input: I1, past: M2) =>
      val argument = argumentStream(input, past)
      val output = s(argument(0))
      (output, argument(1))

extension [Input, Output, Memory](s: ReactiveStreamFunc[Input, Output, Memory])
  def toMapping: Input => SourceFunc[Output, Memory] =
    (input: Input) => s.applyInput(input)

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

  def initializeMemoryAny(
      initialValue: Memory
  ): ReactiveStreamFunc[Input, Output, Option[Any]] =
    s.memoryMap(
      _ match {
        case Some(memory) => memory.asInstanceOf[Memory]
        case None         => initialValue
      },
      Some(_)
    )

  def withInput[O](
      mapF: (Input, Output) => O
  ): ReactiveStreamFunc[Input, O, Memory] =
    (input: Input, mem: Memory) =>
      val output = s(input, mem)
      (mapF(input, output(0)), output(1))

  def extend[O1, O2, M1, M2](
      mapF: Output => SourceFunc[O1, M1],
      combine: (O1, Output) => O2,
      pack: (M1, Memory) => M2,
      unpack: M2 => (M1, Memory)
  ): ReactiveStreamFunc[Input, O2, M2] =
    (input: Input, mem: M2) =>
      val (mappedMem, curMem) = unpack(mem)
      val output = s.apply(input, curMem)
      val mappedOutput = mapF(output(0))(mappedMem)
      (combine(mappedOutput(0), output(0)), pack(mappedOutput(1), output(1)))

  def extendTupled[O1, O2, M1](
      mapF: Output => SourceFunc[O1, M1],
      combine: (O1, Output) => O2
  ): ReactiveStreamFunc[Input, O2, (M1, Memory)] =
    s.extend(mapF, combine, (_, _), { case (m1, mem) => (m1, mem) })

  def parallel[O2, Intermediate, M1, M2](
      mapF: (Input, Output) => SourceFunc[Intermediate, M1],
      combine: (Intermediate, Output) => O2,
      pack: (M1, Memory) => M2,
      unpack: M2 => (M1, Memory)
  ): ReactiveStreamFunc[Input, O2, M2] =
    (input: Input, mem: M2) =>
      val (parallelMem, currentMem) = unpack(mem)
      val output = s.apply(input, currentMem)
      val parallelOutput = mapF(input, output(0))(parallelMem)
      (combine(parallelOutput(0), output(0)), pack(parallelOutput(1), output(1)))

  def parallelTupled[O2, Intermediate, M1](
      mapF: (Input, Output) => SourceFunc[Intermediate, M1],
      combine: (Intermediate, Output) => O2
  ): ReactiveStreamFunc[Input, O2, (M1, Memory)] =
    s.parallel(mapF, combine, (_, _), { case (m1, m) => (m1, m) })

  def partial[IntermediateI, O2, Intermediate, M1, M2](
      extract: Output => IntermediateI,
      mapF: IntermediateI => SourceFunc[Intermediate, M1],
      combine: (Intermediate, Output) => O2,
      pack: (M1, Memory) => M2,
      unpack: M2 => (M1, Memory)
  ): ReactiveStreamFunc[Input, O2, M2] =
    (input: Input, mem: M2) =>
      val (parallelMem, currentMem) = unpack(mem)
      val output = s.apply(input, currentMem)
      val partialOutput = mapF(extract(output(0)))(parallelMem)
      (combine(partialOutput(0), output(0)), pack(partialOutput(1), output(1)))

  def partialTupled[IntermediateI, O2, Intermediate, M1](
      extract: Output => IntermediateI,
      mapF: IntermediateI => SourceFunc[Intermediate, M1],
      combine: (Intermediate, Output) => O2
  ): ReactiveStreamFunc[Input, O2, (M1, Memory)] =
    s.partial(extract, mapF, combine, (_, _), { case (m1, mem) => (m1, mem) })

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
