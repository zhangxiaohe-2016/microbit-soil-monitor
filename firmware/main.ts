// USB serial debugging mode. Connect the micro:bit directly to the computer.
// A larger moisture value means the probe is drier.
const DRY_START_WATERING = 200
const WET_STOP_WATERING = 160
let watering = false

function setWatering(enabled: boolean) {
    if (watering == enabled) return
    watering = enabled
    pins.servoWritePin(AnalogPin.P1, enabled ? 180 : 90)
    serial.writeLine(enabled ? "AUTO,OPEN" : "AUTO,CLOSE")
}

// Begin in the safe, closed position: 90 degrees means watering is off.
pins.servoWritePin(AnalogPin.P1, 90)

serial.onDataReceived(serial.delimiters(Delimiters.NewLine), function () {
    const command = serial.readUntil(serial.delimiters(Delimiters.NewLine))
    if (command == "SERVO:0" || command == "SERVO:90" || command == "SERVO:180") {
        const angle = parseInt(command.substr(6))
        pins.servoWritePin(AnalogPin.P1, angle)
        serial.writeLine("OK,SERVO," + angle)
    } else {
        serial.writeLine("ERROR,unknown command: " + command)
    }
})

basic.forever(function () {
    const moisture = pins.analogReadPin(AnalogPin.P0)
    const temperature = input.temperature()
    serial.writeLine("DATA,moisture=" + moisture + ",temperature=" + temperature)
    if (moisture >= DRY_START_WATERING) {
        setWatering(true)
    } else if (moisture <= WET_STOP_WATERING) {
        setWatering(false)
    }
    basic.showNumber(moisture)
    basic.pause(1000)
})
