package com.xingleme.wakeup

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes

/**
 * 一套完整的提神配色。所有主题都只使用浅色底，强调色用于按钮、开关和状态。
 */
data class ThemePalette(
    val id: String,
    @StyleRes val themeRes: Int,
    @StringRes val nameRes: Int,
    @ColorRes val primaryRes: Int,
    @ColorRes val deepRes: Int,
    @ColorRes val containerRes: Int,
    @ColorRes val pageRes: Int,
    @ColorRes val gradientStartRes: Int,
    @ColorRes val gradientEndRes: Int
)

object Palettes {
    val lime = ThemePalette(
        id = "lime",
        themeRes = R.style.Theme_Xingleme,
        nameRes = R.string.theme_lime,
        primaryRes = R.color.lime_primary,
        deepRes = R.color.lime_deep,
        containerRes = R.color.lime_container,
        pageRes = R.color.lime_page,
        gradientStartRes = R.color.lime_gradient_start,
        gradientEndRes = R.color.lime_gradient_end
    )

    val mint = ThemePalette(
        id = "mint",
        themeRes = R.style.Theme_Xingleme_Mint,
        nameRes = R.string.theme_mint,
        primaryRes = R.color.mint_primary,
        deepRes = R.color.mint_deep,
        containerRes = R.color.mint_container,
        pageRes = R.color.mint_page,
        gradientStartRes = R.color.mint_gradient_start,
        gradientEndRes = R.color.mint_gradient_end
    )

    val orange = ThemePalette(
        id = "orange",
        themeRes = R.style.Theme_Xingleme_Orange,
        nameRes = R.string.theme_orange,
        primaryRes = R.color.orange_primary,
        deepRes = R.color.orange_deep,
        containerRes = R.color.orange_container,
        pageRes = R.color.orange_page,
        gradientStartRes = R.color.orange_gradient_start,
        gradientEndRes = R.color.orange_gradient_end
    )

    val blue = ThemePalette(
        id = "blue",
        themeRes = R.style.Theme_Xingleme_Blue,
        nameRes = R.string.theme_blue,
        primaryRes = R.color.blue_primary,
        deepRes = R.color.blue_deep,
        containerRes = R.color.blue_container,
        pageRes = R.color.blue_page,
        gradientStartRes = R.color.blue_gradient_start,
        gradientEndRes = R.color.blue_gradient_end
    )

    val cherry = ThemePalette(
        id = "cherry",
        themeRes = R.style.Theme_Xingleme_Cherry,
        nameRes = R.string.theme_cherry,
        primaryRes = R.color.cherry_primary,
        deepRes = R.color.cherry_deep,
        containerRes = R.color.cherry_container,
        pageRes = R.color.cherry_page,
        gradientStartRes = R.color.cherry_gradient_start,
        gradientEndRes = R.color.cherry_gradient_end
    )

    val all = listOf(lime, mint, orange, blue, cherry)

    fun byId(id: String?): ThemePalette = all.firstOrNull { it.id == id } ?: lime

    fun themeRes(id: String?): Int = byId(id).themeRes
}
