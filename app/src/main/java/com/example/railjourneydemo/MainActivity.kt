package com.example.railjourneydemo

import android.content.Context
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val DEFAULT_SERVICE_NO = "R28199"
private const val DEFAULT_IR_NO = "19AAAGMO289C1ZC"

private val TRAIN_TYPE_OPTIONS = listOf("ORDINARY", "MAIL/EXPRESS", "SUPERFAST", "AC EMU TRAIN")
private val TICKET_TYPE_OPTIONS = listOf("JOURNEY", "RETURN")
private val CLASS_OPTIONS = listOf("SECOND", "FIRST")

private val DATE_TIME_FORMAT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply { isLenient = false }
private val TICKET_DATE_TIME_FORMAT = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US)

/** Current device date & time as "dd/MM/yyyy HH:mm" (24-hour clock). */
private fun currentDateTimeString(): String = DATE_TIME_FORMAT.format(Calendar.getInstance().time)

/** Adds [hours] to a "dd/MM/yyyy HH:mm" string; returns null if [dateTime] isn't fully valid yet. */
private fun addHours(dateTime: String, hours: Int): String? = try {
    val parsed = DATE_TIME_FORMAT.parse(dateTime)
    if (parsed == null) {
        null
    } else {
        val cal = Calendar.getInstance()
        cal.time = parsed
        cal.add(Calendar.HOUR_OF_DAY, hours)
        DATE_TIME_FORMAT.format(cal.time)
    }
} catch (e: Exception) {
    null
}

/** Converts "dd/MM/yyyy HH:mm" to the ticket's display format, e.g. "21 Sep 2026, 11:07". */
private fun toTicketDisplayDateTime(dateTime: String): String = try {
    val parsed = DATE_TIME_FORMAT.parse(dateTime)
    if (parsed == null) dateTime else TICKET_DATE_TIME_FORMAT.format(parsed)
} catch (e: Exception) {
    dateTime
}

/** Formats a fare string to always show exactly two decimal places. */
private fun formatFare(value: String): String {
    val number = value.trim().toDoubleOrNull() ?: return "0.00"
    return String.format(Locale.US, "%.2f", number)
}

/** Keeps only digits and a single decimal point while typing a fare amount. */
private fun sanitizeFareInput(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    if (firstDot == -1) return filtered
    return filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
}

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
    val serviceNo: String,
)

/** A saved passenger's re-usable booking details (timing fields are intentionally excluded). */
private data class PassengerProfile(
    val passengerName: String,
    val mobile: String,
    val origin: String,
    val distance: String,
    val destination: String,
    val via: String,
    val adults: String,
    val children: String,
    val className: String,
    val trainType: String,
    val ticketType: String,
    val fare: String,
    val irNumber: String,
    val serviceNo: String,
)

private const val PROFILES_PREFS = "rail_journey_profiles"
private const val PROFILE_NAMES_KEY = "saved_passenger_names"

private fun loadSavedPassengerNames(context: Context): List<String> {
    val prefs = context.getSharedPreferences(PROFILES_PREFS, Context.MODE_PRIVATE)
    return (prefs.getStringSet(PROFILE_NAMES_KEY, emptySet()) ?: emptySet()).sorted()
}

private fun saveProfile(context: Context, profile: PassengerProfile) {
    val trimmedName = profile.passengerName.trim()
    if (trimmedName.isEmpty()) return
    val prefs = context.getSharedPreferences(PROFILES_PREFS, Context.MODE_PRIVATE)
    val json = JSONObject().apply {
        put("passengerName", trimmedName)
        put("mobile", profile.mobile)
        put("origin", profile.origin)
        put("distance", profile.distance)
        put("destination", profile.destination)
        put("via", profile.via)
        put("adults", profile.adults)
        put("children", profile.children)
        put("className", profile.className)
        put("trainType", profile.trainType)
        put("ticketType", profile.ticketType)
        put("fare", profile.fare)
        put("irNumber", profile.irNumber)
        put("serviceNo", profile.serviceNo)
    }
    val names = (loadSavedPassengerNames(context) + trimmedName).toSet()
    prefs.edit()
        .putString("profile_${trimmedName.lowercase()}", json.toString())
        .putStringSet(PROFILE_NAMES_KEY, names)
        .apply()
}

