# Emergency Room Simulation and Workforce Optimization

This project models and optimizes the operation of an Emergency Room (ER) under both routine and surge conditions. It combines a discrete-event simulation of patient flow with linear programming (LP)-based optimization of staff scheduling. The system is fully configurable and includes an interactive dashboard for real-time visualization.

## Overview
The system models:
- Patient arrivals using a time-varying Poisson process or a Discrete Event Simulator (DES)
- Patient triage using modular triage classifiers (CTAS, ESI, MTS)
- Treatment times based on triage level
- Waiting room and treatment room capacity constraints
- Dynamic staff allocation across shifts
- Legal and contractual staff constraints (Oregon labor laws and ACGME rules)
- Staff scheduling via binary integer linear programming
- Interactive REST API and dashboard

## Key Components

### simulation package
Implements core ER dynamics:
- PoissonSimulator: hour-level simulation using a time-varying Poisson process
- DES: minute-level discrete-event simulation for detailed tracking
- EmergencyRoom: manages patient queues, room availability, staff resources
- Patient: stores patient metadata and timing
- TriageClassifier: interface with implementations for CTAS, ESI, MTS


### staff package
Models all ER personnel:
- StaffMemberInterface and concrete classes (Nurse, Physician, ResidentPhysician, AdminClerk)
- OregonStaffingRules: computes staffing demand based on patient volume and acuity
- Demand objects drive LP scheduling based on simulated patient flow


### scheduling package
Optimizes workforce allocation:
- NurseScheduler, PhysicianScheduler, ResidentPhysicianScheduler, AdminStaffScheduler
- Builds and solves binary LP models with Google OR-Tools
- Enforces:
    - Maximum shift limits
    - Rest periods
    - Weekly hour limits
    - Demand coverage for each shift
- Extracts optimized schedules and integrates them with simulation and dashboard

### `webserver` and Dashboard

Provides an interactive frontend:

- Embedded Spark server exposes REST API
- Dashboard displays:
  - Patient flow over time
  - Triage level distribution
  - Staff utilization
  - Rejections and key performance indicators (KPIs)
- Configuration driven via centralized `config.json` file

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven

### Running the Simulation

```bash
mvn clean compile exec:java -Dexec.mainClass="Main"

mvn clean compile exec:java '-Dexec.mainClass=Main'
```

## Running the Webserver
``` bash
mvn clean compile exec:java -Dexec.mainClass="WebServer"
```
Then open the dashboard at: http://localhost:8080

## Simulation Parameters

Configurable via src/main/resources/config.json:
- ER capacity (treatment rooms, waiting room size)
- Patient arrival patterns and triage model
- Treatment time distributions
- Staffing limits and policies
- Wages and staff counts
- Legal constraints (rest periods, max weekly hours)

## Output and Visualization

The system produces:
- Time-series data: arrivals, wait times, resource utilization
- Summary statistics: treated, rejected, in-queue patients
- Triage distribution
- Staff utilization and shift assignments
- Cost of optimized workforce plans

Data is available through:
- REST API endpoints (/api/simulation/run, /api/simulation/chartdata, etc.)
- Interactive dashboard (visual charts + KPIs)

## Project Goals
The system is designed to support:
- Operational optimization of ER staffing
- Resilience testing under demand surges
- Comparative evaluation of triage models
- Visualization of patient and staff dynamics
- Configurable scenario planning

## License
MIT License

## Acknowledgements
Developed as part of Project 2-2, Maastricht University, Data Science and Artificial Intelligence.
Contributors: Emre Arac, Eldar Ulanov, Mason Decker, Olaf Deckers, Jesse Hoydonckx, Timur Schmidt, Vlad Stefan
