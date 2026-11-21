/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.materialkolor.dynamiccolor

import com.materialkolor.dynamiccolor.ToneDeltaPair.DeltaConstraint
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.DynamicScheme.Platform
import com.materialkolor.scheme.Variant

/**
 * [ColorSpec] implementation for the 2026 spec (CMF — Color, Material, Finish).
 *
 * Ported directly from upstream material-foundation/material-color-utilities
 * (`kotlin/dynamiccolor/ColorSpec2026.kt` at revision `bd811e4ca2ab94c4ce3cadc89c19b1820b9704b3`).
 */
public open class ColorSpec2026 : ColorSpec2025() {

    private fun findBestToneForChroma(
        hue: Double,
        chroma: Double,
        tone: Double,
        byDecreasingTone: Boolean,
    ): Double {
        var tone = tone
        var answer = tone
        var bestCandidate = Hct.from(hue, chroma, answer)
        while (bestCandidate.chroma < chroma) {
            if (tone < 0 || tone > 100) {
                break
            }
            tone += if (byDecreasingTone) -1.0 else 1.0
            val newCandidate = Hct.from(hue, chroma, tone)
            if (bestCandidate.chroma < newCandidate.chroma) {
                bestCandidate = newCandidate
                answer = tone
            }
        }
        return answer
    }

    private fun tMaxC(
        palette: TonalPalette,
        lowerBound: Double = 0.0,
        upperBound: Double = 100.0,
        chromaMultiplier: Double = 1.0,
    ): Double {
        val answer = findBestToneForChroma(palette.hue, palette.chroma * chromaMultiplier, 100.0, true)
        return answer.coerceIn(lowerBound, upperBound)
    }

    private fun tMinC(
        palette: TonalPalette,
        lowerBound: Double = 0.0,
        upperBound: Double = 100.0,
    ): Double {
        val answer = findBestToneForChroma(palette.hue, palette.chroma, 0.0, false)
        return answer.coerceIn(lowerBound, upperBound)
    }

    private fun getContrastCurve(defaultContrast: Double): ContrastCurve =
        when (defaultContrast) {
            1.5 -> ContrastCurve(1.5, 1.5, 3.0, 5.5)
            3.0 -> ContrastCurve(3.0, 3.0, 4.5, 7.0)
            4.5 -> ContrastCurve(4.5, 4.5, 7.0, 11.0)
            6.0 -> ContrastCurve(6.0, 6.0, 7.0, 11.0)
            7.0 -> ContrastCurve(7.0, 7.0, 11.0, 21.0)
            9.0 -> ContrastCurve(9.0, 9.0, 11.0, 21.0)
            11.0 -> ContrastCurve(11.0, 11.0, 21.0, 21.0)
            21.0 -> ContrastCurve(21.0, 21.0, 21.0, 21.0)
            else -> ContrastCurve(defaultContrast, defaultContrast, 7.0, 21.0)
        }

    // Surfaces [S]

