package snake

import scala.annotation.targetName

val DELTA_T = 25

type Time = Double

type MemoryTuple[T] = (Option[Double], Option[T])
type ReactiveStream[Memory, Input, Output] =
  (Time, Input, MemoryTuple[Memory]) => (Output, Memory)
type Source[Memory, Output] =
  (Time, MemoryTuple[Memory]) => (Output, Memory)
type InputBehavior[Input, Output] =
  (Time, Input) => Output
type Behavior[Output] =
  Time => Output

def tupleMemMap[T1, T2](a: MemoryTuple[T1], f: T1 => T2): MemoryTuple[T2] =
  (a._1, a._2.map(f))

def lift[T](a: T): ReactiveStream[Unit, Unit, T] = {
  def _upgrade(time: Double, argument: Unit, past: MemoryTuple[Unit]): (T, Unit) = {
    (a, ())
  }
  _upgrade
}

@targetName("upgradeTimeMemoryMappedFunc")
def lift[T1, T2](f: Source[T1, T2]): ReactiveStream[T1, Unit, T2] = {
  def _upgrade(time: Double, argument: Unit, past: MemoryTuple[T1]): (T2, T1) = {
    f(time, past)
  }
  _upgrade
}

@targetName("upgradeTimeMapFunc")
def lift[Input, Output](
    f: InputBehavior[Input, Output]
): ReactiveStream[Unit, Input, Output] = {
  def _upgrade(time: Double, argument: Input, past: MemoryTuple[Unit]): (Output, Unit) = {
    (f(time, argument), ())
  }
  _upgrade
}

@targetName("upgradeTimeFunc")
def lift[T](f: Behavior[T]): ReactiveStream[Unit, Unit, T] = {
  def _upgrade(time: Double, argument: Unit, past: MemoryTuple[Unit]): (T, Unit) = {
    (f(time), ())
  }
  _upgrade
}

def liftWithTimeWithOutput[Memory, Input](
    f: (Input, Option[Memory]) => Memory
): ReactiveStream[Memory, Input, Memory] = {
  def _upgrade(time: Double, argument: Input, past: MemoryTuple[Memory]): (Memory, Memory) = {
    val value = f(argument, past._2)
    (value, value)
  }
  _upgrade
}

def liftWithOutput[Memory, Input](
    f: (Time, Input, MemoryTuple[Memory]) => Memory
): ReactiveStream[Memory, Input, Memory] = {
  def _upgrade(time: Double, argument: Input, past: MemoryTuple[Memory]): (Memory, Memory) = {
    val value = f(time, argument, past)
    (value, value)
  }
  _upgrade
}

def liftWithTime[Memory, Input, Output](
    f: (Input, Option[Memory]) => (Output, Memory)
): ReactiveStream[Memory, Input, Output] = {
  def _upgrade(time: Double, argument: Input, past: MemoryTuple[Memory]): (Output, Memory) = {
    f(argument, past._2)
  }
  _upgrade
}

def liftWithTime[T1, T2](f: T1 => T2): ReactiveStream[Unit, T1, T2] = {
  def _upgrade(time: Double, argument: T1, past: MemoryTuple[Unit]): (T2, Unit) = {
    (f(argument), ())
  }
  _upgrade
}

def liftWithTime[T1](f: () => T1): ReactiveStream[Unit, Unit, T1] = {
  def _upgrade(time: Double, _arg: Unit, past: MemoryTuple[Unit]): (T1, Unit) = {
    (f(), ())
  }
  _upgrade
}

def liftToSource[T1](f: Behavior[T1]): Source[Unit, T1] = {
  def _upgrade(time: Double, past: MemoryTuple[Unit]): (T1, Unit) = {
    (f(time), ())
  }
  _upgrade
}

