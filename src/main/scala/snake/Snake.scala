package snake

import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.window
import org.scalajs.dom.html.Canvas
import org.scalajs.dom.CanvasRenderingContext2D

import scala.scalajs.js.Date
import scala.scalajs.js

import scala.annotation.targetName
import snake._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import java.awt.Event

case class Vect2d(x: Double, y: Double)
case class Rect(x: Double, y: Double, w: Double, h: Double)
case class Direction(x: Int, y: Int)

sealed trait DrawOp
case class DrawRect(x: Double, y: Double, w: Double, h: Double, color: String) extends DrawOp
case class DrawText(x: Double, y: Double, text: String, font: String, color: String) extends DrawOp

enum EventType:
  case LeftKeyPress, RightKeyPress, UpKeyPress, DownKeyPress, LeftKeyRelease, RightKeyRelease,
  UpKeyRelease, DownKeyRelease, PKeyPress, RKeyPress, FocusIn, FocusOut

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

  var events = scala.collection.mutable.HashMap[EventType, Double]()

  def record(event: EventType): Unit = {
    val time = curTime()
    events(event) = time
  }

  def getEventTime(event: EventType): Option[Double] =
    events.get(event)

  val start = new Date()

  def curTime(): Double =
    (new Date()).getTime() - start.getTime()

  def didPress(
      event: EventType
  ): (Double, Option[Double]) => Option[Double] = {
    def _didPress(time: Double, lastTime: Option[Double]): Option[Double] = {
      val pressedTime = getEventTime(event)
      (pressedTime, lastTime) match {
        case (Some(pressedTime), Some(lastTime)) =>
          if (pressedTime >= lastTime) && (pressedTime <= time) then Some(pressedTime) else None
        case _ => None
      }
    }
    _didPress
  }

  def accumulate(
      factor: Int = 1
  ): (Boolean, Option[Int]) => (Int, Option[Int]) = {
    def _accumulate(increment: Boolean, past: Option[Int]): (Int, Option[Int]) = {
      val result = past match {
        case None => {
          (if increment then 1 else 0) * factor
        }
        case Some(pastValue) => {
          (if increment then 1 else 0) * factor + pastValue
        }
      }
      (result, Some(result))
    }
    _accumulate
  }

  def keepIfLarger(
      arg: Int,
      past: Option[Int]
  ): (Int, Option[Int]) = {
    val pastValue = past.getOrElse(0)
    val result = if (arg > pastValue) {
      arg
    } else {
      pastValue
    }
    (result, Some(result))
  }

  val SNAKE_SIZE = 20.0
  // Milliseconds per movement
  val PULSE_TIME: Double = 300.0
  def tick(
      args: (Double, (Boolean, Int)),
      past: (Option[(Double, Double)])
  ): (Option[Double], Option[(Double, Double)]) = {
    val (time, (stop, score)) = args
    val accumulatedTime = past.map(_._2).getOrElse(0.0)
    val pastTime = past.map(_._1)
    pastTime match {
      case Some(pastTime) => {
        val totalAccumulatedTime = (time - pastTime) + accumulatedTime
        val speedupFactor = ((score / 20.0) + 1).min(4)
        val effectivePulseTime = PULSE_TIME / speedupFactor
        if (totalAccumulatedTime > effectivePulseTime) {
          val pulses = (totalAccumulatedTime / effectivePulseTime).toInt
          val elapsedTime = pulses * effectivePulseTime
          val newTime = totalAccumulatedTime - elapsedTime
          if (!stop) {
            (Some(pulses * SNAKE_SIZE), Some((time, newTime)))
          } else {
            (None, Some((time, newTime)))
          }
        } else {
          (None, Some((time, totalAccumulatedTime)))
        }
      }
      case None =>
        (None, Some((time, 0)))
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
      speedPulse: Option[Double],
      direction: Direction
  ): Option[Vect2d] = {
    speedPulse.map((speed) => Vect2d(direction.x * speed, direction.y * speed))
  }

  def buttonStateLatch(
      arg: (Option[Double], Option[Double]),
      past: Option[Double]
  ): (Option[Double], Option[Double]) = {
    val (press, release) = arg
    val result = if (!release.isEmpty) {
      None
    } else if (!press.isEmpty) {
      Some(press.get)
    } else {
      past
    }
    (result, result)
  }

  def pauseLatch(
      input: (Option[Double], Option[Double]),
      past: Option[Boolean]
  ): (Boolean, Option[Boolean]) = {
    val (pausePressEvent, focusOut) = input
    val pausePress = !pausePressEvent.isEmpty
    val pastPauseState = past.getOrElse(false)
    val result = if (pausePress || (!pastPauseState && !focusOut.isEmpty)) {
      !pastPauseState
    } else {
      pastPauseState
    }
    (result, Some(result))
  }

  def positiveLatch(
      arg: Boolean,
      past: Option[Boolean]
  ): (Boolean, Option[Boolean]) = {
    val curValue = past.getOrElse(false)
    val output = if (curValue || arg) {
      true
    } else {
      curValue
    }
    (output, Some(output))
  }

  def snake(
      bounds: Rect
  ): ReactiveStreamAny[
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
    toAny(liftWithOutput(_snake)).map((output) => (output._1, output._2))
  }

  def food(bounds: Rect): ReactiveStreamAny[
    Option[List[Vect2d]],
    (Vect2d, Boolean)
  ] = {
    def _food(
        snakeArgument: Option[List[Vect2d]],
        past: Option[Vect2d]
    ): ((Vect2d, Boolean), Option[Vect2d]) = {
      val oldFoodPosition = past.getOrElse(Vect2d(SNAKE_SIZE * 3, SNAKE_SIZE * 4))
      val snake = snakeArgument.getOrElse(List())
      snake.headOption match {
        case None => ((oldFoodPosition, false), Some(oldFoodPosition))
        case Some(head) =>
          if (
            (head.x / SNAKE_SIZE).toInt == (oldFoodPosition.x / SNAKE_SIZE).toInt
            && (head.y / SNAKE_SIZE).toInt == (oldFoodPosition.y / SNAKE_SIZE).toInt
          ) {
            val random = new scala.util.Random
            lazy val newFoodPositionStream: LazyList[Vect2d] = Vect2d(
              random.nextInt((bounds.w / SNAKE_SIZE).toInt) * SNAKE_SIZE,
              random.nextInt((bounds.h / SNAKE_SIZE).toInt) * SNAKE_SIZE
            ) #:: newFoodPositionStream.map { _ =>
              Vect2d(
                random.nextInt((bounds.w / SNAKE_SIZE).toInt) * SNAKE_SIZE,
                random.nextInt((bounds.h / SNAKE_SIZE).toInt) * SNAKE_SIZE
              )
            }
            val newFood = newFoodPositionStream.find(!snake.contains(_)).get
            ((newFood, true), Some(newFood))
          } else {
            ((oldFoodPosition, false), Some(oldFoodPosition))
          }
      }
    }
    toAny(_food)
  }

  def makeButtonLatch(
      press: (Double, Option[Double]) => Option[Double],
      release: (Double, Option[Double]) => Option[Double]
  ): Reactive[(Option[Double], Double), Option[Double]] = {
    assumeInputSource({
      case (pastTime: Option[Double], time: Double) => {
        toAny(buttonStateLatch)
          .applyValue((press(time, pastTime), release(time, pastTime)))
      }
    })
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
      input: (Option[Double], Boolean, Boolean, Boolean),
      past: Option[(Boolean, Boolean)]
  ): (Boolean, Option[(Boolean, Boolean)]) = {
    val (tick, paused, gameOver, focusIn) = input
    val (pastPaused, pastGameOver) = past.getOrElse((false, false))
    val should = focusIn || !(tick.isEmpty && paused == pastPaused && gameOver == pastGameOver)
    (should, Some((paused, gameOver)))
  }

  def singleTruePulse(
      past: Option[(Double, Boolean)]
  ): (Option[Boolean], Boolean) = {
    val (_, pastValue) = past.getOrElse((0, false))
    if (pastValue) {
      (None, pastValue)
    } else {
      (Some(true), true)
    }
  }

  def onFrame(time: Double, drawOps: (Double) => Iterable[DrawOp]): Unit = {
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
      drawOps: (Double) => Iterable[DrawOp]
  ): Unit = {
    val time = (new Date()).getTime() - start.getTime()
    val drawOpsValue = drawOps(time)
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

  def withPastTime[T](f: (Double, Option[Double]) => T): Reactive[Double, T] =
    identity[Double]()
      .withPastOutput()
      .map({
        case (pastTime, time) => {
          f(time, pastTime)
        }
      })

  var didPressRState = create(
    withPastTime(didPress(EventType.RKeyPress))
  )

  var didFocusInState = create(
    withPastTime(didPress(EventType.FocusIn))
  )

  val drawing =
    assumeInputSource(
      (input: (Option[Double], Boolean, Boolean, Int, List[Vect2d], Vect2d, Int, Boolean)) =>
        val (tick, isGameOver, paused, score, snake, food, highScore, focusIn) = input
        toAny(shouldRedraw)
          .applyValue((tick, paused, isGameOver, focusIn))
          .map({ shouldRedraw =>
            if (shouldRedraw) {
              val bounds = Rect(0, 0, canvas.width, canvas.height)
              drawClearScreen(bounds) ::: drawFood(food)
                ::: drawSnake(snake) ::: drawScore(score)
                ::: drawHighScore(highScore) ::: drawPause(paused && !isGameOver) ::: drawGameOver(
                  isGameOver
                )
            } else {
              List()
            }
          })
    )
  var drawState = create(drawing)

  var mainState: Option[
    StatefulStream[Double, (Option[Double], Boolean, Boolean, Int, List[Vect2d], Vect2d)]
  ] =
    None
  var highScoreState = create(toAny(keepIfLarger))

  def stepsSinceLast(
      time: Double,
      past: Option[Double]
  ): ((Int, Double, Double), Option[Double]) = {
    val pastTime = past.getOrElse(0.0)
    var delta = time - pastTime
    val stepTime = (delta / 200.0).max(DELTA_T)
    var steps = (delta / stepTime).floor.toInt
    ((steps, stepTime, pastTime), Some(pastTime + stepTime * steps))
  }

  var stepsSinceLastState = create(toAny(stepsSinceLast))

  def main(args: Array[String]): Unit = {
    val bounds = Rect(0, 0, canvas.width, canvas.height)

    val upPress = didPress(EventType.UpKeyPress)
    val downPress = didPress(EventType.DownKeyPress)
    val leftPress = didPress(EventType.LeftKeyPress)
    val rightPress = didPress(EventType.RightKeyPress)

    val upRelease = didPress(EventType.UpKeyRelease)
    val downRelease = didPress(EventType.DownKeyRelease)
    val leftRelease = didPress(EventType.LeftKeyRelease)
    val rightRelease = didPress(EventType.RightKeyRelease)

    val pPress = didPress(EventType.PKeyPress)
    val focusOut = didPress(EventType.FocusOut)

    val resultStream = assumeInputSource((time: Double) => {
      val keyTuplesPre = sharedPair(
        sharedPair(
          makeButtonLatch(leftPress, leftRelease),
          makeButtonLatch(rightPress, rightRelease)
        ),
        sharedPair(
          makeButtonLatch(downPress, downRelease),
          makeButtonLatch(upPress, upRelease)
        )
      )

      val keyTuples = repeatPast(time)
        .flatMapSource { case (pastTime: Option[Double]) =>
          keyTuplesPre.applyValue((pastTime, time))
        }

      val keyValues = keyTuples.map(Keys.from_tuples)

      val pause =
        repeatPast(time)
          .flatMapSource({ case (pastTime: Option[Double]) =>
            val p = pPress(time, pastTime)
            val out = focusOut(time, pastTime)
            toAny(pauseLatch).applyValue((p, out))
          })

      val directionValidation = toAny(liftWithOutput(validatedCurrentDirection))

      val actualDirection =
        assumeInputSource((lastMovedDirection: Option[Direction]) => {
          keyValues
            .map(desiredDirections)
            .flatMapSource(desiredDirections => {
              directionValidation.applyValue(
                (desiredDirections, lastMovedDirection)
              )
            })
        })

      feedbackChannelSourceFlatMap((input: Option[(Direction, List[Vect2d], Boolean, Int)]) => {
        val (
          latchedDirection: Option[Direction],
          pastSnake: Option[List[Vect2d]],
          isGameOver: Boolean,
          score: Int
        ) =
          input
            .map(value => (Some(value._1), Some(value._2), value._3, value._4))
            .getOrElse((None, None, false, 0))

        pause
          .flatMapSource(paused => {
            pair(
              actualDirection
                .applyValue(latchedDirection),
              toAny(tick)
                .applyValue((time, (isGameOver || paused, score)))
            )
              .flatMapSource({ (direction, tick) =>
                cachedSource(
                  (paused, direction, tick),
                  food(bounds)
                    .applyValue(pastSnake)
                    .flatMapSource({
                      case (foodPos, didEatFood) => {
                        latchValue(
                          Direction(0, 1),
                          tick.map(_ => direction)
                        ).flatMapSource(latchedDirection => {
                          snake(bounds)
                            .applyValue((movement(tick, latchedDirection), didEatFood))
                            .flatMapSource(
                              { case (snake, isGameOverCurrent) =>
                                pair(
                                  toAny(accumulate(1))
                                    .applyValue(
                                      didEatFood
                                    ),
                                  toAny(positiveLatch)
                                    .applyValue(isGameOverCurrent)
                                )
                                  .map({
                                    case (score, isGameOver) => {
                                      (
                                        (latchedDirection, snake, isGameOver, score),
                                        (tick, isGameOver, paused, score, snake, foodPos)
                                      )
                                    }
                                  })
                              }
                            )
                        })
                      }
                    })
                )
              })
          })

      })
    })

    mainState = Some(create(resultStream))

    val drawOps = (time: Double) => {
      val (steps, stepTime, pastTime) = stepsSinceLastState.run(time)
      var ops: List[DrawOp] = List()
      for (step <- 0.until(steps)) {
        val didPressR = didPressRState.run(time)

        if (!didPressR.isEmpty) {
          mainState.foreach { _.clear() }
        }

        mainState match {
          case None =>
          case Some(mainState) => {
            val (tick, isGameOver, paused, score, snake, foodPos) =
              mainState.run(pastTime + step * stepTime)
            val highScore = highScoreState.run(score)
            val didFocusIn = !didFocusInState.run(time).isEmpty
            ops = drawState.run(
              (tick, isGameOver, paused, score, snake, foodPos, highScore, didFocusIn)
            )
          }
        }
      }

      ops
    }

    document.addEventListener(
      "keydown",
      (e: dom.KeyboardEvent) =>
        e.key match {
          case "r" | "R" =>
            record(EventType.RKeyPress)
          case "ArrowLeft" =>
            record(EventType.LeftKeyPress)
          case "ArrowRight" =>
            record(EventType.RightKeyPress)
          case "ArrowUp" =>
            record(EventType.UpKeyPress)
          case "ArrowDown" =>
            record(EventType.DownKeyPress)
          case "p" | "P" =>
            record(EventType.PKeyPress)
          case _ => ()
        }
    )
    document.addEventListener(
      "keyup",
      (e: dom.KeyboardEvent) => {
        e.key match {
          case "ArrowLeft" =>
            record(EventType.LeftKeyRelease)
          case "ArrowRight" =>
            record(EventType.RightKeyRelease)
          case "ArrowUp" =>
            record(EventType.UpKeyRelease)
          case "ArrowDown" =>
            record(EventType.DownKeyRelease)
          case _ => ()
        }
      }
    )
    document.addEventListener(
      "visibilitychange",
      (e: dom.Event) => {
        if (document.visibilityState == "hidden") {
          record(EventType.FocusOut)
        } else {
          record(EventType.FocusIn)
        }
      }
    )

    window.requestAnimationFrame((timestamp: Double) => {
      TutorialApp.onFrame(timestamp, drawOps)
    })
  }
}
