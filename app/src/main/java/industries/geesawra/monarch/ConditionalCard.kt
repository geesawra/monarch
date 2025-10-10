package industries.geesawra.monarch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConditionalCard(text: String, wrapWithCard: Boolean = true) {
    if (wrapWithCard) {
        OutlinedCard(
            modifier = Modifier
                .height(80.dp)
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .height(60.dp)
                .padding(start = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}