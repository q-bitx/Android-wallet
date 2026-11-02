package org.qbitx.wallet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.qbitx.wallet.R
import org.qbitx.wallet.ui.theme.*

@Composable
fun QbxBrand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.qbx_symbol), null, Modifier.size(36.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Q-Bit", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = QBXOnSurface, letterSpacing = (-0.7).sp)
                Text("X", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = QBXMint, letterSpacing = (-0.7).sp)
                Text("WALLET", fontSize = 9.sp, letterSpacing = 1.8.sp, fontFamily = FontFamily.Monospace,
                    color = QBXOnSurfaceDim, modifier = Modifier.padding(start = 14.dp))
            }
            Text("ecosystem", fontSize = 8.sp, letterSpacing = 1.2.sp, fontFamily = FontFamily.Monospace,
                color = QBXOnSurfaceDim)
        }
    }
}
