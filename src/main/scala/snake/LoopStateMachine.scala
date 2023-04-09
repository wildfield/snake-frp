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

def merged[InputTop, InputBottom, Intermediate, Output](
    leftTop: StatefulStream[InputTop, Intermediate],
    leftBottom: StatefulStream[InputBottom, Intermediate],
    right: StatefulStream[Intermediate, Output]
): YMergedStream[InputTop, InputBottom, Intermediate, Output] =
  YMergedStream(leftTop, leftBottom, right)

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

class YMergedStream[InputTop, InputBottom, Intermediate, Output](
    leftTop: StatefulStream[InputTop, Intermediate],
    leftBottom: StatefulStream[InputBottom, Intermediate],
    right: StatefulStream[Intermediate, Output]
) {
  def runTop(input: InputTop): Output = {
    val leftTopOutput = leftTop.run(input)
    val output = right.run(leftTopOutput)
    output
  }

  def runBottom(input: InputBottom): Output = {
    val leftBottomOutput = leftBottom.run(input)
    val output = right.run(leftBottomOutput)
    output
  }

  def clear(): Unit = {
    leftTop.clear()
    leftBottom.clear()
    right.clear()
  }
}
