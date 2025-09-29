package industries.geesawra.jerryno

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import industries.geesawra.jerryno.datalayer.TimelineViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeView(
    modalSheetState: SheetState,
    focusRequester: FocusRequester,
    timelineViewModel: TimelineViewModel,
    onDismissRequest: () -> Unit,
) {

}