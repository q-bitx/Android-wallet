package org.qbitx.wallet.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.qbitx.wallet.R
import org.qbitx.wallet.ui.theme.*

@Composable
fun SeedBackupScreen(seed: String, onConfirmed: () -> Unit) {
    var safetyQuiz by remember(seed) { mutableStateOf(false) }
    if (safetyQuiz) {
        RecoverySafetyScreen(onConfirmed)
        return
    }
    var checking by remember(seed) { mutableStateOf(false) }
    var answers by remember(seed) { mutableStateOf("") }
    val words = seed.split(' ')
    val fontScale=LocalDensity.current.fontScale
    val width=LocalConfiguration.current.screenWidthDp
    val columns=when { fontScale>=1.6f || width<320 -> 1; fontScale>1.15f || width<380 -> 2; else -> 3 }
    Surface(modifier = Modifier.fillMaxSize(), color = QBXBackground) {
        Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            QbxBrand()
            HorizontalDivider(color=QBXDivider)
            Text("SEED / 24 WORDS", fontFamily=FontFamily.Monospace, fontSize=10.sp, letterSpacing=2.sp, color=QBXMint)
            Text(stringResource(R.string.seed_title), style=MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.seed_instruction), color=QBXOnSurfaceDim, style=MaterialTheme.typography.bodyMedium)
            if (!checking) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    words.chunked(columns).forEachIndexed { row, group ->
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                            group.forEachIndexed { column, word ->
                                Surface(color=QBXSurface,modifier=Modifier.weight(1f)
                                    .border(1.dp,QBXDivider,RoundedCornerShape(8.dp)),shape=RoundedCornerShape(8.dp)) {
                                    Row(Modifier.padding(horizontal=10.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                                        Text("${row*columns+column+1}",fontFamily=FontFamily.Monospace,fontSize=10.sp,color=QBXOnSurfaceDim)
                                        Text(word,fontFamily=FontFamily.Monospace,fontSize=13.sp,fontWeight=FontWeight.Medium,color=QBXOnSurface)
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick={checking=true},modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.seed_written))
                }
            } else {
                Text(stringResource(R.string.seed_check_instruction),color=QBXOnSurface)
                OutlinedTextField(value=answers,onValueChange={answers=it},modifier=Modifier.fillMaxWidth(),
                    label={Text(stringResource(R.string.seed_confirm))},keyboardOptions=KeyboardOptions(autoCorrect=false,keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),
                    shape=RoundedCornerShape(8.dp))
                Button(onClick={safetyQuiz=true},modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(8.dp),
                    enabled=answers.trim().split(Regex("\\s+"))==listOf(words[3],words[11],words[19])) {
                    Text(stringResource(R.string.seed_open))
                }
                TextButton(onClick={checking=false;answers=""}) { Text(stringResource(R.string.seed_show_again)) }
            }
            Text(stringResource(R.string.seed_format),fontFamily=FontFamily.Monospace,fontSize=10.sp,color=QBXOnSurfaceDim)
        }
    }
}
