/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = darkColorScheme(
    primary = Color(0xFF91E7C2), onPrimary = Color(0xFF0B1B23),
    primaryContainer = Color(0xFF244335), onPrimaryContainer = Color(0xFFB9F3D8),
    secondary = Color(0xFF97A9B7), onSecondary = Color(0xFF0B1B23),
    surface = Color(0xFF142633), onSurface = Color(0xFFF3F5EF),
    onSurfaceVariant = Color(0xFF97A9B7), outline = Color(0xFF334653),
    background = Color(0xFF0B1B23), onBackground = Color(0xFFF3F5EF),
)

@Composable
fun DisplayAccessTheme(content: @Composable () -> Unit) {
  MaterialTheme(
      colorScheme = LightColorScheme,
      content = content,
  )
}
