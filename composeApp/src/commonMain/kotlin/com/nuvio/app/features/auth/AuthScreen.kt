package com.nuvio.app.features.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.ui.NuvioDesktopContentMaxWidth
import com.nuvio.app.core.ui.NuvioCompactDialogMaxWidth
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.nuvioOverlayGradientBrush
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_wordmark
import nuvio.composeapp.generated.resources.compose_auth_already_have_account
import nuvio.composeapp.generated.resources.compose_auth_continue_without_account
import nuvio.composeapp.generated.resources.compose_auth_create_account
import nuvio.composeapp.generated.resources.compose_auth_dont_have_account
import nuvio.composeapp.generated.resources.compose_auth_email
import nuvio.composeapp.generated.resources.compose_auth_or_separator
import nuvio.composeapp.generated.resources.compose_auth_password
import nuvio.composeapp.generated.resources.compose_auth_sign_in
import nuvio.composeapp.generated.resources.compose_auth_sign_in_subtitle
import nuvio.composeapp.generated.resources.compose_auth_sign_up
import nuvio.composeapp.generated.resources.compose_auth_sign_up_subtitle
import nuvio.composeapp.generated.resources.compose_auth_store_locally
import nuvio.composeapp.generated.resources.compose_auth_tagline
import nuvio.composeapp.generated.resources.compose_auth_welcome_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal data class AuthScreenLayout(
    val useSplitLayout: Boolean,
    val contentMaxWidth: Dp,
    val formMaxWidth: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
)

