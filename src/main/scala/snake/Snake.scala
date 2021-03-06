package snake

import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.window
import org.scalajs.dom.html.Canvas
import org.scalajs.dom.raw.CanvasRenderingContext2D

import scala.scalajs.js.Date
import scala.scalajs.js

import scala.annotation.targetName
import snake._

case class Vect2d(x: Double, y: Double)
case class Rect(x: Double, y: Double, w: Double, h: Double)
case class Direction(x: Int, y: Int)

sealed trait DrawOp
case class DrawRect(x: Double, y: Double, w: Double, h: Double, color: String) extends DrawOp
case class DrawText(x: Double, y: Double, text: String, font: String, color: String) extends DrawOp

case class Keys(
    left: Option[Double],
    right: Option[Double],
    down: Option[Double],
    up: Option[Double]
)

object Keys {
  def from_tuples(
      arguments: ((Option[Double], Option[Double]), (Option[Double], Option[Double]))
  ): Keys = {
    val ((left, right), (down, up)) = arguments
    Keys(left, right, down, up)
  }
}

object TutorialApp {
  var lastTime: Option[Double] = None

  val canvas = document.getElementById("snake").asInstanceOf[Canvas]
  val ctx =
    canvas.getContext("2d", Map("alpha" -> false)).asInstanceOf[dom.CanvasRenderingContext2D]

  var counter = 0

  var rPressTime: Double = Double.MinValue
  var pPressTime: Double = Double.MinValue

  var leftPressTime: Double = Double.MinValue
  var rightPressTime: Double = Double.MinValue
  var upPressTime: Double = Double.MinValue
  var downPressTime: Double = Double.MinValue

  var leftReleaseTime: Double = Double.MinValue
  var rightReleaseTime: Double = Double.MinValue
  var upReleaseTime: Double = Double.MinValue
  var downReleaseTime: Double = Double.MinValue

  var focusInTime: Double = Double.MinValue
  var focusOutTime: Double = Double.MinValue

  val start = new Date()

  def curTime(): Double =
    (new Date()).getTime() - start.getTime()

  def onFocusIn(): Unit = {
    focusInTime = curTime()
  }

  def onFocusOut(): Unit = {
    focusOutTime = curTime()
  }

  def onPressP(): Unit = {
    pPressTime = curTime()
  }

  def onPressR(): Unit = {
    rPressTime = curTime()
  }

  def onPressLeft(): Unit = {
    leftPressTime = curTime()
  }

  def onPressRight(): Unit = {
    rightPressTime = curTime()
  }

  def onPressUp(): Unit = {
    upPressTime = curTime()
  }

  def onPressDown(): Unit = {
    downPressTime = curTime()
  }

  def onReleaseLeft(): Unit = {
    leftReleaseTime = curTime()
  }

  def onReleaseRight(): Unit = {
    rightReleaseTime = curTime()
  }

  def onReleaseUp(): Unit = {
    upReleaseTime = curTime()
  }

  def onReleaseDown(): Unit = {
    downReleaseTime = curTime()
  }

  def didPress(
      getTime: () => Double
  ): Source[Unit, Option[Double]] = {
    def _didPress(time: Double, past: MemoryTuple[Unit]): (Option[Double], Unit) = {
      val pressedTime = getTime()
      val lastTime = past._1.getOrElse(0.0)
      val pressDetected = (pressedTime >= lastTime) && (pressedTime <= time)
      (
        if pressDetected then Some(pressedTime) else None,
        ()
      )
    }
    _didPress
  }

  def accumulate(
      factor: Int = 1
  ): (Boolean, Option[Int]) => (Int, Int) = {
    def _accumulate(increment: Boolean, past: Option[Int]): (Int, Int) = {
      val result = past match {
        case None => {
          (if increment then 1 else 0) * factor
        }
        case Some(pastValue) => {
          (if increment then 1 else 0) * factor + pastValue
        }
      }
      (result, result)
    }
    _accumulate
  }

  def keepIfLarger(
      time: Double,
      arg: Int,
      past: MemoryTuple[Int]
  ): (Int, Int) = {
    val pastValue = past._2.getOrElse(0)
    val result = if (arg > pastValue) {
      arg
    } else {
      pastValue
    }
    (result, result)
  }

