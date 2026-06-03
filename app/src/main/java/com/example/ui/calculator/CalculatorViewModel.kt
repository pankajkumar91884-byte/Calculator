package com.example.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.VaultRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CalculatorEffect {
    object NavigateToVault : CalculatorEffect()
    data class ShowToast(val message: String) : CalculatorEffect()
}

class CalculatorViewModel(private val repository: VaultRepository) : ViewModel() {

    private val _displayExpression = MutableStateFlow("")
    val displayExpression: StateFlow<String> = _displayExpression.asStateFlow()

    private val _resultText = MutableStateFlow("0")
    val resultText: StateFlow<String> = _resultText.asStateFlow()

    private val _isPinSet = MutableStateFlow(false)
    val isPinSet: StateFlow<Boolean> = _isPinSet.asStateFlow()

    private val _effects = MutableSharedFlow<CalculatorEffect>()
    val effects: SharedFlow<CalculatorEffect> = _effects.asSharedFlow()

    // PIN Setup State tracking:
    // 0 = Normal, 1 = First PIN entered, waiting for confirmation
    private var setupState = 0
    private var draftPin = ""

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                _isPinSet.value = settings?.isPinSet == true
            }
        }
    }

    fun onKeyPress(key: String) {
        when (key) {
            "C" -> {
                _displayExpression.value = ""
                _resultText.value = "0"
                if (setupState == 1) {
                    setupState = 0
                    draftPin = ""
                    sendToast("PIN setup canceled")
                }
            }
            "DEL" -> {
                val current = _displayExpression.value
                if (current.isNotEmpty()) {
                    _displayExpression.value = current.dropLast(1)
                }
            }
            "=" -> {
                evaluateAndProcess()
            }
            "±" -> {
                val current = _displayExpression.value
                if (current.startsWith("-")) {
                    _displayExpression.value = current.substring(1)
                } else if (current.isNotEmpty()) {
                    _displayExpression.value = "-$current"
                }
            }
            else -> {
                _displayExpression.value += key
            }
        }
    }

    private fun evaluateAndProcess() {
        val expr = _displayExpression.value.trim()
        if (expr.isEmpty()) return

        // 1. PIN or Unlock verification
        if (!_isPinSet.value) {
            // First-time setup system
            if (setupState == 0) {
                if (isValidPinPattern(expr)) {
                    draftPin = expr
                    setupState = 1
                    _displayExpression.value = ""
                    _resultText.value = "Confirm PIN"
                    sendToast("Enter same PIN and press '=' again to confirm")
                } else {
                    // Try to evaluate as normal expression anyway
                    evaluateMath(expr)
                }
            } else if (setupState == 1) {
                if (expr == draftPin) {
                    viewModelScope.launch {
                        val success = repository.setupPin(draftPin)
                        if (success) {
                            sendToast("PIN Saved! Private Vault Opened!")
                            _displayExpression.value = ""
                            _resultText.value = "0"
                            setupState = 0
                            draftPin = ""
                            _effects.emit(CalculatorEffect.NavigateToVault)
                        } else {
                            sendToast("Error setting up vault PIN")
                        }
                    }
                } else {
                    sendToast("PIN limits do not match. Restarting PIN setup.")
                    setupState = 0
                    draftPin = ""
                    _displayExpression.value = ""
                    _resultText.value = "0"
                }
            }
            return
        }

        // 2. Normal operational unlock query (if PIN matches our registered setup)
        if (isValidPinPattern(expr)) {
            viewModelScope.launch {
                val success = repository.unlockVault(expr)
                if (success) {
                    _displayExpression.value = ""
                    _resultText.value = "0"
                    _effects.emit(CalculatorEffect.NavigateToVault)
                } else {
                    // Standard Vault hiding behavior: If PIN doesn't match, we evaluate as regular mathematical expression!
                    // This protects the user from intruders because a wrong PIN just renders a normal calculation instead of a flag!
                    evaluateMath(expr)
                }
            }
        } else {
            evaluateMath(expr)
        }
    }

    private fun isValidPinPattern(str: String): Boolean {
        // Safe 4-8 numeric PIN pattern
        return str.length in 4..8 && str.all { it.isDigit() }
    }

    private fun evaluateMath(expr: String) {
        try {
            val evaluator = MathEvaluator()
            val result = evaluator.evaluate(expr)
            // Format result beautifully
            _resultText.value = if (result % 1.0 == 0.0) {
                result.toLong().toString()
            } else {
                result.toString()
            }
        } catch (e: Exception) {
            _resultText.value = "Error"
        }
    }

    private fun sendToast(msg: String) {
        viewModelScope.launch {
            _effects.emit(CalculatorEffect.ShowToast(msg))
        }
    }
}

// Compact robust expression evaluator
class MathEvaluator {
    fun evaluate(expression: String): Double {
        // Pre-process visual multipliers, divisions, and percentage representations
        var sanitized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace(" ", "")

        // Handle simple percentages: convert "X%" to "X/100"
        sanitized = regexPercent(sanitized)
        return parse(sanitized)
    }

    private fun regexPercent(expr: String): String {
        return expr.replace(Regex("(\\d+(?:\\.\\d+)?)%")) { matchResult ->
            val value = matchResult.groupValues[1]
            "($value/100)"
        }
    }

    private fun parse(str: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parseExpression(): Double {
                nextChar()
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm()
                    else if (eat('-'.code)) x -= parseTerm()
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    } else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return +parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    if (!eat(')'.code)) throw IllegalArgumentException("Missing closed parentheses")
                } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, pos).toDouble()
                } else {
                    throw IllegalArgumentException("Unexpected character: " + ch.toChar())
                }
                return x
            }
        }.parseExpression()
    }
}
