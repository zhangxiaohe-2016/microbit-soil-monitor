basic.showIcon(IconNames.Yes)
basic.pause(500)

basic.forever(function () {
    basic.showArrow(ArrowNames.West)
    pins.servoWritePin(AnalogPin.P1, 45)
    basic.pause(1500)

    basic.showIcon(IconNames.SmallSquare)
    pins.servoWritePin(AnalogPin.P1, 90)
    basic.pause(1500)

    basic.showArrow(ArrowNames.East)
    pins.servoWritePin(AnalogPin.P1, 135)
    basic.pause(1500)

    basic.showIcon(IconNames.SmallSquare)
    pins.servoWritePin(AnalogPin.P1, 90)
    basic.pause(1500)
})
