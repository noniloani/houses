package com.meta.wearable.dat.externalsampleapps.displayaccess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.displayaccess.house.*
import kotlin.math.min

private val Mint = Color(0xFF91E7C2)
private val Muted = Color(0xFF97A9B7)
private val Panel = Color(0xFF142633)
private val Amber = Color(0xFFF4CB87)

@Composable
fun LiveScannerScreen(
    scan: HouseScanState,
    vision: VisionState,
    busy: Boolean,
    connected: Boolean,
    error: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRescan: () -> Unit,
    onSelect: (AddressSite) -> Unit,
    onConnect: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("FIELD NOTES / BRITISH COLUMBIA", color = Mint, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        Text("House lens", fontSize = 32.sp, fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF3F5EF), modifier = Modifier.padding(top = 6.dp))
      }
      Surface(color = Panel, shape = RoundedCornerShape(16.dp), onClick = onConnect) {
        Icon(Icons.Default.Settings, "Glasses settings", Modifier.padding(14.dp).size(20.dp), tint = Mint)
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatusPill(if (connected) "Glasses ready" else "Connect glasses", connected)
      StatusPill(scan.locationAccuracyM?.let { "GPS ±${it.toInt()} m" } ?: "GPS waiting",
          scan.locationAccuracyM?.let { it <= 50 && (scan.locationAgeMs ?: Long.MAX_VALUE) <= 15000 } == true)
    }
    CameraPanel(vision, scan)
    if (vision.error != null && scan.active) {
      Text(vision.error, color = Amber, style = MaterialTheme.typography.bodySmall)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Button(onClick = if (scan.active) onStop else if (connected) onStart else onConnect,
          enabled = !busy, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF0B1B23))) {
        if (busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = Mint)
        else {
          Icon(if (scan.active) Icons.Default.Stop else Icons.Default.CenterFocusStrong, null, Modifier.size(19.dp))
          Spacer(Modifier.width(8.dp))
          Text(if (scan.active) "Stop scanning" else if (connected) "Start scanning" else "Connect glasses", fontWeight = FontWeight.Bold)
        }
      }
      OutlinedButton(onClick = onRescan, enabled = scan.active && !busy,
          modifier = Modifier.height(54.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Default.Refresh, "Rescan")
      }
    }
    if (error != null) Text(error, color = Amber, style = MaterialTheme.typography.bodyMedium)
    PropertyCard(scan)
    if (scan.candidates.isNotEmpty()) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Nearby addresses", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("${scan.candidates.size} found", color = Muted, fontSize = 12.sp)
      }
      Text("Tap an address to hold it for 20 seconds.", color = Muted, fontSize = 12.sp)
      // Keep the full list selectable; dense streets are precisely where manual selection helps.
      scan.candidates.forEach { site ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)
            .clickable { onSelect(site) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Home, null, tint = if (site == scan.target.site) Mint else Muted, modifier = Modifier.size(20.dp))
          Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(site.fullAddress.substringBefore(","), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(site.locality, fontSize = 11.sp, color = Muted)
          }
          if (site == scan.target.site) Icon(Icons.Default.Check, "Selected", tint = Mint, modifier = Modifier.size(18.dp))
        }
      }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    TextButton(onClick = {
      context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          android.net.Uri.parse("package:${context.packageName}")))
    }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
      Icon(Icons.Default.Security, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
      Text("App permissions")
    }
    TextButton(onClick = onPreview, modifier = Modifier.align(Alignment.CenterHorizontally), enabled = !busy) {
      Icon(Icons.Default.PhoneAndroid, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
      Text("Preview on phone")
    }
    Text("Camera frames are processed on your phone. Values come from BC Assessment.",
        color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
  }
}

@Composable
private fun StatusPill(label: String, good: Boolean) {
  Row(Modifier.clip(RoundedCornerShape(50)).background(Panel).padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(5.dp).background(if (good) Mint else Amber, RoundedCornerShape(50)))
    Text(label, color = if (good) Mint else Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 7.dp))
  }
}

