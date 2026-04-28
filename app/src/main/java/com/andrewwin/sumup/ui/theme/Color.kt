package com.andrewwin.sumup.ui.theme

import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════════════════════════════════════
// Tonal Palette — seed color #4A90D9 (чистий новинний синій)
// Згенеровано за M3 HCT color space (Hue 220, Chroma 48)
// ════════════════════════════════════════════════════════════════════════════

// Primary tones
val Primary10 = Color(0xFF001B3D)
val Primary20 = Color(0xFF003064)
val Primary30 = Color(0xFF00468F)
val Primary40 = Color(0xFF1060B0)   // ← основний акцент
val Primary80 = Color(0xFFAAC7FF)   // ← primary у dark theme
val Primary90 = Color(0xFFD6E3FF)
val Primary100 = Color(0xFFFFFFFF)

// Secondary tones (менш насичений той самий hue)
val Secondary30 = Color(0xFF29447A)
val Secondary80 = Color(0xFFB0C6FF)
val Secondary90 = Color(0xFFDDE3FF)

// Neutral tones (майже ахроматичні, легкий синій відтінок)
val Neutral6 = Color(0xFF0F1117)   // background dark
val Neutral10 = Color(0xFF181C23)   // surface dark
val Neutral12 = Color(0xFF1C2028)   // surfaceContainer
val Neutral17 = Color(0xFF21252E)   // surfaceContainerHigh
val Neutral22 = Color(0xFF262B34)   // surfaceContainerHighest
val Neutral90 = Color(0xFFE2E2EC)   // onBackground dark
val Neutral95 = Color(0xFFF1F0FA)   // background light
val Neutral99 = Color(0xFFFFFBFF)   // surface light
val Neutral100 = Color(0xFFFFFFFF)

// Neutral Variant tones (trохи більше chroma)
val NeutralVariant30 = Color(0xFF424659)
val NeutralVariant50 = Color(0xFF6C7088)   // onSurfaceVariant dark (secondary text)
val NeutralVariant60 = Color(0xFF868AA4)
val NeutralVariant80 = Color(0xFFBFC2DC)
val NeutralVariant90 = Color(0xFFDCDFF5)

// Error tones
val Error40 = Color(0xFFBA1A1A)
val Error80 = Color(0xFFFFB4AB)
val Error90 = Color(0xFFFFDAD6)
val Error10 = Color(0xFF410002)

// ════════════════════════════════════════════════════════════════════════════
// Named aliases — використовуй ці у Theme.kt та компонентах
// ════════════════════════════════════════════════════════════════════════════

// Dark theme
val PrimaryBlue = Primary80            // #AAC7FF
val OnPrimaryBlue = Primary20            // #003064
val PrimaryContainerBlue = Primary30            // #00468F
val OnPrimaryContainerBlue = Primary90            // #D6E3FF

val SecondaryContainerDark = Secondary30          // #29447A
val OnSecondaryContainerDark = Secondary90          // #DDE3FF

val BackgroundDark = Neutral6             // #0F1117
val OnBackgroundDark = Neutral90            // #E2E2EC

val SurfaceDark = Neutral10            // #181C23
val SurfaceVariantDark = NeutralVariant30     // #424659
val OnSurfaceVariantDark = NeutralVariant80     // #BFC2DC  ← secondary text

val SurfaceContainerLowDark = Neutral10        // #181C23
val SurfaceContainerDark = Neutral12        // #1C2028
val SurfaceContainerHighDark = Neutral17        // #21252E
val SurfaceContainerHighestDark = Neutral22        // #262B34

val OutlineDark = NeutralVariant50     // #6C7088
val OutlineVariantDark = NeutralVariant30     // #424659

// Light theme
val PrimaryLight = Primary40            // #1060B0
val OnPrimaryLight = Primary100           // #FFFFFF
val PrimaryContainerLight = Primary90            // #D6E3FF
val OnPrimaryContainerLight = Primary10            // #001B3D

val SecondaryContainerLight = Secondary90          // #DDE3FF
val OnSecondaryContainerLight = Secondary30         // #29447A

val BackgroundLight = Neutral95            // #F1F0FA
val OnBackgroundLight = Neutral10            // #181C23

val SurfaceLight = Neutral99            // #FFFBFF
val SurfaceVariantLight = NeutralVariant90     // #DCE3FF
val OnSurfaceVariantLight = NeutralVariant30     // #424659

val SurfaceContainerLowLight = Neutral95        // #F1F0FA
val SurfaceContainerLight_ = Neutral99        // #FFFBFF
val SurfaceContainerHighLight = Color(0xFFE6E5EF)
val SurfaceContainerHighestLight = Color(0xFFE0DFE9)

val OutlineLight = NeutralVariant50     // #6C7088
val OutlineVariantLight = NeutralVariant80     // #BFC2DC

// ════════════════════════════════════════════════════════════════════════════
// Category tags — tonal, не брудні
// ════════════════════════════════════════════════════════════════════════════
val TagDefault = Color(0xFF2A2F42)   // нейтральний чіп
val OnTagDefault = Color(0xFFBFC2DC)

val TagBreaking = Color(0xFF93000A)   // error ramp
val OnTagBreaking = Color(0xFFFFDAD6)

val TagPolitics = Color(0xFF00468F)   // primary ramp
val OnTagPolitics = Color(0xFFD6E3FF)

val TagEconomy = Color(0xFF006C4C)   // teal/green
val OnTagEconomy = Color(0xFFB7F1D4)

val TagSociety = Color(0xFF5C4000)   // amber
val OnTagSociety = Color(0xFFFFDFA0)

// ════════════════════════════════════════════════════════════════════════════
// Settings Icon Palette — for settings sections
// ════════════════════════════════════════════════════════════════════════════
val IconBgOrangeDark = Color(0xFF3B2D26)
val IconOrangeDark = Color(0xFFE89A6A)

val IconBgGreenDark = Color(0xFF263B2D)
val IconGreenDark = Color(0xFF86D5A2)

val IconBgPurpleDark = Color(0xFF2D263B)
val IconPurpleDark = Color(0xFF9E86D5)

val IconBgBlueDark = Color(0xFF262D3B)
val IconBlueDark = Color(0xFF86A2D5)

val IconBgGreyDark = Color(0xFF2E3036)
val IconGreyDark = Color(0xFFB1B5BD)

val IconBgOrangeLight = Color(0xFFFFDCC9)
val IconOrangeLight = Color(0xFF99440D)

val IconBgGreenLight = Color(0xFFC7F8D8)
val IconGreenLight = Color(0xFF22643D)

val IconBgPurpleLight = Color(0xFFDFCDFE)
val IconPurpleLight = Color(0xFF4C3089)

val IconBgBlueLight = Color(0xFFD6E3FF)
val IconBlueLight = Color(0xFF003064)

val IconBgGreyLight = Color(0xFFDCE1EB)
val IconGreyLight = Color(0xFF454952)
