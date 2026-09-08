# RailJourneyDemo

A Kotlin + Jetpack Compose Android app for creating a customizable **mock railway-ticket-style preview**.

## What it includes

- Manual input for originating station, distance, ending station, via, passenger counts, dates/times, class, train type, ticket type, and fare.
- A 15-character uppercase hexadecimal reference (0–9, A–F), with a Generate button.
- A dynamic 5:00 countdown shown inside the black preview panel.
- A preview layout inspired by the supplied screenshot, but explicitly labeled **DEMO / NOT A REAL TICKET** and without official railway branding.

## Build

Open this folder in Android Studio and let Gradle sync. Run on an Android device/emulator with Android API 24+.

## Note

This project is intended for UI prototyping/testing. Do not represent the generated screen as an official or valid travel ticket.

### Train Type options
The Train Type field on the booking-details page is a dropdown with exactly these options:
- ORDINARY
- MAIL/EXPRESS
- SUPERFAST
