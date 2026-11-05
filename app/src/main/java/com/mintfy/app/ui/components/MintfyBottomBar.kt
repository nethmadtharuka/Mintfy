package com.mintfy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mintfy.app.ui.theme.MintfyTheme
import com.mintfy.app.ui.theme.TextMuted

/** The four top-level screens in the Figma "Bottom nav", in their order on screen. */
enum class MainTab(val label: String) {
    DASHBOARD("Dashboard"),
    INCOME("Income"),
    HISTORY("History"),
    GOAL("Goal"),
}

/**
 * Figma "Bottom nav": surface fill with a 1dp top border; Dashboard · Income · green "+" · History · Goal.
 * A tab is an 8dp dot over an 11sp label, green and SemiBold when selected, muted grey otherwise.
 * The "+" in the middle opens Add expense, so recording spending is one tap from any main screen (NFR-01).
 */
@Composable
fun MintfyBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit, onAddExpense: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .selectableGroup(),
            ) {
                TabItem(MainTab.DASHBOARD, selected, onSelect)
                TabItem(MainTab.INCOME, selected, onSelect)
                AddExpenseButton(onAddExpense)
                TabItem(MainTab.HISTORY, selected, onSelect)
                TabItem(MainTab.GOAL, selected, onSelect)
            }
        }
    }
}

@Composable
private fun TabItem(tab: MainTab, selected: MainTab, onSelect: (MainTab) -> Unit) {
    val isSelected = tab == selected
    val color = if (isSelected) MaterialTheme.colorScheme.primary else TextMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(70.dp)
            .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
            .padding(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = color,
        )
    }
}

/** Figma "FAB Add expense": 70 x 54 green pill with a black 28 Bold "+". */
@Composable
private fun AddExpenseButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(width = 70.dp, height = 54.dp).semantics { contentDescription = "Add expense" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "+",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Preview
@Composable
fun MintfyBottomBarPreview() {
    MintfyTheme {
        MintfyBottomBar(selected = MainTab.DASHBOARD, onSelect = {}, onAddExpense = {})
    }
}
