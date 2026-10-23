import SwiftUI
import UIKit

/// Apple's own colours, by what each is for, so the app follows light and dark mode the way the
/// phone's own apps do. Only the marks' green and red carry a meaning of their own.
enum RC {
    /// The page behind everything - the grey of Settings, black in dark mode.
    static let page = Color(UIColor.systemGroupedBackground)
    /// A card on that page - white, or the dark grey of a dark-mode list.
    static let card = Color(UIColor.secondarySystemGroupedBackground)
    /// The room's floor, set back from its card, and the beds standing on it.
    static let floor = Color(UIColor.systemGroupedBackground)
    static let bed = Color(UIColor.secondarySystemGroupedBackground)
    static let bedEdge = Color(UIColor.separator)
    static let wall = Color(UIColor.label)
    /// A mark button not pressed, and the glyph on it.
    static let off = Color(UIColor.tertiarySystemFill)
    static let offGlyph = Color(UIColor.secondaryLabel)
    static let green = Color(UIColor.systemGreen)
    static let red = Color(UIColor.systemRed)
    static let greenSoft = Color(UIColor.systemGreen).opacity(0.16)
    static let redSoft = Color(UIColor.systemRed).opacity(0.16)
    /// Someone off the walk - excused, or not in town.
    static let offWalk = Color(UIColor.systemGray5)
    static let bunk = Color(UIColor.tertiaryLabel)
}
