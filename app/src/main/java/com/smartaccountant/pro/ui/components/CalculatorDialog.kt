package com.smartaccountant.pro.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun CalculatorDialog(
    onDismiss: () -> Unit,
    onUseResult: (Double) -> Unit
) {
    var display by remember { mutableStateOf("0") }
    var firstOperand by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }

    fun onDigit(d: String) {
        display = if (display == "0" && d != ".") d else display + d
    }

    fun onOperator(op: String) {
        firstOperand = display.toDoubleOrNull() ?: 0.0
        pendingOp = op
        display = "0"
    }

    fun onEquals() {
        val a = firstOperand ?: return
        val b = display.toDoubleOrNull() ?: 0.0
        val res = when (pendingOp) {
            "+" -> a + b
            "-" -> a - b
            "×" -> a * b
            "÷" -> if (b != 0.0) a / b else 0.0
            else -> b
        }
        display = if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.2f", res)
        firstOperand = null
        pendingOp = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الحاسبة السريعة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = display,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge
                )
                val rows = listOf(
                    listOf("7", "8", "9", "÷"),
                    listOf("4", "5", "6", "×"),
                    listOf("1", "2", "3", "-"),
                    listOf("C", "0", ".", "+")
                )
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            FilledTonalButton(
                                onClick = {
                                    when (key) {
                                        "C" -> { display = "0"; firstOperand = null; pendingOp = null }
                                        "+", "-", "×", "÷" -> onOperator(key)
                                        else -> onDigit(key)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(key, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Button(onClick = { onEquals() }, modifier = Modifier.fillMaxWidth()) {
                    Text("=")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onEquals()
                onUseResult(display.toDoubleOrNull() ?: 0.0)
                onDismiss()
            }) { Text("اعتماد الناتج") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