private fun loadProfile(context: Context, name: String): PassengerProfile? {
    val prefs = context.getSharedPreferences(PROFILES_PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getString("profile_${name.trim().lowercase()}", null) ?: return null
    return try {
        val json = JSONObject(raw)
        PassengerProfile(
            passengerName = json.optString("passengerName"),
            mobile = json.optString("mobile"),
            origin = json.optString("origin"),
            distance = json.optString("distance"),
            destination = json.optString("destination"),
            via = json.optString("via"),
            adults = json.optString("adults"),
            children = json.optString("children"),
            className = json.optString("className"),
            trainType = json.optString("trainType"),
            ticketType = json.optString("ticketType"),
            fare = json.optString("fare"),
            irNumber = json.optString("irNumber"),
            serviceNo = json.optString("serviceNo"),
        )
    } catch (e: Exception) {
        null
    }
}

private val HeaderBlue = Color(0xFF1730D9)
private val PageBg = Color(0xFFE9EDF9)
private val TextBlue = Color(0xFF303C68)
private val TicketBlack = Color(0xFF17171C)
private val Yellow = Color(0xFFFFF52D)
private val RedOrange = Color(0xFFFF4A28)
private val DateOrange = Color(0xFFFFA21A)
private val Cyan = Color(0xFF67CDE6)
private val RailwayGrey = Color(0xFFD4D4D8)
private val BookingGrey = Color(0xFFB9BAC1)
private val ViaBoxBg = Color(0xFFF8F7F8)
private val NoteBg = Color(0xFFFFF0F2)
private val TicketBody = Color(0xFFFFFBFB)
private val GreenBg = Color(0xFFE0F2E3)
private val GreenText = Color(0xFF34C264)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { RailJourneyApp() }
    }
}

