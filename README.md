# Emergency Room Simulation

This project simulates an Emergency Room (ER) in a Dutch city with approximately 150,000 residents. It uses a Poisson process to model patient arrivals and exponential distributions to model treatment times.

## Overview

The simulation models:
- simulation.Patient arrivals following a Poisson distribution
- simulation.Patient triage based on severity levels (RED, ORANGE, YELLOW, GREEN, BLUE)
- Treatment time distributions based on triage levels
- Waiting room capacity constraints
- Treatment room availability
- Time-of-day and weekend variations in patient arrivals

## Key Components

### simulation.Patient
Represents individuals seeking emergency care with attributes like:
- Unique identifier
- Name and age
- Triage level
- Arrival, treatment start, and discharge timestamps

### simulation.EmergencyRoom
Models the ER facility with:
- Waiting room capacity
- Priority queue for patient triage
- Treatment room management
- simulation.Patient flow controls

### simulation.PoissonSimulator
The main simulation engine that:
- Generates patients according to statistical models
- Processes arrivals, treatments, and discharges
- Collects and reports statistics
- Adjusts parameters based on time factors

## Getting Started

### Prerequisites
- Java 11 or higher
- Maven

### Running the Simulation
```bash
mvn clean compile exec:java -Dexec.mainClass="Main"
```
or
```bash
mvn clean compile exec:java '-Dexec.mainClass=Main'
```

## Simulation Parameters

Key parameters that can be adjusted:
- Population size (currently 150,000)
- ER capacity and treatment rooms
- Simulation duration
- Average patient arrival rate
- Treatment time distributions by triage level

## Output Statistics

The simulation produces statistics including:
- Total patients treated
- Patients rejected due to capacity constraints
- Average waiting times
- Average treatment times
- Resource utilization metrics
