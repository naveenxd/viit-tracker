package com.vignan.tracker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    rollNumber: String,
    onRollNumberChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    rememberMe: Boolean = true,
    onRememberMeChange: (Boolean) -> Unit = {},
    isLoading: Boolean,
    errorMessage: String? = null,
    onSubmit: () -> Unit
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackerColors.PureBlack)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        // Wordmark
        Text(
            text = "VIIT",
            color = TrackerColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Attendance Tracker",
            color = TrackerColors.TextMuted,
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif
        )

        Spacer(modifier = Modifier.height(52.dp))

        // Inline Error
        if (!errorMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TrackerColors.DangerRoseSubtle)
                    .border(1.dp, TrackerColors.DangerRose.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(TrackerColors.DangerRose)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = errorMessage,
                    color = TrackerColors.TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // Roll Number
        Text(
            text = "Registration number",
            color = TrackerColors.TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = rollNumber,
            onValueChange = { onRollNumberChange(it.uppercase().trim()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (rollNumber.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onRollNumberChange("") }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", color = TrackerColors.TextMuted, fontSize = 12.sp)
                    }
                }
            },
            placeholder = {
                Text(
                    text = "e.g. 22B91A0501",
                    color = TrackerColors.TextSubtle,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TrackerColors.TextPrimary
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TrackerColors.SurfaceInput,
                unfocusedContainerColor = TrackerColors.SurfaceInput,
                focusedBorderColor = TrackerColors.BorderFocused,
                unfocusedBorderColor = TrackerColors.HairlineBorder,
                focusedTextColor = TrackerColors.TextPrimary,
                unfocusedTextColor = TrackerColors.TextPrimary,
                cursorColor = TrackerColors.TextPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { passwordFocusRequester.requestFocus() }
            )
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Password
        Text(
            text = "Password",
            color = TrackerColors.TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester),
            singleLine = true,
            placeholder = {
                Text(
                    text = "Portal password",
                    color = TrackerColors.TextSubtle,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif
                )
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                color = TrackerColors.TextPrimary
            ),
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onTogglePasswordVisibility() }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEyeIcon(
                        isVisible = isPasswordVisible,
                        color = if (isPasswordVisible) TrackerColors.TextSecondary else TrackerColors.TextMuted
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TrackerColors.SurfaceInput,
                unfocusedContainerColor = TrackerColors.SurfaceInput,
                focusedBorderColor = TrackerColors.BorderFocused,
                unfocusedBorderColor = TrackerColors.HairlineBorder,
                focusedTextColor = TrackerColors.TextPrimary,
                unfocusedTextColor = TrackerColors.TextPrimary,
                cursorColor = TrackerColors.TextPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onSubmit()
            })
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Keep Me Logged In
        val interactionSource = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onRememberMeChange(!rememberMe) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CleanCheckbox(
                checked = rememberMe,
                onCheckedChange = onRememberMeChange
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Keep me signed in",
                color = TrackerColors.TextMuted,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Sign In Button
        Button(
            onClick = {
                focusManager.clearFocus()
                onSubmit()
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TrackerColors.PrimaryWhite,
                contentColor = TrackerColors.PureBlack,
                disabledContainerColor = TrackerColors.SurfaceElevated,
                disabledContentColor = TrackerColors.TextMuted
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    color = TrackerColors.PureBlack,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Signing in…",
                    color = TrackerColors.PureBlack,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
            } else {
                Text(
                    text = "Sign in",
                    color = TrackerColors.PureBlack,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
        FooterBadge()
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- Clean Checkbox

@Composable
private fun CleanCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (checked) TrackerColors.PrimaryWhite else TrackerColors.SurfaceInput,
        animationSpec = tween(durationMillis = 150),
        label = "checkboxBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) TrackerColors.PrimaryWhite else TrackerColors.HairlineBorderLight,
        animationSpec = tween(durationMillis = 150),
        label = "checkboxBorder"
    )

    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Canvas(modifier = Modifier.size(10.dp)) {
                val w = size.width
                val h = size.height
                val strokeW = 1.8.dp.toPx()
                val checkPath = Path().apply {
                    moveTo(w * 0.15f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.82f)
                    lineTo(w * 0.88f, h * 0.22f)
                }
                drawPath(
                    path = checkPath,
                    color = TrackerColors.PureBlack,
                    style = Stroke(
                        width = strokeW,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}
