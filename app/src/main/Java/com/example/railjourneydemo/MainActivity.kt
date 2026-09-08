package com.example.railjourneydemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class TicketData(
    val passengerName: String,
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
    val reference: String,
)

private val HeaderBlue = Color(0xFF1426D9)
private val PageBg = Color(0xFFE9EDF9)
private val TextBlue = Color(0xFF303C68)
private val TicketBlack = Color(0xFF17171C)
private val Yellow = Color(0xFFFFF52D)
private val RedOrange = Color(0xFFFF4A28)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RailJourneyApp() }
    }
}

@Composable
private fun RailJourneyApp() {
    var showTicket by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(300) }

    var passengerName by remember { mutableStateOf("Aman") }
    var origin by remember { mutableStateOf("RAJENDRANAGAR T") }
    var distance by remember { mutableStateOf("3 km") }
    var destination by remember { mutableStateOf("PATNA JN.") }
    var bookingDateTime by remember { mutableStateOf("10 Mar 2026, 23:15") }
    var via by remember { mutableStateOf("---") }
    var adults by remember { mutableStateOf("1") }
    var children by remember { mutableStateOf("0") }
    var bookedOn by remember { mutableStateOf("10/03/2026 23:15") }
    var validTill by remember { mutableStateOf("11/03/2026 02:15") }
    var className by remember { mutableStateOf("SECOND") }
    var trainType by remember { mutableStateOf("MAIL/EXPRESS") }
    var ticketType by remember { mutableStateOf("JOURNEY") }
    var fare by remember { mutableStateOf("35.00") }
    var reference by remember { mutableStateOf(generateHexRef()) }

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
        passengerName, origin, distance, destination, bookingDateTime, via, adults, children,
        bookedOn, validTill, className, trainType, ticketType, fare, reference
    )

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = PageBg) {
            if (showTicket) {
                TicketScreen(data, secondsLeft, onBack = { showTicket = false })
            } else {
                InputScreen(
                    data = data,
                    setPassengerName = { passengerName = it },
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
                    setReference = { reference = sanitizeHex(it) },
                    onGenerateTicket = { showTicket = true }
                )
            }
        }
    }
}

private fun sanitizeHex(value: String): String =
    value.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(15)

private fun generateHexRef(): String = buildString {
    repeat(15) { append("0123456789ABCDEF"[Random.nextInt(16)]) }
}

@Composable
private fun InputScreen(
    data: TicketData,
    setPassengerName: (String) -> Unit,
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
    setReference: (String) -> Unit,
    onGenerateTicket: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Input page: the only action at the bottom is Generate Ticket.
        Row(
            Modifier.fillMaxWidth().background(HeaderBlue).padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Booking Details", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Field("Passenger Name", data.passengerName, setPassengerName, Modifier.fillMaxWidth())
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
                TrainTypeField(data.trainType, setTrainType, Modifier.weight(1f))
                Field("Ticket Type", data.ticketType, setTicketType, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Field("Fare (₹)", data.fare, setFare, Modifier.weight(1f))
                Field("IR No. (15 characters)", data.reference, setReference, Modifier.weight(1f))
            }
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
private fun TrainTypeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("ORDINARY", "MAIL/EXPRESS", "SUPERFAST")

    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text("Train Type") },
            singleLine = true,
            readOnly = true,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HeaderBlue,
                unfocusedBorderColor = Color(0xFFB7C4E2),
                focusedLabelColor = HeaderBlue,
                unfocusedLabelColor = TextBlue
            )
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.32f)
        ) {
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
    val minutes = secondsLeft / 60
    val seconds = secondsLeft % 60
    val countdown = "%02d:%02d".format(minutes, seconds)

    Column(Modifier.fillMaxSize().background(PageBg).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().background(HeaderBlue).padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                Text("←", color = Color.White, fontSize = 38.sp)
            }
            Column(Modifier.weight(1f)) {
                Text("Booking Details", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Mobile: *", color = Color.White, fontSize = 15.sp)
            }
            Text("↗", color = Color.White, fontSize = 34.sp)
        }

        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            Text("Thank You ${data.passengerName}, Happy Journey !", fontSize = 19.sp, color = TextBlue)
            Spacer(Modifier.height(20.dp))

            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(TicketBlack)
            ) {
                Row(Modifier.fillMaxWidth().height(360.dp), verticalAlignment = Alignment.CenterVertically) {
                    SideRailwayText()
                    Column(
                        Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Dynamic preview will close in", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(countdown, color = RedOrange, fontSize = 51.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(2.dp))
                        Text("Ticket Booking Date & Time", color = Color.White, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(data.bookingDateTime, color = Yellow, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(26.dp))
                        Text("Ticket is Non-Transferable", color = Color.White, fontSize = 14.sp)
                    }
                    SideRailwayText()
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Journey Ticket", fontSize = 23.sp, color = TextBlue)
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(data.origin, fontSize = 18.sp, color = TextBlue, modifier = Modifier.weight(1f))
                Text("—${data.distance}—", fontSize = 15.sp, color = TextBlue, textAlign = TextAlign.Center)
                Text(data.destination, fontSize = 18.sp, color = TextBlue, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(2.dp))
            Text("T", fontSize = 18.sp, color = TextBlue)
            Spacer(Modifier.height(12.dp))

            InfoRow("Via", data.via, "Passenger", "${data.adults} Adult, ${data.children} Child")
            Spacer(Modifier.height(20.dp))
            InfoRow("Booked on", data.bookedOn, "*Valid Till", data.validTill)
            Spacer(Modifier.height(18.dp))
            Text("${data.className} | ${data.trainType} | ${data.ticketType} | ₹${data.fare}", fontSize = 17.sp, color = TextBlue)
            Spacer(Modifier.height(4.dp))
            Text(sanitizeHex(data.reference).padEnd(15, '0').take(15), fontSize = 15.sp, color = TextBlue, letterSpacing = 1.sp)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFD4D8E4)))
            Spacer(Modifier.height(27.dp))
            Text("*Valid for start of journey within 3 hour or until departure of the first train.", fontSize = 14.sp, color = TextBlue)
            Spacer(Modifier.height(28.dp))
            Text(
                "Note: This ticket is non refundable. Ticket is stored locally on the device. Please do not change your handset or perform factory reset.",
                fontSize = 16.sp,
                color = Color(0xFF9E2330),
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = { },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, HeaderBlue),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = HeaderBlue
                )
            ) {
                Text(
                    "Book Connecting Journey",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SideRailwayText() {
    Column(
        Modifier.width(54.dp).fillMaxHeight().padding(vertical = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("┆", color = Color.White, fontSize = 24.sp)
        Text("I\nN\nD\nI\nA\nN   R\nA\nI\nL\nW\nA\nY\nS", color = Color.White, fontSize = 12.sp, lineHeight = 13.sp, textAlign = TextAlign.Center)
        Text("┆", color = Color.White, fontSize = 24.sp)
    }
}

@Composable
private fun InfoRow(leftTitle: String, leftValue: String, rightTitle: String, rightValue: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(leftTitle, fontSize = 17.sp, color = TextBlue)
            Text(leftValue, fontSize = 17.sp, color = TextBlue)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(rightTitle, fontSize = 17.sp, color = TextBlue, textAlign = TextAlign.End)
            Text(rightValue, fontSize = 17.sp, color = TextBlue, textAlign = TextAlign.End)
        }
    }
}
