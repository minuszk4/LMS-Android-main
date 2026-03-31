package com.example.lms.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true
) {
    Column(modifier) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF64748B)
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            placeholder = {
                Text(
                    text = placeholder,
                    fontSize = 15.sp,
                    color = Color(0xFFA0AEC0)
                )
            },
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = Color(0xFF2D3748),
                fontWeight = FontWeight.Normal,
                lineHeight = 22.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFFAFAFC),
                unfocusedContainerColor = Color(0xFFFAFAFC),
                disabledContainerColor = Color(0xFFE2E8F0),
                focusedBorderColor = Color(0xFF4B5CC4),
                unfocusedBorderColor = Color(0xFFE2E8F0),
                disabledBorderColor = Color(0xFFE2E8F0),
                cursorColor = Color(0xFF4B5CC4),
                focusedTextColor = Color(0xFF2D3748),
                unfocusedTextColor = Color(0xFF2D3748),
                disabledTextColor = Color(0xFFA0AEC0)
            )
        )
    }
}

@Composable
fun FormFieldWithValidation(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true
) {
    Column(modifier) {
        FormField(
            label = label,
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            minLines = minLines,
            keyboardType = keyboardType,
            enabled = enabled
        )
        AnimatedVisibility(
            visible = error != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            error?.let {
                Text(
                    text = it,
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }
    }
}
