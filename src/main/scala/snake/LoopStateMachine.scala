package snake

class LoopStateMachine[Input, Output](stream: ReactiveStreamAny[Input, Output]) {
  private var state: Option[Any] = None

  def run(input: Input): Output = {
    val (output, newState) = stream(input, state)
    state = newState
    output
  }
}