internal fun authScreenLayoutFor(availableWidth: Dp): AuthScreenLayout =
    if (availableWidth >= 900.dp) {
        AuthScreenLayout(
            useSplitLayout = true,
            contentMaxWidth = NuvioDesktopContentMaxWidth,
            formMaxWidth = NuvioCompactDialogMaxWidth,
            horizontalPadding = 48.dp,
            verticalPadding = 32.dp,
        )
    } else {
        AuthScreenLayout(
            useSplitLayout = false,
            contentMaxWidth = 460.dp,
            formMaxWidth = 440.dp,
            horizontalPadding = 24.dp,
            verticalPadding = 28.dp,
        )
    }

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
) {
    val authError by AuthRepository.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val emailFocusRequester = remember { FocusRequester() }
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }

    fun submitCredentials() {
        if (email.isBlank() || password.length < 6 || isLoading) return
        isLoading = true
        scope.launch {
            if (isSignUp) {
                AuthRepository.signUpWithEmail(email, password)
            } else {
                AuthRepository.signInWithEmail(email, password)
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { emailFocusRequester.requestFocus() }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val layout = remember(maxWidth) { authScreenLayoutFor(maxWidth) }
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = nuvioOverlayGradientBrush()),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = layout.horizontalPadding,
                    end = layout.horizontalPadding,
                    top = statusBarTop + layout.verticalPadding,
                    bottom = layout.verticalPadding,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (layout.useSplitLayout) {
                Row(
                    modifier = Modifier
                        .widthIn(max = layout.contentMaxWidth)
                        .fillMaxWidth()
                        .heightIn(min = 500.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                ) {
                    AuthBrandBlock(
                        modifier = Modifier
                            .weight(0.48f)
                            .fillMaxHeight(),
                        compact = false,
                    )
                    Box(
                        modifier = Modifier.weight(0.52f),
                        contentAlignment = Alignment.Center,
                    ) {
                        AuthFormCard(
                            modifier = Modifier
                                .widthIn(max = layout.formMaxWidth)
                                .fillMaxWidth(),
                            isSignUp = isSignUp,
                            email = email,
                            onEmailChange = {
                                email = it
                                AuthRepository.clearError()
                            },
                            password = password,
                            onPasswordChange = {
                                password = it
                                AuthRepository.clearError()
                            },
                            passwordVisible = passwordVisible,
                            onPasswordVisibilityChange = { passwordVisible = !passwordVisible },
                            isLoading = isLoading,
                            authError = authError,
                            emailFocusRequester = emailFocusRequester,
                            onSubmit = ::submitCredentials,
                            onToggleSignUp = {
                                isSignUp = !isSignUp
                                AuthRepository.clearError()
                            },
                            onContinueWithoutAccount = AuthRepository::signInAnonymously,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = layout.contentMaxWidth)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AuthBrandBlock(compact = true)
                    Spacer(modifier = Modifier.height(32.dp))
                    AuthFormCard(
                        modifier = Modifier
                            .widthIn(max = layout.formMaxWidth)
                            .fillMaxWidth(),
                        isSignUp = isSignUp,
                        email = email,
                        onEmailChange = {
                            email = it
                            AuthRepository.clearError()
                        },
                        password = password,
                        onPasswordChange = {
                            password = it
                            AuthRepository.clearError()
                        },
                        passwordVisible = passwordVisible,
                        onPasswordVisibilityChange = { passwordVisible = !passwordVisible },
                        isLoading = isLoading,
                        authError = authError,
                        emailFocusRequester = emailFocusRequester,
                        onSubmit = ::submitCredentials,
                        onToggleSignUp = {
                            isSignUp = !isSignUp
                            AuthRepository.clearError()
                        },
                        onContinueWithoutAccount = AuthRepository::signInAnonymously,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthBrandBlock(
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.app_logo_wordmark),
            contentDescription = null,
            modifier = Modifier
                .widthIn(max = if (compact) 260.dp else 380.dp)
                .fillMaxWidth(if (compact) 0.68f else 0.82f)
                .height(if (compact) 42.dp else 62.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.height(if (compact) 10.dp else 18.dp))
        Text(
            text = stringResource(Res.string.compose_auth_tagline),
            modifier = Modifier.widthIn(max = 360.dp),
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AuthFormCard(
    modifier: Modifier,
    isSignUp: Boolean,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityChange: () -> Unit,
    isLoading: Boolean,
    authError: String?,
    emailFocusRequester: FocusRequester,
    onSubmit: () -> Unit,
    onToggleSignUp: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
) {
    Surface(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                onSubmit()
                true
            } else {
                false
            }
        },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 26.dp),
        ) {
            AnimatedContent(
                targetState = isSignUp,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "heading",
            ) { signUp ->
                Text(
                    text = if (signUp) {
                        stringResource(Res.string.compose_auth_create_account)
                    } else {
                        stringResource(Res.string.compose_auth_welcome_back)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            AnimatedContent(
                targetState = isSignUp,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "subtitle",
            ) { signUp ->
                Text(
                    text = if (signUp) {
                        stringResource(Res.string.compose_auth_sign_up_subtitle)
                    } else {
                        stringResource(Res.string.compose_auth_sign_in_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocusRequester),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(Res.string.compose_auth_email),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                colors = authTextFieldColors(),
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(Res.string.compose_auth_password),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                trailingIcon = {
                    IconButton(onClick = onPasswordVisibilityChange) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Rounded.VisibilityOff
                            } else {
                                Icons.Rounded.Visibility
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                colors = authTextFieldColors(),
            )

            authError?.let { errorText ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            NuvioPrimaryButton(
                text = if (isLoading) {
                    ""
                } else if (isSignUp) {
                    stringResource(Res.string.compose_auth_create_account)
                } else {
                    stringResource(Res.string.compose_auth_sign_in)
                },
                enabled = email.isNotBlank() && password.length >= 6 && !isLoading,
                onClick = onSubmit,
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(top = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                AnimatedContent(
                    targetState = isSignUp,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "togglePrompt",
                ) { signUp ->
                    Text(
                        text = if (signUp) {
                            stringResource(Res.string.compose_auth_already_have_account)
                        } else {
                            stringResource(Res.string.compose_auth_dont_have_account)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedContent(
                    targetState = isSignUp,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "toggleAction",
                ) { signUp ->
                    Text(
                        text = if (signUp) {
                            stringResource(Res.string.compose_auth_sign_in)
                        } else {
                            stringResource(Res.string.compose_auth_sign_up)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onToggleSignUp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            AuthDivider()
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onContinueWithoutAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(
                    text = stringResource(Res.string.compose_auth_continue_without_account),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.compose_auth_store_locally),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun authTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f),
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary,
    )

@Composable
private fun AuthDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        )
        Text(
            text = stringResource(Res.string.compose_auth_or_separator),
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        )
    }
}