@Composable
private fun RailJourneyApp() {
    val context = LocalContext.current
    var showTicket by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(300) }

    var passengerName by remember { mutableStateOf("Aman") }
    var mobile by remember { mutableStateOf("7431922555") }
    var origin by remember { mutableStateOf("HOWRAH") }
    var distance by remember { mutableStateOf("116 km") }
    var destination by remember { mutableStateOf("KHARAGPUR") }
    var via by remember { mutableStateOf("SRC-PKU") }
    var adults by remember { mutableStateOf("1") }
    var children by remember { mutableStateOf("0") }
    // Booked On defaults to the device's current date & time (24-hour clock);
    // Valid Till defaults to three hours after it. Both stay editable.
    var bookedOn by remember { mutableStateOf(currentDateTimeString()) }
    var validTill by remember { mutableStateOf(addHours(bookedOn, 3) ?: bookedOn) }
    var className by remember { mutableStateOf(CLASS_OPTIONS.first()) }
    var trainType by remember { mutableStateOf(TRAIN_TYPE_OPTIONS.first()) }
    var ticketType by remember { mutableStateOf(TICKET_TYPE_OPTIONS.first()) }
    var fare by remember { mutableStateOf("30.00") }
    var irNumber by remember { mutableStateOf(DEFAULT_IR_NO) }
    var journeyTicket by remember { mutableStateOf("") }
    var serviceNo by remember { mutableStateOf(DEFAULT_SERVICE_NO) }

    var savedNames by remember { mutableStateOf(loadSavedPassengerNames(context)) }

    LaunchedEffect(showTicket) {
        if (showTicket) secondsLeft = 300
    }
    LaunchedEffect(showTicket, secondsLeft) {
        if (showTicket && secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    // "Ticket Booking Date & Time" is always derived from "Booked On" — no
    // separate entry for it.
    val data = TicketData(
        passengerName = passengerName,
        mobile = mobile,
        origin = origin,
        distance = distance,
        destination = destination,
        bookingDateTime = toTicketDisplayDateTime(bookedOn),
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
        serviceNo = serviceNo,
    )

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = PageBg) {
            if (showTicket) {
                TicketScreen(data, secondsLeft, onBack = { showTicket = false })
            } else {
                InputScreen(
                    data = data,
                    savedNames = savedNames,
                    setPassengerName = { passengerName = it },
                    setMobile = { mobile = it.filter(Char::isDigit).take(15) },
                    setOrigin = { origin = it.uppercase() },
                    setDistance = { distance = it },
                    setDestination = { destination = it.uppercase() },
                    setVia = { via = it.uppercase() },
                    setAdults = { adults = it.filter(Char::isDigit) },
                    setChildren = { children = it.filter(Char::isDigit) },
                    setBookedOn = {
                        bookedOn = it
                        addHours(it, 3)?.let { computed -> validTill = computed }
                    },
                    setValidTill = { validTill = it },
                    resetBookedOnToNow = {
                        val now = currentDateTimeString()
                        bookedOn = now
                        validTill = addHours(now, 3) ?: now
                    },
                    setClassName = { className = it },
                    setTrainType = { trainType = it },
                    setTicketType = { ticketType = it },
                    setFare = { fare = sanitizeFareInput(it) },
                    onFareFocusLost = { fare = formatFare(fare) },
                    setIrNumber = { irNumber = sanitizeAlphaNumeric15(it) },
                    setServiceNo = { serviceNo = it.uppercase().take(10) },
                    onLoadProfile = { name ->
                        loadProfile(context, name)?.let { p ->
                            passengerName = p.passengerName
                            mobile = p.mobile
                            origin = p.origin
                            distance = p.distance
                            destination = p.destination
                            via = p.via
                            adults = p.adults
                            children = p.children
                            className = p.className
                            trainType = p.trainType
                            ticketType = p.ticketType
                            fare = p.fare
                            irNumber = p.irNumber
                            serviceNo = p.serviceNo
                        }
                    },
                    onSaveProfile = {
                        saveProfile(
                            context,
                            PassengerProfile(
                                passengerName = passengerName,
                                mobile = mobile,
                                origin = origin,
                                distance = distance,
                                destination = destination,
                                via = via,
                                adults = adults,
                                children = children,
                                className = className,
                                trainType = trainType,
                                ticketType = ticketType,
                                fare = formatFare(fare).also { fare = it },
                                irNumber = irNumber,
                                serviceNo = serviceNo,
                            )
                        )
                        savedNames = loadSavedPassengerNames(context)
                    },
                    onGenerateTicket = {
                        fare = formatFare(fare)
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
    savedNames: List<String>,
    setPassengerName: (String) -> Unit,
    setMobile: (String) -> Unit,
    setOrigin: (String) -> Unit,
    setDistance: (String) -> Unit,
    setDestination: (String) -> Unit,
    setVia: (String) -> Unit,
    setAdults: (String) -> Unit,
    setChildren: (String) -> Unit,
    setBookedOn: (String) -> Unit,
    setValidTill: (String) -> Unit,
    resetBookedOnToNow: () -> Unit,
    setClassName: (String) -> Unit,
    setTrainType: (String) -> Unit,
    setTicketType: (String) -> Unit,
    setFare: (String) -> Unit,
    onFareFocusLost: () -> Unit,
    setIrNumber: (String) -> Unit,
    setServiceNo: (String) -> Unit,
    onLoadProfile: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onGenerateTicket: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg)
            .verticalScroll(rememberScrollState())
    ) {
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
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (savedNames.isNotEmpty()) {
                SectionCard(title = "Saved Passengers", icon = Icons.Default.History) {
                    var expanded by remember { mutableStateOf(false) }
                    Text(
                        "Quickly fill in every field from a passenger you've saved before.",
                        fontSize = 12.sp,
                        color = Color(0xFF7A7B84)
                    )
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = "Select a saved passenger",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Load saved passenger") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            colors = fieldColors()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            savedNames.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onLoadProfile(name)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            SectionCard(title = "Passenger Details") {
                Field("Passenger Name", data.passengerName, setPassengerName, Modifier.fillMaxWidth())
                Field("Mobile Number", data.mobile, setMobile, Modifier.fillMaxWidth(), KeyboardType.Phone)
            }

            SectionCard(title = "Journey Route") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Field("From Station", data.origin, setOrigin, Modifier.weight(1.5f))
                    Field("Distance (km)", data.distance, setDistance, Modifier.weight(.7f))
                    Field("To Station", data.destination, setDestination, Modifier.weight(1.5f))
                }
                Text(
                    "From/To station and Via are always saved in UPPERCASE.",
                    fontSize = 11.sp,
                    color = Color(0xFF7A7B84)
                )
                Field("Via", data.via, setVia, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Field("Adults", data.adults, setAdults, Modifier.weight(1f), KeyboardType.Number)
                    Field("Children", data.children, setChildren, Modifier.weight(1f), KeyboardType.Number)
                }
            }

            SectionCard(title = "Booking Time") {
                Text(
                    "Defaults to your device's current date & time. Valid Till auto-fills 3 hours later — both stay editable.",
                    fontSize = 11.sp,
                    color = Color(0xFF7A7B84)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Field("Booked On (dd/MM/yyyy HH:mm)", data.bookedOn, setBookedOn, Modifier.weight(1f))
                    OutlinedButton(
                        onClick = resetBookedOnToNow,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text("Now", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Field("Valid Till (dd/MM/yyyy HH:mm)", data.validTill, setValidTill, Modifier.fillMaxWidth())
            }

            SectionCard(title = "Ticket Details") {
                DropdownField("Class", data.className, CLASS_OPTIONS, setClassName, Modifier.fillMaxWidth())
                DropdownField("Train Type", data.trainType, TRAIN_TYPE_OPTIONS, setTrainType, Modifier.fillMaxWidth())
                DropdownField("Ticket Type", data.ticketType, TICKET_TYPE_OPTIONS, setTicketType, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Field(
                        "Fare (₹)",
                        data.fare,
                        setFare,
                        Modifier.weight(1f).onFocusChanged { if (!it.isFocused) onFareFocusLost() },
                        KeyboardType.Decimal
                    )
                    Field("IR No. (15 characters)", data.irNumber, setIrNumber, Modifier.weight(1f))
                }
                Field("Service No.", data.serviceNo, setServiceNo, Modifier.fillMaxWidth())
            }

            OutlinedButton(
                onClick = onSaveProfile,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.4.dp, HeaderBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Passenger Details", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

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
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HeaderBlue,
    unfocusedBorderColor = Color(0xFFB7C4E2),
    focusedLabelColor = HeaderBlue,
    unfocusedLabelColor = TextBlue
)

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE7E7EC), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = HeaderBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextBlue)
        }
        content()
    }
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            colors = fieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
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
        colors = fieldColors()
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

        // Flush, square-edged strip — no card margin and no gap from the header.
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                "Thank You ${data.passengerName}, Happy Journey !",
                fontSize = 14.sp,
                color = TextBlue
            )
        }

        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            DynamicTicket(data, countdown)
            TicketBody(data)

            // The refund note is a separate element, outside the journey-ticket card.
            Spacer(Modifier.height(14.dp))
            TicketNote()

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
        }

        // QR panel has square edges and reaches both screen edges.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            TicketCutoutDivider()
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Ticket QR code",
                    modifier = Modifier.size(250.dp)
                )
            }
        }

        // Soft shadow-like band so the white QR panel's bottom margin reads
        // clearly above the "Do you know?" panel.
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFFD7D9DE), Color.White)))
        )

        // Informational panel has square edges and reaches both screen edges.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 22.dp, vertical = 18.dp)
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