  val SNAKE_SIZE = 20.0
  // Milliseconds per movement
  val PULSE_TIME: Double = 300.0
  def tick(
      time: Double,
      args: (Boolean, Int),
      past: MemoryTuple[Double]
  ): (Option[Double], Double) = {
    val (stop, score) = args
    val pastTime = past._1.getOrElse(0.0)
    val accumulatedTime = past._2.getOrElse(0.0)
    val totalTime = (time - pastTime) + accumulatedTime
    val speedupFactor = ((score / 20.0) + 1).min(4)
    val effectivePulseTime = PULSE_TIME / speedupFactor
    if (totalTime > effectivePulseTime) {
      val pulses = (totalTime / effectivePulseTime).toInt
      val elapsedTime = pulses * effectivePulseTime
      val newTime = totalTime - elapsedTime
      if (!stop) {
        (Some(pulses * SNAKE_SIZE), newTime)
      } else {
        (None, newTime)
      }
    } else {
      (None, totalTime)
    }
  }

  def validatedCurrentDirection(
      arguments: (List[Direction], Option[Direction]),
      past: Option[Direction]
  ): Direction = {
    val pastValue = past.getOrElse(Direction(0, 1))
    val (desiredDirections, lastMovedDirection) = arguments
    lastMovedDirection match {
      case Some(lastMovedDirection) => {
        // Ignore directions opposite to the last moved and exactly aligned
        // this allows the snake to process fast inputs
        val result = desiredDirections.find(direction => {
          val invalid = (direction.x != 0 && direction.x == -lastMovedDirection.x) ||
            (direction.y != 0 && direction.y == -lastMovedDirection.y) ||
            (direction.x == lastMovedDirection.x && direction.y == lastMovedDirection.y)
          !invalid
        })
        result.getOrElse(pastValue)
      }
      case _ => desiredDirections.headOption.getOrElse(pastValue)
    }
  }

  def storeDirectionIfMoved[T](
      time: Double,
      arguments: (Direction, Option[T]),
      past: MemoryTuple[Option[Direction]]
  ): Option[Direction] = {
    val (direction, signal) = arguments
    val pastValue = past._2.flatten
    signal.map(_ => direction) match {
      case Some(result) => Some(result)
      case None         => pastValue
    }
  }

  def desiredDirections(
      keys: Keys
  ): List[Direction] = {
    val directions =
      List(keys.left, keys.right, keys.up, keys.down).zipWithIndex.map((pressTime, idx) =>
        pressTime.flatMap(pressTime => {
          idx match {
            case 0 => Some(((Direction(-1, 0)), pressTime))
            case 1 => Some(((Direction(1, 0)), pressTime))
            case 2 => Some(((Direction(0, -1)), pressTime))
            case 3 => Some(((Direction(0, 1)), pressTime))
            case _ => None
          }
        })
      )
    directions.flatten.sortBy(-_._2).map(_._1)
  }

  // Arguments: speed pulse and direction to move
  def movement(
      arguments: (Option[Double], Direction)
  ): Option[Vect2d] = {
    val (speedPulse, direction) = arguments
    speedPulse.map((speed) => Vect2d(direction.x * speed, direction.y * speed))
  }

  def buttonStateLatch(
      time: Double,
      arg: (Option[Double], Option[Double]),
      past: MemoryTuple[Option[Double]]
  ): (Option[Double], Option[Double]) = {
    val (press, release) = arg
    val result = if (!release.isEmpty) {
      None
    } else if (!press.isEmpty) {
      press
    } else {
      past._2.flatten
    }
    (result, result)
  }

  def pauseLatch(
      time: Double,
      input: (Option[Double], Option[Double]),
      past: MemoryTuple[Boolean]
  ): (Boolean, Boolean) = {
    val (pausePressEvent, focusOut) = input
    val pausePress = !pausePressEvent.isEmpty
    val pastPauseState = past._2.getOrElse(false)
    val result = if (pausePress || (!pastPauseState && !focusOut.isEmpty)) {
      !pastPauseState
    } else {
      pastPauseState
    }
    (result, result)
  }

  def positiveLatch(
      time: Double,
      arg: Boolean,
      past: MemoryTuple[Boolean]
  ): Boolean = {
    val curValue = past._2.getOrElse(false)
    if (curValue || arg) {
      true
    } else {
      curValue
    }
  }

