package com.example.ui.calculator

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onNavigateToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val expr by viewModel.displayExpression.collectAsState()
    val result by viewModel.resultText.collectAsState()
    val isPinSet by viewModel.isPinSet.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CalculatorEffect.NavigateToVault -> onNavigateToVault()
                is CalculatorEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Vault Stealth Header area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular stealth lock icon matching HTML design: w-8 h-8 rounded-full bg-white/5 active:bg-white/10
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                        .clickable {
                            Toast.makeText(
                                context,
                                "Stealth Mode Active. Type PIN and press '=' to access vault.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Stealth Vault",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // SECURED indicator watermark
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.02f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (isPinSet) MaterialTheme.colorScheme.primary else Color(0xFFFBBF24), // Vivid amber/yellow
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = if (isPinSet) "SECURED" else "TAP PIN TO SETUP",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Display panel area (large font, light weight tracking-tighter)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                // Calculation input expression (text-white/40 text-2xl font-light)
                Text(
                    text = expr.ifEmpty { " " },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Right,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 32.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calculator_expression")
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                // Calculated output (Result) (text-6xl font-light tracking-tighter text-white)
                Text(
                    text = result,
                    color = if (result == "Confirm PIN" || result == "0") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1.5).sp,
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calculator_result")
                )
            }

            // Keypad Tray: bg-[#121212] rounded-t-[40px] p-6 pb-10 shadow-2xl
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface, // KeypadBg (#121212)
                        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
                    )
                    .navigationBarsPadding() // Professional safe area padding
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 28.dp)
            ) {
                // Grid of Calculator Keys
                val buttons = listOf(
                    listOf("C", "(", ")", "÷"),
                    listOf("7", "8", "9", "×"),
                    listOf("4", "5", "6", "-"),
                    listOf("1", "2", "3", "+"),
                    listOf("0", ".", "DEL", "=")
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    buttons.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { char ->
                                CalculatorButton(
                                    text = char,
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.onKeyPress(char) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Utility extension helper for clean color alphas
private fun Color.withOpacity(alpha: Float): Color = this.copy(alpha = alpha)

@Composable
fun CalculatorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOperator = text in listOf("÷", "×", "-", "+", "=")
    val isSpecial = text in listOf("C", "(", ")")

    val containerColor = when {
        text == "=" -> MaterialTheme.colorScheme.primary // bg-green-500
        isOperator -> MaterialTheme.colorScheme.surfaceVariant // bg-[#2a2a2a]
        isSpecial -> MaterialTheme.colorScheme.tertiary.withOpacity(0.9f) // bg-[#1e1e1e]
        else -> MaterialTheme.colorScheme.tertiary // bg-[#1e1e1e]
    }

    val contentColor = when {
        text == "=" -> MaterialTheme.colorScheme.onPrimary
        isOperator -> MaterialTheme.colorScheme.onSurfaceVariant // text-green-400 (AccentGreenLight)
        isSpecial -> MaterialTheme.colorScheme.onSurfaceVariant // text-green-400
        else -> MaterialTheme.colorScheme.onSurface // text-white for numbers
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1.1f) // Custom proportional oval/round shape matching HTML grid items aspect
            .clip(CircleShape) // rounded-full
            .background(containerColor)
            .clickable(onClick = onClick)
            .testTag("btn_$text")
    ) {
        if (text == "DEL") {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Delete",
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = text,
                color = contentColor,
                fontSize = 24.sp,
                fontWeight = if (isOperator || isSpecial) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
