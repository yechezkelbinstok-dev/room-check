import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255, opacity: 1)
    }
}

/// The Android app's colours, one for one, so the two read as the same app.
enum RC {
    static let bg = Color(hex: 0xF2F2F7)
    static let card = Color.white
    static let sep = Color(hex: 0xE4E4E9)
    static let text = Color(hex: 0x111114)
    static let sub = Color(hex: 0x8A8A8E)
    static let sub2 = Color(hex: 0x63656B)
    static let blue = Color(hex: 0x0A63C4)
    static let blueL = Color(hex: 0xEAF1FB)
    static let green = Color(hex: 0x12795A)
    static let greenL = Color(hex: 0xE6F4EE)
    static let red = Color(hex: 0xC42B21)
    static let redL = Color(hex: 0xFBEBEA)
    static let grey = Color(hex: 0x8A8A8E)
    static let greyL = Color(hex: 0xEEEEF1)
    static let wall = Color(hex: 0x24262D)
    static let floor = Color(hex: 0xFBFBFA)
    static let bunk = Color(hex: 0xB7B9C2)
    static let segment = Color(hex: 0xE7E7EC)
    static let buttonOff = Color(hex: 0xEDEEF2)
    static let glyphOff = Color(hex: 0x9A9CA6)
}