@Composable
private fun DynamicTicket(data: TicketData, countdown: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(PageBg)
    ) {
        // Full-width blue tint above the black preview.
        Box(Modifier.fillMaxWidth().height(10.dp).background(Cyan))

        Row(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(TicketBlack),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exactly one dashed divider on each side of the black panel.
            RailwaySideBrand("INDIAN RAILWAYS", drawDividerOnRight = true)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Dynamic preview will close in",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(1.dp))
                Text(countdown, color = RedOrange, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(0.dp))
                Text("Ticket Booking Date & Time", color = BookingGrey, fontSize = 13.sp)
                Spacer(Modifier.height(1.dp))
                Text(
                    data.bookingDateTime,
                    color = DateOrange,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Text(data.serviceNo, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text("Ticket is Non-Transferable", color = Color.White, fontSize = 13.sp)
            }
            RailwaySideBrand("भारतीय रेल", drawDividerOnRight = false)
        }

        // Blue tint below the black preview is shorter than the panel and
        // starts from the left edge; the rest of the row shows the page
        // background instead of continuing the cyan all the way across.
        Box(
            Modifier
                .fillMaxWidth(0.45f)
                .height(10.dp)
                .background(Cyan)
        )
    }
}

@Composable
private fun RailwaySideBrand(text: String, drawDividerOnRight: Boolean) {
    Box(
        Modifier
            .width(48.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // The reference has only two dashed lines total: one inner divider
            // on each side of the central ticket content.
            val effect = PathEffect.dashPathEffect(floatArrayOf(18f, 8f), 0f)
            val x = if (drawDividerOnRight) size.width - 1.5f else 1.5f
            drawLine(
                color = RailwayGrey,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.1f,
                pathEffect = effect
            )

            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.rotate(-90f, size.width / 2f, size.height / 2f)
                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(212, 212, 216)
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = AndroidPaint.Align.CENTER
                    textSize = 19.sp.toPx()
                }
                val maxTextWidth = size.height * 0.90f
                val measured = paint.measureText(text)
                if (measured > maxTextWidth) {
                    paint.textSize *= (maxTextWidth / measured)
                }
                val xText = size.width / 2f
                val yText = size.height / 2f - (paint.ascent() + paint.descent()) / 2f
                native.drawText(text, xText, yText, paint)
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
            .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(TicketBody)
    ) {
        // All the regular field content stays inset from the card edges...
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Journey Ticket",
                    fontSize = 11.sp,
                    color = Color(0xFF7A7B84),
                    lineHeight = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GreenBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "●  ACTIVE",
                        color = GreenText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = GreenText.copy(alpha = 0.55f),
                                blurRadius = 6f
                            )
                        )
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(data.journeyTicket, fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(ViaBoxBg)
                    .border(1.dp, Color(0xFFE3E1E3), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ViaRouteIcon()
                    Spacer(Modifier.width(7.dp))
                    Text("Via: ${data.via}", color = TextBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("IR:${data.irNumber}", color = TextBlue, fontSize = 13.sp, letterSpacing = 0.6.sp)
        }

        // Cutout notches sit right at the card's true edges, so this divider
        // spans the full card width rather than the inset content width.
        TicketCutoutDivider()

        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "*Valid for start of journey within 1 hour or until departure of the first train.",
                fontSize = 12.sp,
                color = TextBlue,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))
        }

        // Blue border below "Valid for..." spans edge to edge of the white card.
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(Brush.horizontalGradient(listOf(Cyan, Color(0xFF1598ED), Cyan)))
        )
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun TicketNote() {
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
            color = Color(0xFFD34F59),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun ViaRouteIcon() {
    Canvas(Modifier.size(18.dp)) {
        val stroke = 1.7.dp.toPx()
        val c = TextBlue
        // Small branching route/track mark inspired by the reference icon.
        drawLine(c, Offset(2f, size.height * 0.66f), Offset(size.width * 0.48f, size.height * 0.66f), strokeWidth = stroke)
        drawLine(c, Offset(size.width * 0.48f, size.height * 0.66f), Offset(size.width * 0.80f, size.height * 0.38f), strokeWidth = stroke)
        drawLine(c, Offset(size.width * 0.48f, size.height * 0.66f), Offset(size.width * 0.80f, size.height * 0.84f), strokeWidth = stroke)
        drawCircle(c, radius = 1.8.dp.toPx(), center = Offset(2f, size.height * 0.66f))
        drawCircle(c, radius = 1.8.dp.toPx(), center = Offset(size.width * 0.80f, size.height * 0.38f))
        drawCircle(c, radius = 1.8.dp.toPx(), center = Offset(size.width * 0.80f, size.height * 0.84f))
    }
}

@Composable
private fun TwoColumnField(leftTitle: String, leftValue: String, rightTitle: String, rightValue: String, boldValues: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(leftTitle, fontSize = 11.sp, color = Color(0xFF7A7B84), lineHeight = 13.sp)
            Text(leftValue, fontSize = 14.sp, color = Color.Black, fontWeight = if (boldValues) FontWeight.Bold else FontWeight.Normal, lineHeight = 17.sp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(rightTitle, fontSize = 11.sp, color = Color(0xFF7A7B84), textAlign = TextAlign.End, lineHeight = 13.sp)
            Text(rightValue, fontSize = 14.sp, color = Color.Black, fontWeight = if (boldValues) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.End, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun TicketCutoutDivider() {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        val r = 6.dp.toPx()
        val cy = size.height / 2f
        // Only the inner half of each circle is visible, creating the small,
        // subtle ticket notches right at both true edges of the white card.
        drawCircle(
            color = PageBg,
            radius = r,
            center = Offset(0f, cy)
        )
        drawCircle(
            color = PageBg,
            radius = r,
            center = Offset(size.width, cy)
        )
        val effect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
        drawLine(
            color = Color(0xFFD7D9E0),
            start = Offset(r, cy),
            end = Offset(size.width - r, cy),
            strokeWidth = 1f,
            pathEffect = effect
        )
    }
}

private fun buildQrPayload(data: TicketData): String = buildString {
    appendLine("RAIL JOURNEY TICKET")
    appendLine("Journey Ticket=${data.journeyTicket}")
    appendLine("Service No=${data.serviceNo}")
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
