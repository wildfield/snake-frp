package snake

trait StatefulStream[Input, Output, Memory] {
  def run(input: Input): Output
  def clear(newValue: Memory): Unit
}

def create[Input, Output, Memory](
    stream: ReactiveStream[Input, Output, Memory],
    defaultState: Memory
): StatefulStream[Input, Output, Memory] = LoopStateMachine(stream, defaultState)

class LoopStateMachine[Input, Output, Memory](
    stream: ReactiveStream[Input, Output, Memory],
    defaultState: Memory
) extends StatefulStream[Input, Output, Memory] {
  private var state: Memory = defaultState

  def run(input: Input): Output = {
    val (output, newState) = stream(input, state)
    state = newState
    output
  }

  def clear(newValue: Memory): Unit = {
    state = newValue
  }
}
