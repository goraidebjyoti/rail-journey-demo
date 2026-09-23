package com.example.railjourneydemo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

private const val FIXED_SERVICE_NO = "R28199"
private const val DEFAULT_IR_NO = "19AAAGMO289C1ZC"

private data class TicketData(
    val passengerName: String,
    val mobile: String,
    val origin: String,
    val distance: String,
    val destination: String,
    val bookingDateTime: String,
    val via: String,
    val adults: String,
    val children: String,
    val bookedOn: String,
    val validTill: String,
    val className: String,
    val trainType: String,
    val ticketType: String,
    val fare: String,
    val irNumber: String,
    val journeyTicket: String,
)

private val HeaderBlue = Color(0xFF1730D9)
private val PageBg = Color(0xFFE9EDF9)
private val TextBlue = Color(0xFF303C68)
private val TicketBlack = Color(0xFF17171C)
private val Yellow = Color(0xFFFFF52D)
private val RedOrange = Color(0xFFFF4A28)
private val Cyan = Color(0xFF11A7F1)
private val NoteBg = Color(0xFFFFF0F2)
private val TicketBody = Color(0xFFFFFBFB)
private val GreenBg = Color(0xFFE0F2E3)
private val GreenText = Color(0xFF2D8A46)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { RailJourneyApp() }
    }
}

@Composable
private fun RailJourneyApp() {
    var showTicket by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(300) }

    var passengerName by remember { mutableStateOf("Aman") }
    var mobile by remember { mutableStateOf("7431922555") }
    var origin by remember { mutableStateOf("HOWRAH") }
    var distance by remember { mutableStateOf("116 km") }
    var destination by remember { mutableStateOf("KHARAGPUR") }
    var bookingDateTime by remember { mutableStateOf("21 Sep 2026, 11:07") }
    var via by remember { mutableStateOf("SRC-PKU") }
    var adults by remember { mutableStateOf("1") }
    var children by remember { mutableStateOf("0") }
    var bookedOn by remember { mutableStateOf("21/09/2026 11:07") }
    var validTill by remember { mutableStateOf("21/09/2026 12:07") }
    var className by remember { mutableStateOf("SECOND") }
    var trainType by remember { mutableStateOf("ORDINARY") }
    var ticketType by remember { mutableStateOf("JOURNEY") }
    var fare by remember { mutableStateOf("30.00") }
    var irNumber by remember { mutableStateOf(DEFAULT_IR_NO) }
    var journeyTicket by remember { mutableStateOf("") }

    LaunchedEffect(showTicket) {
        if (showTicket) secondsLeft = 300
    }
    LaunchedEffect(showTicket, secondsLeft) {
        if (showTicket && secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    val data = TicketData(
        passengerName = passengerName,
        mobile = mobile,
        origin = origin,
        distance = distance,
        destination = destination,
        bookingDateTime = bookingDateTime,
        via = via,
        adults = adults,
        children = children,
        bookedOn = bookedOn,
        validTill = validTill,
        className = className,
        trainType = trainType,
        ticketType = ticketType,
        fare = fare,
        irNumber = sanitizeAlphaNumeric15(irNumber).padEnd(15, '0'),
        journeyTicket = journeyTicket,
    )

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = PageBg) {
            if (showTicket) {
                TicketScreen(data, secondsLeft, onBack = { showTicket = false })
            } else {
                InputScreen(
                    data = data,
                    setPassengerName = { passengerName = it },
                    setMobile = { mobile = it.filter(Char::isDigit).take(15) },
                    setOrigin = { origin = it },
                    setDistance = { distance = it },
                    setDestination = { destination = it },
                    setBookingDateTime = { bookingDateTime = it },
                    setVia = { via = it },
                    setAdults = { adults = it.filter(Char::isDigit) },
                    setChildren = { children = it.filter(Char::isDigit) },
                    setBookedOn = { bookedOn = it },
                    setValidTill = { validTill = it },
                    setClassName = { className = it },
                    setTrainType = { trainType = it },
                    setTicketType = { ticketType = it },
                    setFare = { fare = it },
                    setIrNumber = { irNumber = sanitizeAlphaNumeric15(it) },
                    onGenerateTicket = {
                        journeyTicket = generateJourneyTicket()
                        showTicket = true
                    }
                )
            }
        }
    }
}

private fun sanitizeAlphaNumeric15(value: String): String =
    value.uppercase().filter { it in '0'..'9' || it in 'A'..'Z' }.take(15)

private fun generateJourneyTicket(): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".filter { it != 'X' }
    val counts = mutableMapOf<Char, Int>()
    val output = StringBuilder("X")
    var previous: Char? = null
    while (output.length < 10) {
        val candidates = alphabet.filter { candidate ->
            candidate != previous && (counts[candidate] ?: 0) < 2
        }
        if (candidates.isEmpty()) break
        val next = candidates[Random.nextInt(candidates.length)]
        output.append(next)
        counts[next] = (counts[next] ?: 0) + 1
        previous = next
    }
    return output.toString().padEnd(10, '0').take(10)
}

