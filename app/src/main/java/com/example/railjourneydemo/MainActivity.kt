@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.railjourneydemo

import android.content.Context
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
import androidx.compose.material.icons.filled.Delete
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
import org.json.JSONArray
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

/** A saved journey's re-usable booking details (timing fields are intentionally
 * excluded, since those always default to "now"). One passenger can have several
 * of these — one per distinct route — since the id is passenger + route. */
private data class SavedJourney(
    val id: String,
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
) {
    val label: String get() = "$passengerName  —  $origin → $destination"
}

private const val PROFILES_PREFS = "rail_journey_profiles"
private const val SAVED_JOURNEYS_KEY = "saved_journeys_json"

/** Same passenger + same route overwrites the existing saved entry; a different
 * route for the same passenger is stored as an additional, separate entry —
 * this is how one passenger ends up with multiple saved journeys. */
private fun savedJourneyId(passengerName: String, origin: String, destination: String): String =
    "${passengerName.trim().lowercase()}|${origin.trim().uppercase()}|${destination.trim().uppercase()}"

private fun loadSavedJourneys(context: Context): List<SavedJourney> {
    val prefs = context.getSharedPreferences(PROFILES_PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getString(SAVED_JOURNEYS_KEY, null) ?: return emptyList()
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val json = array.optJSONObject(i) ?: return@mapNotNull null
            SavedJourney(
                id = json.optString("id"),
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
        }.sortedBy { it.label.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun persistSavedJourneys(context: Context, journeys: List<SavedJourney>) {
    val array = JSONArray()
    journeys.forEach { j ->
        array.put(
            JSONObject().apply {
                put("id", j.id)
                put("passengerName", j.passengerName)
                put("mobile", j.mobile)
                put("origin", j.origin)
                put("distance", j.distance)
                put("destination", j.destination)
                put("via", j.via)
                put("adults", j.adults)
                put("children", j.children)
                put("className", j.className)
                put("trainType", j.trainType)
                put("ticketType", j.ticketType)
                put("fare", j.fare)
                put("irNumber", j.irNumber)
                put("serviceNo", j.serviceNo)
            }
        )
    }
    val prefs = context.getSharedPreferences(PROFILES_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putString(SAVED_JOURNEYS_KEY, array.toString()).apply()
}

/** Saves [journey], returning the refreshed list. A journey with the same
 * passenger + route replaces the existing one instead of duplicating it. */
private fun saveJourney(context: Context, journey: SavedJourney): List<SavedJourney> {
    if (journey.passengerName.trim().isEmpty()) return loadSavedJourneys(context)
    val updated = loadSavedJourneys(context).filterNot { it.id == journey.id } + journey
    persistSavedJourneys(context, updated)
    return loadSavedJourneys(context)
}

private fun deleteJourney(context: Context, id: String): List<SavedJourney> {
    val updated = loadSavedJourneys(context).filterNot { it.id == id }
    persistSavedJourneys(context, updated)
    return updated
}

private val HeaderBlue = Color(0xFF1730D9)
private val PageBg = Color(0xFFE9EDF9)
private val TextBlue = Color(0xFF303C68)
private val TicketBlack = Color(0xFF17171C)
private val Yellow = Color(0xFFFFF52D)
private val RedOrange = Color(0xFFFF4A28)
private val DateOrange = Color(0xFFFFA21A)
private val Cyan = Color(0xFF67CDE6)
private val RailwayGrey = Color(0xFF9A9AA5)
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

    var savedJourneys by remember { mutableStateOf(loadSavedJourneys(context)) }

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
                    savedJourneys = savedJourneys,
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
                    onLoadJourney = { id ->
                        savedJourneys.find { it.id == id }?.let { j ->
                            passengerName = j.passengerName
                            mobile = j.mobile
                            origin = j.origin
                            distance = j.distance
                            destination = j.destination
                            via = j.via
                            adults = j.adults
                            children = j.children
                            className = j.className
                            trainType = j.trainType
                            ticketType = j.ticketType
                            fare = j.fare
                            irNumber = j.irNumber
                            serviceNo = j.serviceNo
                        }
                    },
                    onSaveJourney = {
                        fare = formatFare(fare)
                        savedJourneys = saveJourney(
                            context,
                            SavedJourney(
                                id = savedJourneyId(passengerName, origin, destination),
                                passengerName = passengerName.trim(),
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
                                fare = fare,
                                irNumber = irNumber,
                                serviceNo = serviceNo,
                            )
                        )
                    },
                    onDeleteJourney = { id ->
                        savedJourneys = deleteJourney(context, id)
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
    savedJourneys: List<SavedJourney>,
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
    onLoadJourney: (String) -> Unit,
    onSaveJourney: () -> Unit,
    onDeleteJourney: (String) -> Unit,
    onGenerateTicket: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        // Fixed header — stays anchored at the top and never scrolls with the content.
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
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (savedJourneys.isNotEmpty()) {
                SectionCard(title = "Saved Journeys", icon = Icons.Default.History) {
                    var expanded by remember { mutableStateOf(false) }
                    Text(
                        "A passenger can have several saved journeys — one per route. " +
                            "Pick one below to fill in every field instantly.",
                        fontSize = 12.sp,
                        color = Color(0xFF7A7B84)
                    )
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = "Select a saved journey",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Load saved journey") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            colors = fieldColors()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            savedJourneys.forEach { journey ->
                                DropdownMenuItem(
                                    text = { Text(journey.label, fontSize = 13.sp) },
                                    onClick = {
                                        onLoadJourney(journey.id)
                                        expanded = false
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                onDeleteJourney(journey.id)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete saved journey",
                                                tint = Color(0xFFB3261E),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
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
                onClick = onSaveJourney,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.4.dp, HeaderBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save This Journey", fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
    val minutes = secondsLeft / 60
    val seconds = secondsLeft % 60
    val countdown = "%02d:%02d".format(minutes, seconds)
    val qrPayload = buildQrPayload(data)
    val qrBitmap = remember(qrPayload) { generateQrBitmap(qrPayload, 560) }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        // Fixed header — stays anchored at the top and never scrolls with the content.
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
                        .size(42.dp)
                        .border(1.5.dp, Color.White, CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Booking Details", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Mobile: ${data.mobile}", color = Color.White, fontSize = 14.sp)
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {

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

        // QR panel has square edges and reaches both screen edges. No cutout
        // notches here — those only belong on the ticket card above.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
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
        // clearly above the "Do you know?" panel — a bit more breathing
        // room than a bare divider line.
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
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
            Text("IR recovers only 57% of cost of travel on an average.", fontSize = 17.sp, color = Color(0xFF6B6C75), lineHeight = 23.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "This ticket is booked on a personal user ID. It’s sale/purchase is an offence u/s 143 of the Railways Act, 1989",
                fontSize = 17.sp,
                color = Color(0xFF6B6C75),
                lineHeight = 23.sp
            )
            Spacer(Modifier.height(10.dp))
            Text("For enquiry and integrated railway helpline, please dial 139.", fontSize = 17.sp, color = Color(0xFF6B6C75), lineHeight = 23.sp)
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
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(PageBg)
    ) {
        // Full-width blue tint above the black preview — same thickness and
        // curve as the blue strip at the end of the ticket card. Its height
        // matches the corner radius above so the rounding never reaches into
        // the black box.
        Box(Modifier.fillMaxWidth().height(14.dp).background(Cyan))

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
        // Half as thick as the strip above the black preview.
        Box(
            Modifier
                .fillMaxWidth(0.45f)
                .height(5.dp)
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
            val effect = PathEffect.dashPathEffect(floatArrayOf(28f, 12f), 0f)
            val x = if (drawDividerOnRight) size.width - 1.5f else 1.5f
            drawLine(
                color = RailwayGrey,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.4f,
                pathEffect = effect
            )

            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.rotate(-90f, size.width / 2f, size.height / 2f)
                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(154, 154, 165)
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
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(0.7f)) {
                    Text(
                        "Journey Ticket",
                        fontSize = 11.sp,
                        color = Color(0xFF7A7B84),
                        fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp
                    )
                    Text(
                        data.journeyTicket,
                        fontSize = 14.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        lineHeight = 15.sp
                    )
                }
                // Overlaid rather than placed in the same Row as the label, so
                // the badge's own padding never pushes the ticket code down.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(50))
                        .background(GreenBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "●  ACTIVE",
                        color = GreenText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
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
                    Text("Via: ${data.via}", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("IR:${data.irNumber}", color = Color.Black, fontSize = 13.sp, letterSpacing = 0.6.sp)
        }

        // Cutout notches sit right at the card's true edges, so this divider
        // spans the full card width rather than the inset content width.
        TicketCutoutDivider()

        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "*Valid for start of journey within 1 hour or until departure of the first train.",
                fontSize = 12.sp,
                color = Color(0xFF7A7B84),
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))
        }

        // Blue border below "Valid for..." spans edge to edge of the white card,
        // as a solid color — the same blue used around the black preview box.
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(Cyan)
        )
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
            color = Color(0xFFFF3B4E),
            fontWeight = FontWeight.Normal,
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
            Text(leftTitle, fontSize = 11.sp, color = Color(0xFF7A7B84), fontWeight = FontWeight.Bold, lineHeight = 13.sp)
            Text(leftValue, fontSize = 14.sp, color = Color.Black, fontWeight = if (boldValues) FontWeight.Bold else FontWeight.Normal, lineHeight = 17.sp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(rightTitle, fontSize = 11.sp, color = Color(0xFF7A7B84), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, lineHeight = 13.sp)
            Text(rightValue, fontSize = 14.sp, color = Color.Black, fontWeight = if (boldValues) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.End, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun TicketCutoutDivider() {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        val r = 12.dp.toPx()
        val cy = size.height / 2f
        // Only the inner half of each circle is visible, creating the ticket
        // notches right at both true edges of the white card.
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
        val effect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
        drawLine(
            color = Color(0xFFD7D9E0),
            start = Offset(r, cy),
            end = Offset(size.width - r, cy),
            strokeWidth = 1.3f,
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
