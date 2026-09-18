package com.example.readmymi.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ThresholdSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SwitchRow(label = label, checked = checked, onCheckedChange = onCheckedChange)
}

@Composable
fun SettingsHint(text: String) {
    Text(text, fontSize = 12.sp)
}
