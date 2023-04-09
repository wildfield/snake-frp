package snake

import snake.TutorialApp.stepsSinceLastState

trait StatefulStream[Input, Output] {
  def run(input: Input): Output
  def clear(): Unit
}

def create[Input, Output](
    stream: ReactiveStreamAny[Input, Output]
): StatefulStream[Input, Output] = LoopStateMachine(stream)

def connect[Input, Intermediate, Output](
    m1: StatefulStream[Input, Intermediate],
    m2: StatefulStream[Intermediate, Output]
): StatefulStream[Input, Output] = ConnectedStream(m1, m2)

class LoopStateMachine[Input, Output](stream: ReactiveStreamAny[Input, Output])
    extends StatefulStream[Input, Output] {
  private var state: Option[Any] = None

  def run(input: Input): Output = {
    val (output, newState) = stream(input, state)
    state = newState
    output
  }

  def clear(): Unit = {
    state = None
  }
}

class ConnectedStream[Input, Intermediate, Output](
    left: StatefulStream[Input, Intermediate],
    right: StatefulStream[Intermediate, Output]
) extends StatefulStream[Input, Output] {
  def run(input: Input): Output = {
    val leftOutput = left.run(input)
    val output = right.run(leftOutput)
    output
  }

  def clear(): Unit = {
    left.clear()
    right.clear()
  }
}
