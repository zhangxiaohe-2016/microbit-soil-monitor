bluetooth.startIOPinService()
bluetooth.startTemperatureService()

basic.forever(function () {
    basic.showNumber(pins.analogReadPin(AnalogPin.P0))
    basic.pause(1000)
})