// def outputMemory[T1, T2](
//     f: ReactiveStream[T1, T2, Unit]
// ): ReactiveStream[T1, T2, T1] = {
//   def _output(time: Double, argument: T2, past: MemoryTuple[T1]): (T1, T1) = {
//     val value = f(time, argument, past)
//     (value._2, value._2)
//   }
//   _output
// }

// def map[T1, T2, T3, T4](
//     f1: ReactiveStream[T1, T2, T3],
//     mapFunc: T3 => T4
// ): ReactiveStream[T1, T2, T4] = {
//   def _map(time: Double, argument: T2, past: MemoryTuple[T1]): (T4, T1) = {
//     val value = f1(time, argument, past)
//     (mapFunc(value._1), value._2)
//   }
//   _map
// }

// def map[T1, T2, T3](
//     f1: Source[T1, T2],
//     mapFunc: T2 => T3
// ): Source[T1, T3] = {
//   def _map(time: Double, past: MemoryTuple[T1]): (T3, T1) = {
//     val value = f1(time, past)
//     (mapFunc(value._1), value._2)
//   }
//   _map
// }

// def mapInput[T1, T2, T3, T4](
//     f1: ReactiveStream[T1, T2, T3],
//     map: T4 => T2
// ): ReactiveStream[T1, T4, T3] = {
//   def _map(time: Double, argument: T4, past: MemoryTuple[T1]): (T3, T1) = {
//     val value = f1(time, map(argument), past)
//     (value._1, value._2)
//   }
//   _map
// }

// def mapMemory[T1, T2, T3, T4](
//     f1: ReactiveStream[T1, T2, T3],
//     compress: T1 => T4,
//     uncompress: T4 => T1
// ): ReactiveStream[T4, T2, T3] = {
//   def _map(time: Double, argument: T2, past: MemoryTuple[T4]): (T3, T4) = {
//     val value = f1(time, argument, tupleMemMap(past, uncompress))
//     (value._1, compress(value._2))
//   }
//   _map
// }

// def mapMemory[T1, T2, T3](
//     f1: Source[T1, T2],
//     compress: T1 => T3,
//     uncompress: T3 => T1
// ): Source[T3, T2] = {
//   def _map(time: Double, past: MemoryTuple[T3]): (T2, T3) = {
//     val value = f1(time, tupleMemMap(past, uncompress))
//     (value._1, compress(value._2))
//   }
//   _map
// }

@targetName("pairMemoryFuncBehaviors")
def pair[T1, T2, T3, T4](
    target1: Source[T1, T2],
    target2: Source[T3, T4]
): Source[(T1, T3), (T2, T4)] = {
  def _pairCombinator(
      time: Double,
      past: MemoryTuple[(T1, T3)]
  ): ((T2, T4), (T1, T3)) = {
    val value1 = target1(time, tupleMemMap(past, _._1))
    val value2 = target2(time, tupleMemMap(past, _._2))
    ((value1._1, value2._1), (value1._2, value2._2))
  }
  _pairCombinator
}

// def apply[T1, T2, T3](
//     f1: ReactiveStream[T1, T2, T3],
//     a: T2
// ): Source[T1, T3] = {
//   def _apply(
//       time: Double,
//       past: MemoryTuple[T1]
//   ): (T3, T1) = {
//     val value1 = f1(time, a, past)
//     value1
//   }
//   _apply
// }

// def apply[T1, T2, T3](
//     f1: ReactiveStream[T1, T2, T3],
//     f2: Behavior[T2]
// ): Source[T1, T3] = {
//   def _apply(
//       time: Double,
//       past: MemoryTuple[T1]
//   ): (T3, T1) = {
//     val value2 = f2(time)
//     val value1 = f1(time, value2, past)
//     value1
//   }
//   _apply
// }

// def apply[T1, T2, T3, T4](
//     f1: ReactiveStream[T1, T4, T3],
//     f2: Source[T2, T4]
// ): Source[(T1, T2), T3] = {
//   def _apply(
//       time: Double,
//       past: MemoryTuple[(T1, T2)]
//   ): (T3, (T1, T2)) = {
//     val value2 = f2(time, tupleMemMap(past, _._2))
//     val value1 = f1(time, value2._1, tupleMemMap(past, _._1))
//     (value1._1, (value1._2, value2._2))
//   }
//   _apply
// }

