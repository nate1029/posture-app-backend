package com.example.neckguard.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Global state for Dark Mode and Test Gradients (Persisted in MainActivity)
var isDarkModeState by mutableStateOf(false)
var selectedGradientIndex by mutableStateOf(-1) // -1 is the programmatic gradient, 0-5 are the image gradients
var gradientVerticalBias by mutableStateOf(0f)

// ── TextNeck Design System ─────────────────────────────────────────────

// Light Theme Raw Colors (Including Matcha Latte Defaults)
private val LightTeal          = Color(0xFF0D7377)
private val LightTealSoft      = Color(0xFF5EADB0)
private val LightTealWash      = Color(0xFFE4F0EF)
private val LightAmber         = Color(0xFFD4943F)
private val LightAlert         = Color(0xFFCF4F44)
private val LightSand          = Color(0xFFD5C8A8)
private val LightBg            = Color(0xFFF1F5EB) // Matcha Base
private val LightSlate         = Color(0xFF2C3E50)
private val LightSlateMuted    = Color(0xFF7F9099)
private val LightWhite         = Color(0xFFFFFFFF)

private val LightHeroBgTop     = Color(0xFFE6EED9) // Matcha Mist Gradient Top
private val LightHeroBgBottom  = Color(0xFFF5F3EE) // Matcha Mist Gradient Bottom
private val LightCapsuleBg     = Color(0xFFE1EAD8) // Matcha Latte
private val LightCapsuleBtnBg  = Color(0xFFD2DEC6)

private val LightGoodBg        = Color(0xFFD3F0E2)
private val LightGoodText      = Color(0xFF198053)
private val LightModBg         = Color(0xFFFDE8D0)
private val LightModText       = Color(0xFFA0540A)
private val LightPoorBg        = Color(0xFFF9DEDE)
private val LightPoorText      = Color(0xFFC94040)

private val LightCardTealWash  = Color(0xFFCFEAE8)
private val LightCardGolden    = Color(0xFFEFDC9B)
private val LightCardPeriwinkle= Color(0xFFC5D1F6)
private val LightCardSand      = Color(0xFFDACDB0)
private val LightCardBorder    = Color(0x0D000000)

// Dark Theme Raw Colors (Sleek, Glowing Midnight Vibes)
private val DarkTeal           = Color(0xFF4FD1C5) // Brighter glowing teal
private val DarkTealSoft       = Color(0xFF81E6D9)
private val DarkTealWash       = Color(0xFF1E293B) // Dark Slate Wash
private val DarkAmber          = Color(0xFFFBD38D) // Pale Amber
private val DarkAlert          = Color(0xFFFC8181) // Glowing Red
private val DarkSand           = Color(0xFFB7A57A)
private val DarkBg             = Color(0xFF0F172A) // Very Dark Blue/Black
private val DarkSlate          = Color(0xFFF8FAFC) // Near White for text
private val DarkSlateMuted     = Color(0xFF94A3B8)
private val DarkWhite          = Color(0xFF1E293B) // Card surface in dark mode

private val DarkHeroBgTop      = Color(0xFF1A1F14) // Dark Matcha Gradient Top
private val DarkHeroBgBottom   = Color(0xFF0F172A) // Dark Matcha Gradient Bottom
private val DarkCapsuleBg      = Color(0xFF1E293B) // Card color
private val DarkCapsuleBtnBg   = Color(0xFF334155) // Inner pill

private val DarkGoodBg         = Color(0xFF064E3B)
private val DarkGoodText       = Color(0xFF34D399)
private val DarkModBg          = Color(0xFF7B341E)
private val DarkModText        = Color(0xFFFBD38D)
private val DarkPoorBg         = Color(0xFF7F1D1D)
private val DarkPoorText       = Color(0xFFF87171)

private val DarkCardTealWash   = Color(0xFF134E4A)
private val DarkCardGolden     = Color(0xFF713F12)
private val DarkCardPeriwinkle = Color(0xFF312E81)
private val DarkCardSand       = Color(0xFF422006)
private val DarkCardBorder     = Color(0x33FFFFFF)

// ── Dynamic Properties (Reads current state automatically) ─────────────
val Teal: Color get() = if (isDarkModeState) DarkTeal else LightTeal
val TealSoft: Color get() = if (isDarkModeState) DarkTealSoft else LightTealSoft
val TealWash: Color get() = if (isDarkModeState) DarkTealWash else LightTealWash
val Amber: Color get() = if (isDarkModeState) DarkAmber else LightAmber
val Alert: Color get() = if (isDarkModeState) DarkAlert else LightAlert
val Sand: Color get() = if (isDarkModeState) DarkSand else LightSand
val Bg: Color get() = if (isDarkModeState) DarkBg else LightBg
val Slate: Color get() = if (isDarkModeState) DarkSlate else LightSlate
val SlateMuted: Color get() = if (isDarkModeState) DarkSlateMuted else LightSlateMuted
val White: Color get() = if (isDarkModeState) DarkWhite else LightWhite

private val HeroBgTop: Color get() = if (isDarkModeState) DarkHeroBgTop else LightHeroBgTop
private val HeroBgBottom: Color get() = if (isDarkModeState) DarkHeroBgBottom else LightHeroBgBottom

val HeroBg: Color get() = HeroBgTop // Fallback for statusBar

val HeroBgGradient: Brush get() = Brush.verticalGradient(
    colors = listOf(HeroBgTop, HeroBgBottom)
)
val CapsuleBg: Color get() = if (isDarkModeState) DarkCapsuleBg else LightCapsuleBg
val CapsuleBtnBg: Color get() = if (isDarkModeState) DarkCapsuleBtnBg else LightCapsuleBtnBg

val GoodBg: Color get() = if (isDarkModeState) DarkGoodBg else LightGoodBg
val GoodText: Color get() = if (isDarkModeState) DarkGoodText else LightGoodText
val ModBg: Color get() = if (isDarkModeState) DarkModBg else LightModBg
val ModText: Color get() = if (isDarkModeState) DarkModText else LightModText
val PoorBg: Color get() = if (isDarkModeState) DarkPoorBg else LightPoorBg
val PoorText: Color get() = if (isDarkModeState) DarkPoorText else LightPoorText

val CardTealWash: Color get() = if (isDarkModeState) DarkCardTealWash else LightCardTealWash
val CardGolden: Color get() = if (isDarkModeState) DarkCardGolden else LightCardGolden
val CardPeriwinkle: Color get() = if (isDarkModeState) DarkCardPeriwinkle else LightCardPeriwinkle
val CardSand: Color get() = if (isDarkModeState) DarkCardSand else LightCardSand
val CardBorder: Color get() = if (isDarkModeState) DarkCardBorder else LightCardBorder