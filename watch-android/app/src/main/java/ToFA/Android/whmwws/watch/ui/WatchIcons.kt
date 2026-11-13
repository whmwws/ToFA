package ToFA.Android.whmwws.watch.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 自绘键盘用的矢量图标。
 * 系统输入法自身的按键无法定制，这里只替换应用内键盘的「退格」与「明文/隐藏」。
 * 路径取自 Material Icons（Apache 2.0），统一 24 视窗，用 tint 适配深浅色。
 */
private fun vector(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.White),
    ).build()

private val BackspaceVector: ImageVector by lazy {
    vector(
        "Backspace",
        "M22,3H7C6.31,3 5.77,3.35 5.41,3.88L0,12l5.41,8.11c0.36,0.54 0.9,0.89 1.59,0.89h15c1.1,0 " +
            "2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM19,15.59L17.59,17 14,13.41 10.41,17 9,15.59 " +
            "12.59,12 9,8.41 10.41,7 14,10.59 17.59,7 19,8.41 15.41,12 19,15.59z",
    )
}

private val VisibilityVector: ImageVector by lazy {
    vector(
        "Visibility",
        "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 " +
            "-6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9" +
            "c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
    )
}

private val VisibilityOffVector: ImageVector by lazy {
    vector(
        "VisibilityOff",
        "M12,7c2.76,0 5,2.24 5,5 -0.11,0.92 -0.49,1.72 -1.09,2.38l2.96,2.96c1.61,-1.42 2.87,-3.23 " +
            "3.63,-5.33C20.64,7.92 16.9,4.5 12,4.5c-1.23,0 -2.44,0.22 -3.57,0.61l1.79,1.79c0.76,-0.6 " +
            "1.56,-0.98 2.48,-1.09zM2.71,2.71l1.42,1.42C2.3,5.55 0.97,7.42 0,12c1.73,4.39 6,7.5 11,7.5 " +
            "1.21,0 2.38,-0.2 3.49,-0.58l2.06,2.06 1.42,-1.42 -12.84,-12.85zM7.59,7.59l1.79,1.79" +
            "c-0.11,0.31 -0.18,0.64 -0.18,0.98 0,1.66 1.34,3 3,3 0.34,0 0.67,-0.07 0.98,-0.18l1.79,1.79" +
            "c-0.61,0.43 -1.34,0.68 -2.12,0.68 -2.05,0 -3.71,-1.66 -3.71,-3.71 0,-0.78 0.25,-1.51 0.68,-2.12z",
    )
}

@Composable
fun BackspaceIcon(modifier: Modifier = Modifier, tint: Color = WatchColors.TextDim) {
    Icon(
        imageVector = BackspaceVector,
        contentDescription = tr("退格", "Backspace"),
        tint = tint,
        modifier = modifier,
    )
}

/** 明文/隐藏切换图标：显示当前动作（眼睛=可切明文，带斜线=可隐藏）。 */
@Composable
fun PasswordVisibilityIcon(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = WatchColors.TextDim,
) {
    Icon(
        imageVector = if (visible) VisibilityOffVector else VisibilityVector,
        contentDescription = if (visible) tr("隐藏", "Hide") else tr("明文", "Show"),
        tint = tint,
        modifier = modifier,
    )
}