@Composable
private fun InputScreen(
    data: TicketData,
    setPassengerName: (String) -> Unit,
    setMobile: (String) -> Unit,
    setOrigin: (String) -> Unit,
    setDistance: (String) -> Unit,
    setDestination: (String) -> Unit,
    setBookingDateTime: (String) -> Unit,
    setVia: (String) -> Unit,
    setAdults: (String) -> Unit,
    setChildren: (String) -> Unit,
    setBookedOn: (String) -> Unit,
    setValidTill: (String) -> Unit,
    setClassName: (String) -> Unit,
    setTrainType: (String) -> Unit,
    setTicketType: (String) -> Unit,
    setFare: (String) -> Unit,
    setIrNumber: (String) -> Unit,
    onGenerateTicket: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(HeaderBlue)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Booking Details",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Field("Passenger Name", data.passengerName, setPassengerName, Modifier.fillMaxWidth())
            Field("Mobile Number", data.mobile, setMobile, Modifier.fillMaxWidth(), KeyboardType.Phone)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Field("From Station", data.origin, setOrigin, Modifier.weight(1.5f))
                Field("Distance (km)", data.distance, setDistance, Modifier.weight(.7f))
                Field("To Station", data.destination, setDestination, Modifier.weight(1.5f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Field("Ticket Booking Date & Time", data.bookingDateTime, setBookingDateTime, Modifier.weight(1f))
                Field("Via", data.via, setVia, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Field("Adults", data.adults, setAdults, Modifier.weight(1f), KeyboardType.Number)
                Field("Children", data.children, setChildren, Modifier.weight(1f), KeyboardType.Number)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Field("Booked On", data.bookedOn, setBookedOn, Modifier.weight(1f))
                Field("Valid Till", data.validTill, setValidTill, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Field("Class", data.className, setClassName, Modifier.weight(1f))
                // Deliberately editable: the requested train-type selector was replaced with a plain field.
                Field("Train Type", data.trainType, setTrainType, Modifier.weight(1f))
                Field("Ticket Type", data.ticketType, setTicketType, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Field("Fare (₹)", data.fare, setFare, Modifier.weight(1f))
                Field("IR No. (15 characters)", data.irNumber, setIrNumber, Modifier.weight(1f))
            }
            Text(
                "Service No.  $FIXED_SERVICE_NO",
                color = TextBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onGenerateTicket,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue)
            ) {
                Text("GENERATE TICKET", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HeaderBlue,
            unfocusedBorderColor = Color(0xFFB7C4E2),
            focusedLabelColor = HeaderBlue,
            unfocusedLabelColor = TextBlue
        )
    )
}

@Composable
private fun TicketScreen(data: TicketData, secondsLeft: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val minutes = secondsLeft / 60
    val seconds = secondsLeft % 60
    val countdown = "%02d:%02d".format(minutes, seconds)
    val qrPayload = buildQrPayload(data)
    val qrBitmap = remember(qrPayload) { generateQrBitmap(qrPayload, 560) }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().background(HeaderBlue)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(52.dp)
                        .border(1.8.dp, Color.White, CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Booking Details", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Mobile: ${data.mobile}", color = Color.White, fontSize = 14.sp)
                }
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, qrPayload)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share ticket"))
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Share, "Share", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }

        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(
                "Thank You ${data.passengerName}, Happy Journey !",
                fontSize = 15.sp,
                color = TextBlue
            )
            Spacer(Modifier.height(16.dp))

            DynamicTicket(data, countdown)
            Spacer(Modifier.height(12.dp))

            TicketBody(data)

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                border = androidx.compose.foundation.BorderStroke(1.4.dp, HeaderBlue),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = HeaderBlue
                )
            ) {
                Text("Book Connecting Journey", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Ticket QR code",
                    modifier = Modifier.size(250.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF4F4F7))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text("Do you know?", fontSize = 17.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("IR recovers only 57% of cost of travel on an average.", fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "This ticket is booked on a personal user ID. It’s sale/purchase is an offence u/s 143 of the Railways Act, 1989",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Text("For enquiry and integrated railway helpline, please dial 139.", fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun DynamicTicket(data: TicketData, countdown: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(Cyan, Color(0xFF1393E9), Cyan))
            )
            .padding(vertical = 9.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(186.dp)
                .background(TicketBlack),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RailwaySideBrand("INDIAN RAILWAYS")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Dynamic preview will close in", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(2.dp))
                Text(countdown, color = RedOrange, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(1.dp))
                Text("Ticket Booking Date & Time", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(data.bookingDateTime, color = Yellow, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(5.dp))
                Text(FIXED_SERVICE_NO, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text("Ticket is Non-Transferable", color = Color.White, fontSize = 13.sp)
            }
            RailwaySideBrand("भारतीय रेल")
        }
    }
}

@Composable
private fun RailwaySideBrand(text: String) {
    Box(
        Modifier
            .width(48.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val effect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            drawLine(
                color = Color.White,
                start = Offset(6f, 0f),
                end = Offset(6f, size.height),
                strokeWidth = 1.2f,
                pathEffect = effect
            )
            drawLine(
                color = Color.White,
                start = Offset(size.width - 6f, 0f),
                end = Offset(size.width - 6f, size.height),
                strokeWidth = 1.2f,
                pathEffect = effect
            )
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.rotate(-90f, size.width / 2f, size.height / 2f)
                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    textSize = 12.sp.toPx()
                }
                val maxTextWidth = size.height * 0.86f
                val measured = paint.measureText(text)
                if (measured > maxTextWidth) {
                    paint.textSize *= (maxTextWidth / measured)
                }
                val x = size.width / 2f
                val y = size.height / 2f - (paint.ascent() + paint.descent()) / 2f
                native.drawText(text, x, y, paint)
                native.restore()
            }
        }
    }
}

