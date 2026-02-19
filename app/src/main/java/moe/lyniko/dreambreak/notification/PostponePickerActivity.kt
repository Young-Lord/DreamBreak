package moe.lyniko.dreambreak.notification

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.lyniko.dreambreak.R
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.ui.theme.DreamBreakTheme

class PostponePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BreakRuntime.start()
        setContent {
            DreamBreakTheme {
                PostponePickerScreen(
                    onSelect = { seconds ->
                        BreakRuntime.postponeBreakForSeconds(seconds)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun PostponePickerScreen(
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val options = listOf(
        60 to R.string.postpone_option_1m,
        5 * 60 to R.string.postpone_option_5m,
        15 * 60 to R.string.postpone_option_15m,
        30 * 60 to R.string.postpone_option_30m,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.postpone_choose_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )

        options.forEach { (seconds, labelRes) ->
            Button(
                onClick = { onSelect(seconds) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(stringResource(labelRes))
            }
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}