  def snake(
      bounds: Rect
  ): ReactiveStream[
    (List[Vect2d], Boolean, Boolean),
    (Option[Vect2d], Boolean),
    (List[Vect2d], Boolean)
  ] = {
    def _snake(
        deltaOption: (Option[Vect2d], Boolean),
        past: Option[(List[Vect2d], Boolean, Boolean)]
    ): (List[Vect2d], Boolean, Boolean) = {
      val (delta, didEatFood) = deltaOption
      val pastSnake = past match {
        case None => {
          val startY = 200
          val start = Vect2d(0, 200) ::
            Vect2d(0, startY - SNAKE_SIZE) ::
            List(Vect2d(0, startY - SNAKE_SIZE * 2))
          start
        }
        case Some(((snake, _, _))) => {
          snake
        }
      }
      val didEatFoodStored = past.map(_._3).getOrElse(false) || didEatFood
      delta match {
        case None =>
          (pastSnake, false, didEatFoodStored)
        case Some(delta) => {
          val newHeadX = pastSnake.head.x + delta.x
          val newHeadY = pastSnake.head.y + delta.y
          if (
            newHeadX < 0 || newHeadX >= bounds.w || newHeadY < 0 || newHeadY >= bounds.h || pastSnake
              .contains(Vect2d(newHeadX, newHeadY))
          ) {
            (pastSnake, true, didEatFoodStored)
          } else {
            val oldSnake = if didEatFoodStored then pastSnake else pastSnake.dropRight(1)
            val newSnake = Vect2d(newHeadX, newHeadY) :: oldSnake
            (newSnake, false, false)
          }
        }
      }
    }
    map(liftWithTimeWithOutput(_snake), (output) => (output._1, output._2))
  }

