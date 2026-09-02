package com.rabbithole.musicbbit.ui.theme

import androidx.compose.ui.graphics.Color

// Brand Colors — Light Theme
// Coral40 darkened from 0xFFD95C43 (3.8:1) to 0xFFC24832 (4.9:1) so onPrimary white
// text passes WCAG 2.1 AA (4.5:1) on primary buttons and selected day chips.
val Coral40 = Color(0xFFC24832)
val Slate40 = Color(0xFF5D6D7E)
val Amber40 = Color(0xFFD4A017)

// Brand Colors — Dark Theme
val Coral80 = Color(0xFFFF8A75)
val Slate80 = Color(0xFF9AA5B1)
val Amber80 = Color(0xFFFFD54F)

// Neutral tokens — Light Theme (tinted toward Coral, low chroma)
val BackgroundLight = Color(0xFFFDF6F2)
val SurfaceLight = Color(0xFFFDF6F2)
val SurfaceVariantLight = Color(0xFFF4E8E2)
val OnSurfaceVariantLight = Color(0xFF5C4035)
val OutlineLight = Color(0xFF8A6F65)
val OutlineVariantLight = Color(0xFFD9C5BD)

// Neutral tokens — Dark Theme (tinted toward Coral, low chroma)
val BackgroundDark = Color(0xFF1B1715)
val SurfaceDark = Color(0xFF1B1715)
val SurfaceVariantDark = Color(0xFF3D2F2A)
val OnSurfaceVariantDark = Color(0xFFD9C5BD)
val OutlineDark = Color(0xFF9C8278)
val OutlineVariantDark = Color(0xFF5C4035)

// Container tokens — Light Theme (derived from Coral/Slate/Amber; without these
// Material3 falls back to baseline purple for primaryContainer etc.)
val PrimaryContainerLight = Color(0xFFFBD9CE)
val OnPrimaryContainerLight = Color(0xFF4A1D14)
val SecondaryContainerLight = Color(0xFFDCE4EB)
val OnSecondaryContainerLight = Color(0xFF1D2930)
val TertiaryContainerLight = Color(0xFFF5E0BC)
val OnTertiaryContainerLight = Color(0xFF42320A)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)

val SurfaceContainerLowestLight = Color(0xFFFFFAF7)
val SurfaceContainerLowLight = Color(0xFFF9F0EA)
val SurfaceContainerLight = Color(0xFFF3E8E1)
val SurfaceContainerHighLight = Color(0xFFEDE0D8)
val SurfaceContainerHighestLight = Color(0xFFE7D8CF)

// Container tokens — Dark Theme
val PrimaryContainerDark = Color(0xFF6E3122)
val OnPrimaryContainerDark = Color(0xFFFFDAD1)
val SecondaryContainerDark = Color(0xFF424E58)
val OnSecondaryContainerDark = Color(0xFFD7E3EC)
val TertiaryContainerDark = Color(0xFF6E5D30)
val OnTertiaryContainerDark = Color(0xFFF5E0BC)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)

val SurfaceContainerLowestDark = Color(0xFF161210)
val SurfaceContainerLowDark = Color(0xFF241E1B)
val SurfaceContainerDark = Color(0xFF292220)
val SurfaceContainerHighDark = Color(0xFF342B28)
val SurfaceContainerHighestDark = Color(0xFF3F3430)