@Composable
private fun TicketBody(data: TicketData) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TicketBody)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Journey Ticket", fontSize = 20.sp, color = TextBlue, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GreenBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("●  ACTIVE", color = GreenText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(data.journeyTicket, fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))

        TwoColumnField("Source", data.origin, "Destination", data.destination, boldValues = true)
        Spacer(Modifier.height(9.dp))
        TwoColumnField("Distance", data.distance, "Passenger", "${data.adults} Adult, ${data.children} Child", boldValues = true)
        Spacer(Modifier.height(9.dp))
        TwoColumnField("Ticket Type", data.ticketType, "Train Types", data.trainType, boldValues = true)
        Spacer(Modifier.height(9.dp))
        TwoColumnField("Class", data.className, "Fare", "₹${data.fare}", boldValues = true)

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E2E7), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Column {
                Text("↝ Via: ${data.via}", color = TextBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text("IR:${data.irNumber}", color = TextBlue, fontSize = 13.sp, letterSpacing = 0.6.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        TicketCutoutDivider()

        Spacer(Modifier.height(10.dp))
        Text(
            "*Valid for start of journey within 1 hour or until departure of the first train.",
            fontSize = 12.sp,
            color = TextBlue,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(Brush.horizontalGradient(listOf(Cyan, Color(0xFF1598ED), Cyan)))
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(NoteBg)
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Text(
                "Note: This ticket is non refundable. Ticket is stored locally on the device. Please do not change your handset or perform factory reset.",
                fontSize = 13.sp,
                color = Color(0xFF9E2330),
                lineHeight = 20.sp
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun TwoColumnField(leftTitle: String, leftValue: String, rightTitle: String, rightValue: String, boldValues: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(leftTitle, fontSize = 12.sp, color = Color(0xFF6E7285))
            Text(leftValue, fontSize = 14.sp, color = TextBlue, fontWeight = if (boldValues) FontWeight.Bold else FontWeight.Normal)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(rightTitle, fontSize = 12.sp, color = Color(0xFF6E7285), textAlign = TextAlign.End)
            Text(rightValue, fontSize = 14.sp, color = TextBlue, fontWeight = if (boldValues) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun TicketCutoutDivider() {
    Row(
        Modifier
            .fillMaxWidth()
            .height(26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Side cut-outs: page background punches into the lower ticket body.
            drawCircle(PageBg, 13.dp.toPx(), Offset(0f, size.height / 2f))
            drawCircle(PageBg, 13.dp.toPx(), Offset(size.width, size.height / 2f))
            val effect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
            drawLine(
                color = Color(0xFFBFC4D0),
                start = Offset(13.dp.toPx(), size.height / 2f),
                end = Offset(size.width - 13.dp.toPx(), size.height / 2f),
                strokeWidth = 1.2f,
                pathEffect = effect
            )
        }
    }
}

private fun buildQrPayload(data: TicketData): String = buildString {
    appendLine("RAIL JOURNEY TICKET")
    appendLine("Journey Ticket=${data.journeyTicket}")
    appendLine("Service No=$FIXED_SERVICE_NO")
    appendLine("Passenger=${data.passengerName}")
    appendLine("Mobile=${data.mobile}")
    appendLine("Source=${data.origin}")
    appendLine("Destination=${data.destination}")
    appendLine("Distance=${data.distance}")
    appendLine("Booking Date Time=${data.bookingDateTime}")
    appendLine("Via=${data.via}")
    appendLine("Adults=${data.adults}")
    appendLine("Children=${data.children}")
    appendLine("Booked On=${data.bookedOn}")
    appendLine("Valid Till=${data.validTill}")
    appendLine("Class=${data.className}")
    appendLine("Train Type=${data.trainType}")
    appendLine("Ticket Type=${data.ticketType}")
    appendLine("Fare=${data.fare}")
    appendLine("IR=${data.irNumber}")
    appendLine("Status=ACTIVE")
    appendLine("Non-Transferable=true")
}

private fun generateQrBitmap(payload: String, size: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
