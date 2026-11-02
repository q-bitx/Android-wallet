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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
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
    var answers by remember(seed) { mutableStateOf(listOf("", "", "")) }
    val focusManager=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val positions = listOf(3,11,19)
    val focus = remember { List(3) { androidx.compose.ui.focus.FocusRequester() } }
    val expected = positions.map { seed.split(' ')[it] }
    androidx.compose.runtime.LaunchedEffect(answers) {
        val completed=answers.zip(expected).takeWhile { it.first==it.second }.size
        if (completed in 1..2) focus[completed].requestFocus()
        else if(completed==3) { focusManager.clearFocus(); keyboard?.hide() }
    }
    val words = seed.split(' ')
    val fontScale=LocalDensity.current.fontScale
    val width=LocalConfiguration.current.screenWidthDp
    val columns=when { fontScale>=1.6f || width<320 -> 1; fontScale>1.15f || width<380 -> 2; else -> 3 }
    Surface(modifier = Modifier.fillMaxSize(), color = QBXBackground) {
        Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            QbxBrand()
            HorizontalDivider(color=QBXDivider)
            Text(stringResource(R.string.seed_heading), fontFamily=FontFamily.Monospace, fontSize=10.sp, letterSpacing=2.sp, color=QBXMint)
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
                positions.forEachIndexed { index, position ->
                    val value=answers[index]
                    val correct=value==expected[index]
                    val incorrect=value.isNotEmpty() && !correct
                    OutlinedTextField(value=value,onValueChange={ typed ->
                        val normalized=typed.trim().lowercase(java.util.Locale.ROOT)
                        answers=answers.mapIndexed { i, old -> when { i==index -> normalized; i>index -> ""; else -> old } }
                    },modifier=Modifier.fillMaxWidth().focusRequester(focus[index]).testTag("seed_word_${position+1}"),
                        label={Text(stringResource(R.string.seed_word_number,position+1))},
                        enabled=(0 until index).all { answers[it]==expected[it] },isError=incorrect,singleLine=true,
                        trailingIcon={ if(correct) Icon(Icons.Default.CheckCircle,stringResource(R.string.answer_correct),tint=QBXMint)
                            else if(incorrect) Icon(Icons.Default.Cancel,stringResource(R.string.answer_incorrect),tint=QBXRed) },
                        keyboardOptions=KeyboardOptions(autoCorrect=false,capitalization=androidx.compose.ui.text.input.KeyboardCapitalization.None,
                            keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),shape=RoundedCornerShape(8.dp))
                }
                Button(onClick={safetyQuiz=true},modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(8.dp),
                    enabled=answers==expected) {
                    Text(stringResource(R.string.seed_open))
                }
                TextButton(onClick={checking=false;answers=listOf("", "", "")}) { Text(stringResource(R.string.seed_show_again)) }
            }
            Text(stringResource(R.string.seed_format),fontFamily=FontFamily.Monospace,fontSize=10.sp,color=QBXOnSurfaceDim)
        }
    }
}
