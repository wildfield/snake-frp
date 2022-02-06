# A Snake with an FRP implemention

This uses ScalaJS. You can play it at https://wildfield.github.io/snake/

![snake](https://user-images.githubusercontent.com/4116417/152702236-31c3e6b1-02c9-419e-aaa9-a451fce4011b.png)

Controls:
- Arrow keys to move
- P to pause
- R to reset

A [gist](https://gist.github.com/wildfield/5cef98101b5b37d117afa4c29573b497) explaining what's going on

To build and run locally:

1. `sbt` -> `fastLinkJS` (http://www.scala-js.org/doc/project/building.html)
2. `python3 -m http.server`
3. http://localhost:8000/index-dev.html