  def food(bounds: Rect): ReactiveStream[
    Vect2d,
    List[Vect2d],
    (Vect2d, Boolean)
  ] = {
    def _food(
        time: Double,
        snake: List[Vect2d],
        past: MemoryTuple[Vect2d]
    ): ((Vect2d, Boolean), Vect2d) = {
      val oldFoodPosition = past._2.getOrElse(Vect2d(SNAKE_SIZE * 3, SNAKE_SIZE * 4))
      snake.headOption match {
        case None => ((oldFoodPosition, false), oldFoodPosition)
        case Some(head) =>
          if (
            (head.x / SNAKE_SIZE).toInt == (oldFoodPosition.x / SNAKE_SIZE).toInt
            && (head.y / SNAKE_SIZE).toInt == (oldFoodPosition.y / SNAKE_SIZE).toInt
          ) {
            val random = new scala.util.Random
            lazy val newFoodPositionStream: Stream[Vect2d] = Vect2d(
              random.nextInt((bounds.w / SNAKE_SIZE).toInt) * SNAKE_SIZE,
              random.nextInt((bounds.h / SNAKE_SIZE).toInt) * SNAKE_SIZE
            ) #:: newFoodPositionStream.map { _ =>
              Vect2d(
                random.nextInt((bounds.w / SNAKE_SIZE).toInt) * SNAKE_SIZE,
                random.nextInt((bounds.h / SNAKE_SIZE).toInt) * SNAKE_SIZE
              )
            }
            val newFood = newFoodPositionStream.find(!snake.contains(_)).get
            ((newFood, true), newFood)
          } else {
            ((oldFoodPosition, false), oldFoodPosition)
          }
      }
    }
    _food
  }

  def makeButtonLatch[T1](
      press: Source[T1, Option[Double]],
      release: Source[T1, Option[Double]]
  ): Source[((T1, T1), Option[Double]), Option[Double]] = {
    flatMapSource(
      pair(press, release),
      keyPair => {
        apply(buttonStateLatch, keyPair)
      }
    )
  }

  def drawSnake(snake: List[Vect2d]): List[DrawOp] = {
    snake.zipWithIndex.map((elem, idx) => {
      val color = if idx == 0 then "#ffff00" else "#ff0000"
      DrawRect(elem.x, elem.y, SNAKE_SIZE, SNAKE_SIZE, color)
    })
  }

  def drawGameOver(gameOver: Boolean): List[DrawOp] = {
    if (gameOver) {
      List(DrawText(200, 250, "Game Over", "bold 20px arial,serif", "#ffbb00"))
    } else {
      List()
    }
  }

  def drawPause(pause: Boolean): List[DrawOp] = {
    if (pause) {
      List(DrawText(200, 250, "Paused", "bold 20px arial,serif", "#ffbb00"))
    } else {
      List()
    }
  }

  def drawFood(food: Vect2d): List[DrawOp] = {
    List(DrawRect(food.x, food.y, SNAKE_SIZE, SNAKE_SIZE, "#0000ff"))
  }

  def drawScore(score: Int): List[DrawOp] = {
    List(DrawText(360, 40, s"Score: ${score}", "italic bold 14px arial,serif", "#ffbb00"))
  }

  def drawHighScore(score: Int): List[DrawOp] = {
    List(DrawText(360, 60, s"High Score: ${score}", "italic bold 14px arial,serif", "#ffbb00"))
  }

  def drawClearScreen(bounds: Rect): List[DrawOp] = {
    List(DrawRect(bounds.x, bounds.y, bounds.w, bounds.h, "#006123"))
  }

  def shouldRedraw(
      time: Double,
      input: (Option[Double], Boolean, Boolean, Boolean),
      past: MemoryTuple[(Boolean, Boolean)]
  ): (Boolean, (Boolean, Boolean)) = {
    val (tick, paused, gameOver, focusIn) = input
    val (pastPaused, pastGameOver) = past._2.getOrElse((false, false))
    val should = focusIn || !(tick.isEmpty && paused == pastPaused && gameOver == pastGameOver)
    (should, (paused, gameOver))
  }

  def singleTruePulse(
      time: Double,
      past: Option[(Double, Boolean)]
  ): (Option[Boolean], Boolean) = {
    val (_, pastValue) = past.getOrElse((0, false))
    if (pastValue) {
      (None, pastValue)
    } else {
      (Some(true), true)
    }
  }

  def onFrame(time: Double, drawOps: (Double, Double) => Iterable[DrawOp]): Unit = {
    var delta = lastTime match {
      case Some(lastTime) =>
        time - lastTime
      case None =>
        0
    }
    lastTime = Some(time)
    render(delta, drawOps)
    window.requestAnimationFrame((timestamp: Double) => TutorialApp.onFrame(timestamp, drawOps))
  }

  def render(
      delta: Double,
      drawOps: (Double, Double) => Iterable[DrawOp]
  ): Unit = {
    val time = (new Date()).getTime() - start.getTime()
    val timeSampling = (delta / 200.0).max(DELTA_T)
    val drawOpsValue = drawOps(time, timeSampling)
    var lastColor: String = ""
    for (op <- drawOpsValue) {
      op match {
        case DrawRect(x, y, w, h, color) => {
          if (color != lastColor) {
            ctx.fillStyle = color
            lastColor = color
          }
          ctx.fillRect(x, y, w, h)
        }
        case DrawText(x, y, text, font, color) => {
          if (color != lastColor) {
            ctx.fillStyle = color
            lastColor = color
          }
          ctx.font = font
          ctx.fillText(text, x, y)
        }
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val bounds = Rect(0, 0, canvas.width, canvas.height)
    val upPress = didPress(() => this.upPressTime)
    val downPress = didPress(() => this.downPressTime)
    val leftPress = didPress(() => this.leftPressTime)
    val rightPress = didPress(() => this.rightPressTime)

    val upRelease = didPress(() => this.upReleaseTime)
    val downRelease = didPress(() => this.downReleaseTime)
    val leftRelease = didPress(() => this.leftReleaseTime)
    val rightRelease = didPress(() => this.rightReleaseTime)

    val rPress = didPress(() => this.rPressTime)
    val pPress = didPress(() => this.pPressTime)

    val focusOut = didPress(() => this.focusOutTime)

    val upLatch = makeButtonLatch(upPress, upRelease)
    val downLatch = makeButtonLatch(downPress, downRelease)
    val leftLatch = makeButtonLatch(leftPress, leftRelease)
    val rightLatch = makeButtonLatch(rightPress, rightRelease)

    val keyTuples = pair(pair(leftLatch, rightLatch), pair(downLatch, upLatch))
    val keyValues = map(keyTuples, Keys.from_tuples)

    val pLatch =
      apply(
        pauseLatch,
        pair(pPress, focusOut)
      )
    val desiredDirectionsSignal = map(keyValues, desiredDirections)
    val timeWithPause = flatMap(
      pLatch,
      pLatch => {
        map(
          mapInput(
            tick,
            (input: (Boolean, Int)) => (input._1 || pLatch, input._2)
          ),
          tick => (tick, pLatch)
        )
      }
    )
    val resultingMovement =
      flatMapSource(
        timeWithPause,
        output => {
          val (tick, pause) = output
          flatMapSource(
            desiredDirectionsSignal,
            desiredDirections => {
              map(
                pastFlatMapSource(
                  liftWithTimeWithOutput(validatedCurrentDirection),
                  (
                      validatedCurrentDirection,
                      lastMovedDirection: Option[Option[Direction]]
                  ) => {
                    flatMapSource(
                      apply(
                        validatedCurrentDirection,
                        (desiredDirections, lastMovedDirection.flatten)
                      ),
                      currentDirection => {
                        map(
                          apply(
                            liftWithOutput(storeDirectionIfMoved[Double]),
                            (currentDirection, tick)
                          ),
                          (currentDirection, _)
                        )
                      }
                    )
                  }
                ),
                movementDirection => {
                  (movement(tick, movementDirection), pause, tick)
                }
              )
            }
          )
        }
      )
    val gameState =
      pastFlatMapSource(
        resultingMovement,
        (resultingMovement, memory: Option[(Boolean, Int)]) => {
          val pastGameOver = memory.map(_._1).getOrElse(false)
          val pastScore = memory.map(_._2).getOrElse(0)
          flatMapSource(
            apply(
              resultingMovement,
              (pastGameOver, pastScore)
            ),
            output => {
              val (movement, pause, tick) = output
              pastFlatMapSource(
                food(bounds),
                (food, pastSnakeOption: Option[List[Vect2d]]) => {
                  val pastSnake = pastSnakeOption.getOrElse(List())
                  flatMapSource(
                    apply(
                      food,
                      pastSnake
                    ),
                    output => {
                      val (food, didEatFood) = output
                      flatMapSource(
                        apply(liftWithTime(accumulate(1)), didEatFood),
                        score => {
                          flatMapSource(
                            apply(
                              snake(bounds),
                              (movement, didEatFood)
                            ),
                            output => {
                              val (snake, gameOver) = output
                              map(
                                apply(
                                  liftWithOutput(positiveLatch),
                                  gameOver
                                ),
                                latchedGameOver => {
                                  (
                                    (
                                      (tick, score, food, snake, pause, latchedGameOver),
                                      (latchedGameOver, score)
                                    ),
                                    snake
                                  )
                                }
                              )
                            }
                          )
                        }
                      )
                    }
                  )
                }
              )
            }
          )
        }
      )
    val stateWithReset = listenToReset(
      map(
        pair(rPress, gameState),
        output => {
          val (rPress, gameState) = output
          (gameState, !rPress.isEmpty)
        }
      )
    )
    val stateWithHighScore = flatMapSource(
      stateWithReset,
      output => {
        val (tick, score, food, snake, pause, gameOver) = output
        val highScore = apply(keepIfLarger, score)
        map(
          highScore,
          highScore => {
            (tick, (snake, food, score, highScore, pause && !gameOver, gameOver))
          }
        )
      }
    )
    val focusIn = didPress(() => this.focusInTime)
    // Inputs: Snake, Food, Score, High Score, Pause, Game Over
    val draws = flatMapSource(
      stateWithHighScore,
      output => {
        val (tick, (snake, food, score, highScore, pause, gameOver)) = output
        map(
          flatMapSource(
            focusIn,
            focusIn => {
              apply(
                shouldRedraw,
                (tick, pause, gameOver, !focusIn.isEmpty)
              )
            }
          ),
          shouldRedraw => {
            if (shouldRedraw) {
              drawClearScreen(bounds) ::: drawFood(food)
                ::: drawSnake(snake) ::: drawScore(score)
                ::: drawHighScore(highScore) ::: drawPause(pause) ::: drawGameOver(gameOver)
            } else {
              List()
            }
          }
        )
      }
    )
    val drawOpsResolved = backstep(draws)

    document.addEventListener(
      "keydown",
      (e: dom.KeyboardEvent) =>
        e.key match {
          case "r" | "R" =>
            onPressR()
          case "ArrowLeft" =>
            onPressLeft()
          case "ArrowRight" =>
            onPressRight()
          case "ArrowUp" =>
            onPressUp()
          case "ArrowDown" =>
            onPressDown()
          case "p" | "P" =>
            onPressP()
          case _ => ()
        }
    )
    document.addEventListener(
      "keyup",
      (e: dom.KeyboardEvent) => {
        e.key match {
          case "ArrowLeft" =>
            onReleaseLeft()
          case "ArrowRight" =>
            onReleaseRight()
          case "ArrowUp" =>
            onReleaseUp()
          case "ArrowDown" =>
            onReleaseDown()
          case _ => ()
        }
      }
    )
    document.addEventListener(
      "visibilitychange",
      (e: dom.Event) => {
        if (document.visibilityState == "hidden") {
          onFocusOut()
        } else {
          onFocusIn()
        }
      }
    )

    window.requestAnimationFrame((timestamp: Double) =>
      TutorialApp.onFrame(timestamp, drawOpsResolved)
    )
  }
}
