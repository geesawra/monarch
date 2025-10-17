package industries.geesawra.monarch

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val HeartFilled: ImageVector
    get() {
        if (_HeartFilled != null) return _HeartFilled!!

        _HeartFilled = ImageVector.Builder(
            name = "HeartFilled",
            defaultWidth = 15.dp,
            defaultHeight = 15.dp,
            viewportWidth = 15f,
            viewportHeight = 15f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(1.35248f, 4.90532f)
                curveTo(1.35248f, 2.94498f, 2.936f, 1.35248f, 4.89346f, 1.35248f)
                curveTo(6.25769f, 1.35248f, 6.86058f, 1.92336f, 7.50002f, 2.93545f)
                curveTo(8.13946f, 1.92336f, 8.74235f, 1.35248f, 10.1066f, 1.35248f)
                curveTo(12.064f, 1.35248f, 13.6476f, 2.94498f, 13.6476f, 4.90532f)
                curveTo(13.6476f, 6.74041f, 12.6013f, 8.50508f, 11.4008f, 9.96927f)
                curveTo(10.2636f, 11.3562f, 8.92194f, 12.5508f, 8.00601f, 13.3664f)
                curveTo(7.94645f, 13.4194f, 7.88869f, 13.4709f, 7.83291f, 13.5206f)
                curveTo(7.64324f, 13.6899f, 7.3568f, 13.6899f, 7.16713f, 13.5206f)
                curveTo(7.11135f, 13.4709f, 7.05359f, 13.4194f, 6.99403f, 13.3664f)
                curveTo(6.0781f, 12.5508f, 4.73641f, 11.3562f, 3.59926f, 9.96927f)
                curveTo(2.39872f, 8.50508f, 1.35248f, 6.74041f, 1.35248f, 4.90532f)
                close()
            }
        }.build()

        return _HeartFilled!!
    }

private var _HeartFilled: ImageVector? = null

val Heart: ImageVector
    get() {
        if (_Heart != null) return _Heart!!

        _Heart = ImageVector.Builder(
            name = "Heart",
            defaultWidth = 15.dp,
            defaultHeight = 15.dp,
            viewportWidth = 15f,
            viewportHeight = 15f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(4.89346f, 2.35248f)
                curveTo(3.49195f, 2.35248f, 2.35248f, 3.49359f, 2.35248f, 4.90532f)
                curveTo(2.35248f, 6.38164f, 3.20954f, 7.9168f, 4.37255f, 9.33522f)
                curveTo(5.39396f, 10.581f, 6.59464f, 11.6702f, 7.50002f, 12.4778f)
                curveTo(8.4054f, 11.6702f, 9.60608f, 10.581f, 10.6275f, 9.33522f)
                curveTo(11.7905f, 7.9168f, 12.6476f, 6.38164f, 12.6476f, 4.90532f)
                curveTo(12.6476f, 3.49359f, 11.5081f, 2.35248f, 10.1066f, 2.35248f)
                curveTo(9.27059f, 2.35248f, 8.81894f, 2.64323f, 8.5397f, 2.95843f)
                curveTo(8.27877f, 3.25295f, 8.14623f, 3.58566f, 8.02501f, 3.88993f)
                curveTo(8.00391f, 3.9429f, 7.98315f, 3.99501f, 7.96211f, 4.04591f)
                curveTo(7.88482f, 4.23294f, 7.7024f, 4.35494f, 7.50002f, 4.35494f)
                curveTo(7.29765f, 4.35494f, 7.11523f, 4.23295f, 7.03793f, 4.04592f)
                curveTo(7.01689f, 3.99501f, 6.99612f, 3.94289f, 6.97502f, 3.8899f)
                curveTo(6.8538f, 3.58564f, 6.72126f, 3.25294f, 6.46034f, 2.95843f)
                curveTo(6.18109f, 2.64323f, 5.72945f, 2.35248f, 4.89346f, 2.35248f)
                close()
                moveTo(1.35248f, 4.90532f)
                curveTo(1.35248f, 2.94498f, 2.936f, 1.35248f, 4.89346f, 1.35248f)
                curveTo(6.0084f, 1.35248f, 6.73504f, 1.76049f, 7.20884f, 2.2953f)
                curveTo(7.32062f, 2.42147f, 7.41686f, 2.55382f, 7.50002f, 2.68545f)
                curveTo(7.58318f, 2.55382f, 7.67941f, 2.42147f, 7.79119f, 2.2953f)
                curveTo(8.265f, 1.76049f, 8.99164f, 1.35248f, 10.1066f, 1.35248f)
                curveTo(12.064f, 1.35248f, 13.6476f, 2.94498f, 13.6476f, 4.90532f)
                curveTo(13.6476f, 6.74041f, 12.6013f, 8.50508f, 11.4008f, 9.96927f)
                curveTo(10.2636f, 11.3562f, 8.92194f, 12.5508f, 8.00601f, 13.3664f)
                curveTo(7.94645f, 13.4194f, 7.88869f, 13.4709f, 7.83291f, 13.5206f)
                curveTo(7.64324f, 13.6899f, 7.3568f, 13.6899f, 7.16713f, 13.5206f)
                curveTo(7.11135f, 13.4709f, 7.05359f, 13.4194f, 6.99403f, 13.3664f)
                curveTo(6.0781f, 12.5508f, 4.73641f, 11.3562f, 3.59926f, 9.96927f)
                curveTo(2.39872f, 8.50508f, 1.35248f, 6.74041f, 1.35248f, 4.90532f)
                close()
            }
        }.build()

        return _Heart!!
    }

private var _Heart: ImageVector? = null

