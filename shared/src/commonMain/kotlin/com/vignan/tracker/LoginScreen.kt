package com.vignan.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
    isLoading: Boolean,
    onSubmit: () -> Unit
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TrackerColors.SurfaceDark)
                .border(1.dp, TrackerColors.HairlineBorder, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                // Centered LOGIN Header
                Text(
                    text = "LOGIN",
                    color = TrackerColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )

                Text(
                    text = "Registration Number",
                    color = TrackerColors.TextSubtle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = rollNumber,
                    onValueChange = onRollNumberChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g. 26L35A4699", color = TrackerColors.TextSubtle, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TrackerColors.SurfaceInput,
                        unfocusedContainerColor = TrackerColors.SurfaceInput,
                        focusedBorderColor = TrackerColors.HairlineBorderLight,
                        unfocusedBorderColor = TrackerColors.HairlineBorder,
                        focusedTextColor = TrackerColors.TextPrimary,
                        unfocusedTextColor = TrackerColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "PASSWORD",
                    color = TrackerColors.TextSubtle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Password", color = TrackerColors.TextSubtle, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
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
                                color = if (isPasswordVisible) TrackerColors.TextPrimary else TrackerColors.TextMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TrackerColors.SurfaceInput,
                        unfocusedContainerColor = TrackerColors.SurfaceInput,
                        focusedBorderColor = TrackerColors.HairlineBorderLight,
                        unfocusedBorderColor = TrackerColors.HairlineBorder,
                        focusedTextColor = TrackerColors.TextPrimary,
                        unfocusedTextColor = TrackerColors.TextPrimary
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

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSubmit()
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
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
                            modifier = Modifier.size(14.dp),
                            color = TrackerColors.PureBlack,
                            strokeWidth = 1.8.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LOGGING IN...",
                            color = TrackerColors.PureBlack,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    } else {
                        Text(
                            text = "LOGIN",
                            color = TrackerColors.PureBlack,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