@Composable
private fun CameraPanel(vision: VisionState, scan: HouseScanState) {
  Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(24.dp)).background(Panel)) {
    val bitmap = vision.bitmap
    if (bitmap != null) Image(bitmap.asImageBitmap(), "Live glasses camera", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    Canvas(Modifier.fillMaxSize()) {
      if (bitmap == null) {
        val house = Path().apply {
          moveTo(size.width * .29f, size.height * .53f)
          lineTo(size.width * .5f, size.height * .31f)
          lineTo(size.width * .71f, size.height * .53f)
          moveTo(size.width * .33f, size.height * .5f)
          lineTo(size.width * .33f, size.height * .73f)
          lineTo(size.width * .67f, size.height * .73f)
          lineTo(size.width * .67f, size.height * .5f)
        }
        drawPath(house, Muted.copy(alpha = .22f), style = Stroke(2.dp.toPx()))
      }
      val scale = if (bitmap != null) min(size.width / bitmap.width, size.height / bitmap.height) else 1f
      val imageWidth = bitmap?.width?.times(scale) ?: size.width
      val imageHeight = bitmap?.height?.times(scale) ?: size.height
      val dx = (size.width - imageWidth) / 2
      val dy = (size.height - imageHeight) / 2
      vision.tags.forEach { tag ->
        drawRect(Mint, Offset(dx + tag.left * imageWidth, dy + tag.top * imageHeight),
            Size((tag.right - tag.left) * imageWidth, (tag.bottom - tag.top) * imageHeight), style = Stroke(2.dp.toPx()))
      }
      val left = dx + imageWidth * .2f
      val right = dx + imageWidth * .8f
      val top = dy + imageHeight * .15f
      val bottom = dy + imageHeight * .85f
      val arm = 15.dp.toPx()
      listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom)).forEach { p ->
        val sx = if (p.x == left) 1 else -1
        val sy = if (p.y == top) 1 else -1
        drawLine(Mint.copy(alpha = .8f), p, Offset(p.x + sx * arm, p.y), 2.dp.toPx())
        drawLine(Mint.copy(alpha = .8f), p, Offset(p.x, p.y + sy * arm), 2.dp.toPx())
      }
      drawCircle(Mint.copy(alpha = .65f), 2.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
    }
    Row(Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(50))
        .background(Color(0xE60B1B23)).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Default.Videocam, null, Modifier.size(13.dp), tint = Mint)
      Text(vision.label, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
    }
    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xD90B1B23)).padding(16.dp)) {
      Text(if (scan.active) scan.target.kind.label else "Ready when you are", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      Text(if (scan.active) "Keep the house number inside the guide" else "Look at a house. Get the details.",
          color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
  }
}

@Composable
private fun PropertyCard(scan: HouseScanState) {
  Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("PROPERTY SNAPSHOT", color = Muted, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
    Text(scan.target.site?.fullAddress?.substringBefore(",") ?: "Your next discovery", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    val p = scan.assessment
    if (p != null) {
      Text(p.totalValue?.let(HudRenderer::money) ?: "Value unavailable", color = Mint, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
      Text("Assessed value · ${p.assessmentDate ?: "BC Assessment"}", color = Muted, fontSize = 12.sp)
      HorizontalDivider(color = Muted.copy(alpha = .18f))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric("BUILT", p.yearBuilt ?: "—", Modifier.weight(1f))
        Metric("BEDS / BATHS", "${p.bedrooms ?: "—"} / ${p.baths ?: "—"}", Modifier.weight(1f))
        Metric("FLOOR", p.floorAreaSqFt?.let { "$it ft²" } ?: "—", Modifier.weight(1f))
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric("LAND VALUE", p.landValue?.let(HudRenderer::short) ?: "—", Modifier.weight(1f))
        Metric("BUILDING", p.buildingValue?.let(HudRenderer::short) ?: "—", Modifier.weight(1f))
      }
    } else if (scan.loading) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), color = Mint, strokeWidth = 2.dp)
        Text("Looking up the record…", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 10.dp))
      }
    }
    Text(scan.message, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
  }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Text(label, fontSize = 9.sp, letterSpacing = .5.sp, color = Muted)
    Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
  }
}
