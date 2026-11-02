package org.qbitx.wallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
            Text("Безопасность кошелька · ${quiz.question + 1}/2", style=MaterialTheme.typography.headlineSmall)
            Text(if (quiz.question==0)
                "Если кто-то представился командой Q-BitX и просит назвать seed-фразу, что нужно сделать?"
                else "Если я забыл seed-фразу или потерял файл восстановления wallet.dat, кто поможет вернуть доступ?")
            if (quiz.correct == null) {
                val options=if (quiz.question==0) listOf("Да, я должен предоставить seed-фразу", "Нет, это мошенники")
                    else listOf("Команда Q-BitX поможет восстановить доступ к кошельку и средствам", "Команда не сможет мне помочь")
                options.forEachIndexed { index, text ->
                    OutlinedButton(onClick={quiz=quiz.answer(index==1)},modifier=Modifier.fillMaxWidth()) { Text(text) }
                }
            } else {
                Text(if (quiz.correct==true) "Верно" else "Неверно",color=if(quiz.correct==true) QBXMint else QBXRed)
                Text(if (quiz.question==0)
                    "Команда Q-BitX никогда не попросит предоставить seed-фразу или файл wallet.dat. Это мошенники."
                    else "Команда Q-BitX не имеет доступа к вашим кошелькам и резервным файлам восстановления wallet.dat. Если все ваши копии seed-фразы, ключей и файлов восстановления утрачены, доступ к средствам будет утрачен навсегда.")
                Button(onClick={
                    if (quiz.correct!=true) quiz=RecoverySafetyQuiz()
                    else if (quiz.question==0) quiz=quiz.next()
                    else onConfirmed()
                },modifier=Modifier.fillMaxWidth()) { Text(if(quiz.correct==true) "Далее" else "Повторить") }
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
