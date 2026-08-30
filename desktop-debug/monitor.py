"""USB serial monitor and P1 servo controller for the micro:bit soil monitor."""

import argparse
import threading

import serial
from serial.tools import list_ports


def print_ports():
    print("Available serial ports:")
    for port in list_ports.comports():
        print(f"  {port.device} {port.description}")
    print("\nUsage: python monitor.py COM5")


def read_microbit(connection):
    while connection.is_open:
        try:
            line = connection.readline().decode("utf-8", errors="replace").strip()
            if line:
                print(f"[micro:bit] {line}")
        except serial.SerialException as error:
            print(f"Serial error: {error}")
            return


def main():
    parser = argparse.ArgumentParser(description="Monitor a micro:bit over USB serial.")
    parser.add_argument("port", nargs="?", help="Serial port, for example COM5")
    args = parser.parse_args()
    if not args.port:
        print_ports()
        return

    try:
        connection = serial.Serial(args.port, 115200, timeout=1)
    except serial.SerialException as error:
        raise SystemExit(f"Cannot open {args.port}: {error}")

    print(f"Connected to {args.port}. Commands: 0, 90, 180, or quit")
    reader = threading.Thread(target=read_microbit, args=(connection,), daemon=True)
    reader.start()
    try:
        while True:
            command = input().strip()
            if command in {"quit", "exit"}:
                return
            if command in {"0", "90", "180"}:
                try:
                    connection.write(f"SERVO:{command}\n".encode("ascii"))
                except serial.SerialException:
                    print("USB 连接暂时断开了。请拔掉 micro:bit 的 USB 线，再插回去，然后重新运行这个程序。")
                    return
            else:
                print("Enter 0, 90, 180, or quit.")
    finally:
        connection.close()


if __name__ == "__main__":
    main()