// def apply[T1, T2, T3, T4, T5](
//     f1: ReactiveStream[T1, T2, T3],
//     f2: ReactiveStream[T4, T5, T2]
// ): ReactiveStream[(T1, T4), T5, T3] = {
//   def _apply(
//       time: Double,
//       argument: T5,
//       past: MemoryTuple[(T1, T4)]
//   ): (T3, (T1, T4)) = {
//     val value2 = f2(time, argument, tupleMemMap(past, _._2))
//     val value1 = f1(time, value2._1, tupleMemMap(past, _._1))
//     (value1._1, (value1._2, value2._2))
//   }
//   _apply
// }

// def pastFlatMap[T1, T2, T3, T4, T5, T6, T7](
//     f1: ReactiveStream[T1, T2, T3],
//     map: (ReactiveStream[T1, T2, T3], Option[T5]) => ReactiveStream[T4, T7, (T6, T5)]
// ): ReactiveStream[(T4, T5), T7, T6] = {
//   def _bindPast(
//       time: Double,
//       argument: T7,
//       past: MemoryTuple[(T4, T5)]
//   ): (T6, (T4, T5)) = {
//     val oldStoredValue = tupleMemMap(past, _._2)._2
//     val newF = map(f1, oldStoredValue)
//     val newValue = newF(time, argument, tupleMemMap(past, _._1))
//     (newValue._1._1, (newValue._2, newValue._1._2))
//   }
//   _bindPast
// }

// def pastFlatMapSource[T1, T2, T3, T4, T5, T6, T7](
//     map: (Option[T5]) => Source[T4, (T6, T5)]
// ): Source[(T4, T5), T6] = {
//   def _bindPast(
//       time: Double,
//       past: MemoryTuple[(T4, T5)]
//   ): (T6, (T4, T5)) = {
//     val oldStoredValue = tupleMemMap(past, _._2)._2
//     val newF = map(oldStoredValue)
//     val newValue = newF(time, tupleMemMap(past, _._1))
//     (newValue._1._1, (newValue._2, newValue._1._2))
//   }
//   _bindPast
// }

// def pastFlatMapSource[T1, T2, T3, T4, T5, T6, T7](
//     f1: ReactiveStream[T1, T2, T3],
//     map: (ReactiveStream[T1, T2, T3], Option[T5]) => Source[T4, (T6, T5)]
// ): Source[(T4, T5), T6] = {
//   def _bindPast(
//       time: Double,
//       past: MemoryTuple[(T4, T5)]
//   ): (T6, (T4, T5)) = {
//     val oldStoredValue = tupleMemMap(past, _._2)._2
//     val newF = map(f1, oldStoredValue)
//     val newValue = newF(time, tupleMemMap(past, _._1))
//     (newValue._1._1, (newValue._2, newValue._1._2))
//   }
//   _bindPast
// }

def backstepMemory[T]()
    : (time: Double, delta: Double, backstep: (Double, Double) => T) => (T, Double) = {
  var lastTime: Double = Double.MinValue
  var lastValue: Option[T] = None
  def _backstepMemory(time: Double, delta: Double, backstep: (Double, Double) => T): (T, Double) = {
    lastValue match {
      case Some(value) => {
        if (time - DELTA_T < lastTime - Double.MinPositiveValue) {
          (value, lastTime)
        } else {
          val value = backstep(time, delta)
          lastTime = time
          lastValue = Some(value)
          (value, time)
        }
      }
      case None => {
        val value = backstep(time, delta)
        lastTime = time
        lastValue = Some(value)
        (value, time)
      }
    }
  }
  _backstepMemory
}

