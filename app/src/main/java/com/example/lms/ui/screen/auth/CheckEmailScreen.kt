package com.example.lms.ui.screen.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.lms.ui.navigation.Routes
import com.example.lms.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun CheckEmailScreen(
    navController: NavController,
    viewModel: AuthViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val primaryColor = Color(0xFF4B5CC4)
    val lightPurple = Color(0xFFE2E9FC)

    var timeLeft by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(timeLeft) {
        if (timeLeft > 0) {
            delay(1000L)
            timeLeft--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Email Icon Box
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(color = lightPurple, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "Email Sent",
                tint = primaryColor,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Kiểm tra email",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Chúng tôi đã gửi hướng dẫn đặt lại mật khẩu đến địa chỉ email:",
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            color = Color.Gray,
            lineHeight = 22.sp
        )
        
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = uiState.email,
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )


        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Vui lòng kiểm tra hộp thư đến (và cả thư rác) để hoàn tất việc đặt lại mật khẩu.",
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = Color.Gray,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        /* ================= BACK TO LOGIN BUTTON ================= */
        Button(
            onClick = {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Quay lại Đăng nhập",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        /* ================= RESEND EMAIL WITH TIMER ================= */
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (timeLeft > 0) {
                Text(
                    text = "Gửi lại sau ${timeLeft}s",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "Không nhận được email? ",
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                Text(
                    text = "Gửi lại ngay",
                    color = primaryColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            timeLeft = 60
                            viewModel.sendPasswordResetEmail()
                        }
                )
            }
        }
    }
}
