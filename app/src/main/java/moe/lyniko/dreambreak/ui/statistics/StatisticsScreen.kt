package moe.lyniko.dreambreak.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import moe.lyniko.dreambreak.R
import moe.lyniko.dreambreak.core.formatPostponeOption
import moe.lyniko.dreambreak.data.history.HistoryRepository
import moe.lyniko.dreambreak.data.history.PostponeDecisionEntity

@Composable
fun StatisticsPage() {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        HistoryRepository.getInstance(context.applicationContext)
    }
    val decisions by repository.observeRecentPostponeDecisions().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.statistics_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.statistics_recent_postpones),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (decisions.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = stringResource(R.string.statistics_empty),
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = decisions,
                    key = PostponeDecisionEntity::id,
                ) { decision ->
                    PostponeDecisionCard(decision)
                }
            }
        }
    }
}

@Composable
private fun PostponeDecisionCard(decision: PostponeDecisionEntity) {
    val dateTimeFormatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = dateTimeFormatter.format(Date(decision.confirmedAtEpochMillis)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.statistics_delay_duration,
                    formatPostponeOption(decision.delayDurationSeconds),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.statistics_reason),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = decision.reason,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