    public override fun surface(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("surface")
            .setPalette { it.neutralPalette }
            .setTone { if (it.variant == Variant.CMF) (if (it.isDark) 4.0 else 98.0) else 0.0 }
            .setIsBackground(true)
            .build()
        return super
            .surface()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun surfaceDim(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("surface_dim")
            .setPalette { it.neutralPalette }
            .setTone { if (it.variant == Variant.CMF) (if (it.isDark) 4.0 else 87.0) else 0.0 }
            .setIsBackground(true)
            .setChromaMultiplier {
                if (it.variant == Variant.CMF) (if (it.isDark) 1.0 else 1.7) else 0.0
            }
            .build()
        return super
            .surfaceDim()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun surfaceBright(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("surface_bright")
            .setPalette { it.neutralPalette }
            .setTone { if (it.variant == Variant.CMF) (if (it.isDark) 18.0 else 98.0) else 0.0 }
            .setIsBackground(true)
            .setChromaMultiplier {
                if (it.variant == Variant.CMF) (if (it.isDark) 1.7 else 1.0) else 0.0
            }
            .build()
        return super
            .surfaceBright()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun surfaceContainerLowest(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("surface_container_lowest")
            .setPalette { it.neutralPalette }
            .setTone { if (it.variant == Variant.CMF) (if (it.isDark) 0.0 else 100.0) else 0.0 }
            .setIsBackground(true)
            .build()
        return super
            .surfaceContainerLowest()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun surfaceContainerLow(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("surface_container_low")
            .setPalette { it.neutralPalette }
            .setTone { if (it.variant == Variant.CMF) (if (it.isDark) 6.0 else 96.0) else 0.0 }
            .setIsBackground(true)
            .setChromaMultiplier { if (it.variant == Variant.CMF) 1.25 else 0.0 }
            .build()
        return super
            .surfaceContainerLow()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun surfaceContainer(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("surface_container")
            .setPalette { it.neutralPalette }
            .setTone { if (it.variant == Variant.CMF) (if (it.isDark) 9.0 else 94.0) else 0.0 }
            .setIsBackground(true)
            .setChromaMultiplier { if (it.variant == Variant.CMF) 1.4 else 0.0 }
            .build()
        return super
            .surfaceContainer()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun surfaceContainerHigh(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("surface_container_high")
            .setPalette { it.neutralPalette }
            .setTone { if (it.variant == Variant.CMF) (if (it.isDark) 12.0 else 92.0) else 0.0 }
            .setIsBackground(true)
            .setChromaMultiplier { if (it.variant == Variant.CMF) 1.5 else 0.0 }
            .build()
        return super
            .surfaceContainerHigh()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun surfaceContainerHighest(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("surface_container_highest")
            .setPalette { it.neutralPalette }
            .setTone { if (it.variant == Variant.CMF) (if (it.isDark) 15.0 else 90.0) else 0.0 }
            .setIsBackground(true)
            .setChromaMultiplier { if (it.variant == Variant.CMF) 1.7 else 0.0 }
            .build()
        return super
            .surfaceContainerHighest()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onSurface(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_surface")
            .setPalette { it.neutralPalette }
            .setChromaMultiplier { if (it.variant == Variant.CMF) 1.7 else 0.0 }
            .setBackground { highestSurface(it) }
            .setContrastCurve { getContrastCurve(if (it.isDark) 11.0 else 9.0) }
            .build()
        return super
            .onSurface()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onSurfaceVariant(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_surface_variant")
            .setPalette { it.neutralPalette }
            .setChromaMultiplier { if (it.variant == Variant.CMF) 1.7 else 0.0 }
            .setBackground { highestSurface(it) }
            .setContrastCurve { getContrastCurve(if (it.isDark) 6.0 else 4.5) }
            .build()
        return super
            .onSurfaceVariant()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun outline(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("outline")
            .setPalette { it.neutralPalette }
            .setChromaMultiplier { if (it.variant == Variant.CMF) 1.7 else 0.0 }
            .setBackground { highestSurface(it) }
            .setContrastCurve { getContrastCurve(3.0) }
            .build()
        return super
            .outline()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun outlineVariant(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("outline_variant")
            .setPalette { it.neutralPalette }
            .setChromaMultiplier { if (it.variant == Variant.CMF) 1.7 else 0.0 }
            .setBackground { highestSurface(it) }
            .setContrastCurve { getContrastCurve(1.5) }
            .build()
        return super
            .outlineVariant()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun inverseSurface(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("inverse_surface")
            .setPalette { it.neutralPalette }
            .setTone { if (it.isDark) 98.0 else 4.0 }
            .setIsBackground(true)
            .setChromaMultiplier { if (it.variant == Variant.CMF) 1.7 else 0.0 }
            .build()
        return super
            .inverseSurface()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun inverseOnSurface(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("inverse_on_surface")
            .setPalette { it.neutralPalette }
            .setBackground { inverseSurface() }
            .setContrastCurve { getContrastCurve(7.0) }
            .build()
        return super
            .inverseOnSurface()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    // Primaries [P]

    public override fun primary(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("primary")
            .setPalette { it.primaryPalette }
            .setTone {
                if (it.sourceColorHct.chroma <= 12) {
                    if (it.isDark) 80.0 else 40.0
                } else {
                    it.sourceColorHct.tone
                }
            }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setContrastCurve { getContrastCurve(4.5) }
            .setToneDeltaPair {
                if (it.platform == Platform.PHONE) {
                    ToneDeltaPair(
                        roleA = primaryContainer(),
                        roleB = primary(),
                        delta = 5.0,
                        polarity = TonePolarity.RELATIVE_LIGHTER,
                        deltaConstraint = DeltaConstraint.FARTHER,
                    )
                } else {
                    null
                }
            }
            .build()
        return super
            .primary()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun primaryDim(): DynamicColor {
        // Remapped to primary in 2026 spec.
        val color2026 = primary().toBuilder().setName("primary_dim").build()
        return super
            .primaryDim()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onPrimary(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_primary")
            .setPalette { it.primaryPalette }
            .setBackground { primary() }
            .setContrastCurve { getContrastCurve(6.0) }
            .build()
        return super
            .onPrimary()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun primaryContainer(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("primary_container")
            .setPalette { it.primaryPalette }
            .setTone {
                if (!it.isDark && it.sourceColorHct.chroma <= 12) {
                    90.0
                } else if (it.sourceColorHct.tone > 55) {
                    it.sourceColorHct.tone.coerceIn(61.0, 90.0)
                } else {
                    it.sourceColorHct.tone.coerceIn(30.0, 49.0)
                }
            }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setContrastCurve { if (it.contrastLevel > 0) getContrastCurve(1.5) else null }
            .build()
        return super
            .primaryContainer()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onPrimaryContainer(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_primary_container")
            .setPalette { it.primaryPalette }
            .setBackground { primaryContainer() }
            .setContrastCurve { getContrastCurve(6.0) }
            .build()
        return super
            .onPrimaryContainer()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    // Primary Fixed Colors [PF]

    public override fun primaryFixed(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("primary_fixed")
            .setPalette { it.primaryPalette }
            .setTone {
                val tempS = DynamicScheme.from(it, isDark = false, contrastLevel = 0.0)
                primaryContainer().getTone(tempS)
            }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setContrastCurve { if (it.contrastLevel > 0) getContrastCurve(1.5) else null }
            .build()
        return super
            .primaryFixed()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun primaryFixedDim(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("primary_fixed_dim")
            .setPalette { it.primaryPalette }
            .setTone { primaryFixed().getTone(it) }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setToneDeltaPair {
                ToneDeltaPair(
                    roleA = primaryFixedDim(),
                    roleB = primaryFixed(),
                    delta = 5.0,
                    polarity = TonePolarity.DARKER,
                    deltaConstraint = DeltaConstraint.EXACT,
                )
            }
            .setContrastCurve { if (it.contrastLevel > 0) getContrastCurve(1.5) else null }
            .build()
        return super
            .primaryFixedDim()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onPrimaryFixed(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_primary_fixed")
            .setPalette { it.primaryPalette }
            .setBackground { if (primaryFixed().getTone(it) > 57) primaryFixedDim() else primaryFixed() }
            .setContrastCurve { getContrastCurve(7.0) }
            .build()
        return super
            .onPrimaryFixed()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onPrimaryFixedVariant(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_primary_fixed_variant")
            .setPalette { it.primaryPalette }
            .setBackground { if (primaryFixed().getTone(it) > 57) primaryFixedDim() else primaryFixed() }
            .setContrastCurve { getContrastCurve(4.5) }
            .build()
        return super
            .onPrimaryFixedVariant()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    // Secondaries [Q]

    public override fun secondary(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("secondary")
            .setPalette { it.secondaryPalette }
            .setTone { if (it.isDark) tMinC(it.secondaryPalette) else tMaxC(it.secondaryPalette) }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setContrastCurve { getContrastCurve(4.5) }
            .setToneDeltaPair {
                if (it.platform == Platform.PHONE) {
                    ToneDeltaPair(
                        roleA = secondaryContainer(),
                        roleB = secondary(),
                        delta = 5.0,
                        polarity = TonePolarity.RELATIVE_LIGHTER,
                        deltaConstraint = DeltaConstraint.FARTHER,
                    )
                } else {
                    null
                }
            }
            .build()
        return super
            .secondary()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun secondaryDim(): DynamicColor {
        // Remapped to secondary in 2026 spec.
        val color2026 = secondary().toBuilder().setName("secondary_dim").build()
        return super
            .secondaryDim()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onSecondary(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_secondary")
            .setPalette { it.secondaryPalette }
            .setBackground { secondary() }
            .setContrastCurve { getContrastCurve(6.0) }
            .build()
        return super
            .onSecondary()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun secondaryContainer(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("secondary_container")
            .setPalette { it.secondaryPalette }
            .setTone {
                if (it.isDark) {
                    tMinC(it.secondaryPalette, 20.0, 49.0)
                } else {
                    tMaxC(it.secondaryPalette, 61.0, 90.0)
                }
            }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setContrastCurve { if (it.contrastLevel > 0) getContrastCurve(1.5) else null }
            .build()
        return super
            .secondaryContainer()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onSecondaryContainer(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_secondary_container")
            .setPalette { it.secondaryPalette }
            .setBackground { secondaryContainer() }
            .setContrastCurve { getContrastCurve(6.0) }
            .build()
        return super
            .onSecondaryContainer()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    // Secondary Fixed Colors [QF]

    public override fun secondaryFixed(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("secondary_fixed")
            .setPalette { it.secondaryPalette }
            .setTone {
                val tempS = DynamicScheme.from(it, isDark = false, contrastLevel = 0.0)
                secondaryContainer().getTone(tempS)
            }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setContrastCurve { if (it.contrastLevel > 0) getContrastCurve(1.5) else null }
            .build()
        return super
            .secondaryFixed()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun secondaryFixedDim(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("secondary_fixed_dim")
            .setPalette { it.secondaryPalette }
            .setTone { secondaryFixed().getTone(it) }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setToneDeltaPair {
                ToneDeltaPair(
                    roleA = secondaryFixedDim(),
                    roleB = secondaryFixed(),
                    delta = 5.0,
                    polarity = TonePolarity.DARKER,
                    deltaConstraint = DeltaConstraint.EXACT,
                )
            }
            .setContrastCurve { if (it.contrastLevel > 0) getContrastCurve(1.5) else null }
            .build()
        return super
            .secondaryFixedDim()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onSecondaryFixed(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_secondary_fixed")
            .setPalette { it.secondaryPalette }
            .setBackground {
                if (secondaryFixed().getTone(it) > 57) secondaryFixedDim() else secondaryFixed()
            }
            .setContrastCurve { getContrastCurve(7.0) }
            .build()
        return super
            .onSecondaryFixed()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onSecondaryFixedVariant(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_secondary_fixed_variant")
            .setPalette { it.secondaryPalette }
            .setBackground {
                if (secondaryFixed().getTone(it) > 57) secondaryFixedDim() else secondaryFixed()
            }
            .setContrastCurve { getContrastCurve(4.5) }
            .build()
        return super
            .onSecondaryFixedVariant()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    // Tertiaries [T]

    public override fun tertiary(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("tertiary")
            .setPalette { it.tertiaryPalette }
            .setTone { it.sourceColorHctList.getOrNull(1)?.tone ?: it.sourceColorHct.tone }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setContrastCurve { getContrastCurve(4.5) }
            .setToneDeltaPair {
                if (it.platform == Platform.PHONE) {
                    ToneDeltaPair(
                        roleA = tertiaryContainer(),
                        roleB = tertiary(),
                        delta = 5.0,
                        polarity = TonePolarity.RELATIVE_LIGHTER,
                        deltaConstraint = DeltaConstraint.FARTHER,
                    )
                } else {
                    null
                }
            }
            .build()
        return super
            .tertiary()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun tertiaryDim(): DynamicColor {
        // Remapped to tertiary in 2026 spec.
        val color2026 = tertiary().toBuilder().setName("tertiary_dim").build()
        return super
            .tertiaryDim()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onTertiary(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_tertiary")
            .setPalette { it.tertiaryPalette }
            .setBackground { tertiary() }
            .setContrastCurve { getContrastCurve(6.0) }
            .build()
        return super
            .onTertiary()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun tertiaryContainer(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("tertiary_container")
            .setPalette { it.tertiaryPalette }
            .setTone {
                val secondarySourceColorHct = it.sourceColorHctList.getOrNull(1) ?: it.sourceColorHct
                if (secondarySourceColorHct.tone > 55) {
                    secondarySourceColorHct.tone.coerceIn(61.0, 90.0)
                } else {
                    secondarySourceColorHct.tone.coerceIn(20.0, 49.0)
                }
            }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setContrastCurve { if (it.contrastLevel > 0) getContrastCurve(1.5) else null }
            .build()
        return super
            .tertiaryContainer()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onTertiaryContainer(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_tertiary_container")
            .setPalette { it.tertiaryPalette }
            .setBackground { tertiaryContainer() }
            .setContrastCurve { getContrastCurve(6.0) }
            .build()
        return super
            .onTertiaryContainer()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    // Tertiary Fixed Colors [TF]

    public override fun tertiaryFixed(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("tertiary_fixed")
            .setPalette { it.tertiaryPalette }
            .setTone {
                val tempS = DynamicScheme.from(it, isDark = false, contrastLevel = 0.0)
                tertiaryContainer().getTone(tempS)
            }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setContrastCurve { if (it.contrastLevel > 0) getContrastCurve(1.5) else null }
            .build()
        return super
            .tertiaryFixed()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun tertiaryFixedDim(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("tertiary_fixed_dim")
            .setPalette { it.tertiaryPalette }
            .setTone { tertiaryFixed().getTone(it) }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setToneDeltaPair {
                ToneDeltaPair(
                    roleA = tertiaryFixedDim(),
                    roleB = tertiaryFixed(),
                    delta = 5.0,
                    polarity = TonePolarity.DARKER,
                    deltaConstraint = DeltaConstraint.EXACT,
                )
            }
            .setContrastCurve { if (it.contrastLevel > 0) getContrastCurve(1.5) else null }
            .build()
        return super
            .tertiaryFixedDim()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onTertiaryFixed(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_tertiary_fixed")
            .setPalette { it.tertiaryPalette }
            .setBackground {
                if (tertiaryFixed().getTone(it) > 57) tertiaryFixedDim() else tertiaryFixed()
            }
            .setContrastCurve { getContrastCurve(7.0) }
            .build()
        return super
            .onTertiaryFixed()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onTertiaryFixedVariant(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_tertiary_fixed_variant")
            .setPalette { it.tertiaryPalette }
            .setBackground {
                if (tertiaryFixed().getTone(it) > 57) tertiaryFixedDim() else tertiaryFixed()
            }
            .setContrastCurve { getContrastCurve(4.5) }
            .build()
        return super
            .onTertiaryFixedVariant()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    // Errors [E]

    public override fun error(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("error")
            .setPalette { it.errorPalette }
            .setTone { tMaxC(it.errorPalette) }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setContrastCurve { getContrastCurve(4.5) }
            .setToneDeltaPair {
                if (it.platform == Platform.PHONE) {
                    ToneDeltaPair(
                        roleA = errorContainer(),
                        roleB = error(),
                        delta = 5.0,
                        polarity = TonePolarity.RELATIVE_LIGHTER,
                        deltaConstraint = DeltaConstraint.FARTHER,
                    )
                } else {
                    null
                }
            }
            .build()
        return super
            .error()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun errorDim(): DynamicColor {
        // Remapped to error in 2026 spec.
        val color2026 = error().toBuilder().setName("error_dim").build()
        return super
            .errorDim()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onError(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_error")
            .setPalette { it.errorPalette }
            .setBackground { error() }
            .setContrastCurve { getContrastCurve(6.0) }
            .build()
        return super
            .onError()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun errorContainer(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("error_container")
            .setPalette { it.errorPalette }
            .setTone { if (it.isDark) tMinC(it.errorPalette) else tMaxC(it.errorPalette) }
            .setIsBackground(true)
            .setBackground { highestSurface(it) }
            .setContrastCurve { if (it.contrastLevel > 0) getContrastCurve(1.5) else null }
            .build()
        return super
            .errorContainer()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }

    public override fun onErrorContainer(): DynamicColor {
        val color2026 = DynamicColor.Companion.Builder()
            .setName("on_error_container")
            .setPalette { it.errorPalette }
            .setBackground { errorContainer() }
            .setContrastCurve { getContrastCurve(6.0) }
            .build()
        return super
            .onErrorContainer()
            .toBuilder()
            .extendSpecVersion(ColorSpec.SpecVersion.SPEC_2026, color2026)
            .build()
    }
}