def backstep[T1, T3](
    f: Source[T1, T3]
): (Double, Double) => T3 = {
  val m = backstepMemory[(T3, T1)]()
  def _backstep(
      time: Double,
      delta: Double
  ): (T3, T1) = {
    if (time <= 0) {
      f(0, (None, None))
    } else {
      val (pastValue, lastTime) = m(time - delta, delta, _backstep)
      f(time, (Some(lastTime), Some(pastValue._2)))
    }
  }
  def _backstep_initial(
      time: Double,
      delta: Double
  ): T3 = {
    m(time, delta, _backstep)._1._1
  }
  _backstep_initial
}

// @targetName("timeFuncMap")
// def map[T1, T2](timeFunc: Behavior[T1], map: T1 => T2): Behavior[T2] = {
//   def _timeMap(time: Double): T2 = {
//     map(timeFunc(time))
//   }
//   _timeMap
// }

// @targetName("timeFuncMap")
// def backstepMap[T1, T2](behavior: (Double, Double) => T1, map: T1 => T2): (Double, Double) => T2 = {
//   def _timeMap(time: Double, delta: Double): T2 = {
//     map(behavior(time, delta))
//   }
//   _timeMap
// }

// case class SavedState[T](mem: T, stored: Option[T])

// def listenToReset[T1, T3](
//     f1: Source[T1, (T3, Boolean)]
// ): Source[Option[T1], T3] = {
//   def _listenToReset(
//       time: Double,
//       past: MemoryTuple[Option[T1]]
//   ): (T3, Option[T1]) = {
//     val ((output, reset), memory) =
//       f1(time, (past._1, past._2.flatten))
//     if (reset) {
//       (output, None)
//     } else {
//       (output, Some(memory))
//     }
//   }
//   _listenToReset
// }

// def composeWithMap[T1, T2, T3, T4, T5](
//     f1: T1 => T2,
//     f2: T3 => T4,
//     mapFunc: (T2, T4) => T5
// ): ((T1, T3)) => T5 =
//   (a, b) => mapFunc(f1(a), f2(b))

// def composeWithMap[T2, T3, T4, T5](
//     f1: () => T2,
//     f2: T3 => T4,
//     mapFunc: (T2, T4) => T5
// ): (T3) => T5 =
//   b => mapFunc(f1(), f2(b))

// def composeWithMap[T1, T2, T4, T5](
//     f1: T1 => T2,
//     f2: () => T4,
//     mapFunc: (T2, T4) => T5
// ): (T1) => T5 =
//   a => mapFunc(f1(a), f2())

// def composeWithMap[T1, T2, T4, T5](
//     f1: () => T2,
//     f2: () => T4,
//     mapFunc: (T2, T4) => T5
// ): () => T5 =
//   () => mapFunc(f1(), f2())

// def mapInput[T1, T2, T3](f: T1 => T2, map: T3 => T1): T3 => T2 =
//   (a) => f(map(a))

// def flatMap[T1, T2, T3, T4](
//     f: (a: T4) => ReactiveStream[T1, T2, T3]
// ): ReactiveStream[T1, (T4, T2), T3] = {
//   def _flatMap(
//       time: Double,
//       argument: (T4, T2),
//       past: MemoryTuple[T1]
//   ): (T3, T1) = {
//     val behavior = f(argument._1)
//     val value = behavior(time, argument._2, past)
//     value
//   }
//   _flatMap
// }

// def flatMap[T1, T2, T3, T4](
//     f: (time: Double, a: T4) => ReactiveStream[T1, T2, T3]
// ): ReactiveStream[T1, (T4, T2), T3] = {
//   def _flatMap(
//       time: Double,
//       argument: (T4, T2),
//       past: MemoryTuple[T1]
//   ): (T3, T1) = {
//     val behavior = f(time, argument._1)
//     val value = behavior(time, argument._2, past)
//     value
//   }
//   _flatMap
// }

