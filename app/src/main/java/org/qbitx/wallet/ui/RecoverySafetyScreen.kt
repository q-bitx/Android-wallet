package org.qbitx.wallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.qbitx.wallet.R
import androidx.compose.ui.unit.dp
import org.qbitx.wallet.ui.theme.*

/** Completion is persisted only by the caller after both correct answers. */
@Composable
fun RecoverySafetyScreen(onConfirmed: () -> Unit) {
    var quiz by remember { mutableStateOf(RecoverySafetyQuiz()) }
    Surface(Modifier.fillMaxSize(), color=QBXBackground) {
        Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            QbxBrand()
            Text(stringResource(R.string.safety_title,quiz.question+1),style=MaterialTheme.typography.headlineSmall)
            Text(stringResource(if(quiz.question==0) R.string.safety_question_1 else R.string.safety_question_2))
            if (quiz.correct == null) {
                val options=if(quiz.question==0) listOf(R.string.safety_wrong_1,R.string.safety_right_1)
                    else listOf(R.string.safety_wrong_2,R.string.safety_right_2)
                options.forEachIndexed { index, text ->
                    OutlinedButton(onClick={quiz=quiz.answer(index==1)},modifier=Modifier.fillMaxWidth()) { Text(stringResource(text)) }
                }
            } else {
                Text(stringResource(if(quiz.correct==true) R.string.answer_correct else R.string.answer_incorrect),color=if(quiz.correct==true) QBXMint else QBXRed)
                Text(stringResource(if(quiz.question==0) R.string.safety_explanation_1 else R.string.safety_explanation_2))
                Button(onClick={
                    if (quiz.correct!=true) quiz=RecoverySafetyQuiz()
                    else if (quiz.question==0) quiz=quiz.next()
                    else onConfirmed()
                },modifier=Modifier.fillMaxWidth()) { Text(stringResource(if(quiz.correct==true) R.string.continue_button else R.string.repeat_button)) }
            }
        }
    }
}

data class RecoverySafetyQuiz(val question: Int=0, val correct: Boolean?=null) {
    fun answer(isCorrect: Boolean): RecoverySafetyQuiz {
        check(correct==null)
        return copy(correct=isCorrect)
    }
    fun next(): RecoverySafetyQuiz {
        check(question==0 && correct==true)
        return RecoverySafetyQuiz(question=1)
    }
}