// def flatMap[T1, T2, T3, T4, T5, T6](
//     f: ReactiveStream[T1, T2, T3],
//     map: T3 => ReactiveStream[T4, T5, T6]
// ): ReactiveStream[(T1, T4), (T2, T5), T6] = {
//   def _flatMap(
//       time: Double,
//       argument: (T2, T5),
//       past: MemoryTuple[(T1, T4)]
//   ): (T6, (T1, T4)) = {
//     val behavior = f(time, argument._1, tupleMemMap(past, _._1))
//     val value = map(behavior._1)(time, argument._2, tupleMemMap(past, _._2))
//     (value._1, (behavior._2, value._2))
//   }
//   _flatMap
// }

// def flatMap[T1, T3, T4, T5, T6](
//     f: Source[T1, T3],
//     map: T3 => ReactiveStream[T4, T5, T6]
// ): ReactiveStream[(T1, T4), T5, T6] = {
//   def _flatMap(
//       time: Double,
//       argument: T5,
//       past: MemoryTuple[(T1, T4)]
//   ): (T6, (T1, T4)) = {
//     val behavior = f(time, tupleMemMap(past, _._1))
//     val value = map(behavior._1)(time, argument, tupleMemMap(past, _._2))
//     (value._1, (behavior._2, value._2))
//   }
//   _flatMap
// }

// @targetName("flatMap2")
// def flatMap[T1, T2, T3, T4, T5, T6](
//     f: ReactiveStream[Unit, T2, T3],
//     map: T3 => ReactiveStream[T4, T5, T6]
// ): ReactiveStream[T4, (T2, T5), T6] = {
//   def _flatMap(
//       time: Double,
//       argument: (T2, T5),
//       past: MemoryTuple[T4]
//   ): (T6, T4) = {
//     val behavior = f(time, argument._1, tupleMemMap(past, _ => ()))
//     val value = map(behavior._1)(time, argument._2, past)
//     (value._1, value._2)
//   }
//   _flatMap
// }

// @targetName("flatMapBehaviorCell")
// def flatMapSource[T1, T2, T3, T4, T5, T6](
//     f: ReactiveStream[T1, T2, T3],
//     map: T3 => Source[T4, T6]
// ): ReactiveStream[(T1, T4), T2, T6] = {
//   def _flatMap(
//       time: Double,
//       argument: T2,
//       past: MemoryTuple[(T1, T4)]
//   ): (T6, (T1, T4)) = {
//     val behavior = f(time, argument, tupleMemMap(past, _._1))
//     val value = map(behavior._1)(time, tupleMemMap(past, _._2))
//     (value._1, (behavior._2, value._2))
//   }
//   _flatMap
// }

// @targetName("flatMapBehaviorCell5")
// def flatMapSource[T1, T2, T3, T4, T5, T6](
//     f: Source[T1, T3],
//     map: T3 => Source[T4, T6]
// ): Source[(T1, T4), T6] = {
//   def _flatMap(
//       time: Double,
//       past: MemoryTuple[(T1, T4)]
//   ): (T6, (T1, T4)) = {
//     val behavior = f(time, tupleMemMap(past, _._1))
//     val value = map(behavior._1)(time, tupleMemMap(past, _._2))
//     (value._1, (behavior._2, value._2))
//   }
//   _flatMap
// }

// @targetName("flatMapBehaviorCell2")
// def flatMapSource[T1, T2, T3, T4, T5, T6](
//     f: ReactiveStream[Unit, T2, T3],
//     map: T3 => Source[T4, T6]
// ): ReactiveStream[T4, T2, T6] = {
//   def _flatMap(
//       time: Double,
//       argument: T2,
//       past: MemoryTuple[T4]
//   ): (T6, T4) = {
//     val behavior = f(time, argument, tupleMemMap(past, _ => ()))
//     val value = map(behavior._1)(time, past)
//     (value._1, value._2)
//   }
//   _flatMap
// }
