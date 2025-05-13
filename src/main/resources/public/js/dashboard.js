let patientFlowChart = null;
let triageChart = null;
let utilizationChart = null;

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('runSimulation').addEventListener('click', runSimulation);
    initializeCharts();
});


async function runSimulation() {
    const days = document.getElementById('days').value;
    const statusElement = document.getElementById('status');
    
    statusElement.textContent = 'Running simulation...';
    statusElement.style.backgroundColor = '#ffd54f';
    
    try {
        const simResponse = await fetch('/api/simulation/run', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ days: parseInt(days) })
        });
        
        if (!simResponse.ok) {
            throw new Error(`Server returned ${simResponse.status}`);
        }
        
        const simData = await simResponse.json();
        const chartResponse = await fetch('/api/simulation/chartdata');
        const chartData = await chartResponse.json();
        
        const triageResponse = await fetch('/api/patients/triage');
        const triageData = await triageResponse.json();
        
        updateCharts(chartData, triageData);
        updateStatistics(simData);
        
        statusElement.textContent = 'Simulation complete';
        statusElement.style.backgroundColor = '#81c784';
        
    } catch (error) {
        console.error('Error running simulation:', error);
        statusElement.textContent = 'Error: ' + error.message;
        statusElement.style.backgroundColor = '#ef5350';
    }
}


function initializeCharts() {
    // Patient flow chart
    const patientFlowCtx = document.getElementById('patientFlowChart').getContext('2d');
    patientFlowChart = new Chart(patientFlowCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'New Arrivals',
                    data: [],
                    borderColor: '#4285F4',
                    backgroundColor: 'rgba(66, 133, 244, 0.1)',
                    tension: 0.1
                },
                {
                    label: 'Waiting Patients',
                    data: [],
                    borderColor: '#FBBC05',
                    backgroundColor: 'rgba(251, 188, 5, 0.1)',
                    tension: 0.1
                },
                {
                    label: 'In Treatment',
                    data: [],
                    borderColor: '#34A853',
                    backgroundColor: 'rgba(52, 168, 83, 0.1)',
                    tension: 0.1
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: {
                duration: 500
            },
            plugins: {
                title: { display: true, text: 'Patient Flow Over Time' }
            },
            scales: {
                y: { beginAtZero: true, title: { display: true, text: 'Number of Patients' } },
                x: { title: { display: true, text: 'Hours' } }
            }
        }
    });
    
    // Triage distribution chart
    const triageCtx = document.getElementById('triageChart').getContext('2d');
    triageChart = new Chart(triageCtx, {
        type: 'doughnut',
        data: {
            labels: ['RED', 'ORANGE', 'YELLOW', 'GREEN', 'BLUE'],
            datasets: [{
                data: [0, 0, 0, 0, 0],
                backgroundColor: [
                    '#EA4335', // RED
                    '#FBBC05', // ORANGE
                    '#FFFF00', // YELLOW
                    '#34A853', // GREEN
                    '#4285F4'  // BLUE
                ]
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            aspectRatio: 1,
            animation: {
                duration: 500
            },
            plugins: {
                title: { display: true, text: 'Triage Level Distribution' },
                legend: { position: 'right' }
            }
        }
    });
    
    // Utilization chart
    const utilizationCtx = document.getElementById('utilizationChart').getContext('2d');
    utilizationChart = new Chart(utilizationCtx, {
        type: 'bar',
        data: {
            labels: ['Hours'],
            datasets: [{
                label: 'Open Rooms',
                data: [0],
                backgroundColor: '#81C784'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: {
                duration: 500
            },
            scales: {
                y: { 
                    beginAtZero: true, 
                    title: { display: true, text: 'Number of Rooms' }
                }
            },
            plugins: {
                title: { display: true, text: 'Treatment Room Availability' }
            }
        }
    });
}

// Update charts with new data
function updateCharts(chartData, triageData) {
    // Generate hour labels
    const labels = chartData.hours.map(h => `Hour ${h}`);
    
    // Update patient flow chart
    patientFlowChart.data.labels = labels;
    patientFlowChart.data.datasets[0].data = chartData.arrivals;
    patientFlowChart.data.datasets[1].data = chartData.waiting;
    patientFlowChart.data.datasets[2].data = chartData.treating;
    patientFlowChart.update('none');
    
    // Update triage chart
    const triageCounts = triageData.triageCounts;
    triageChart.data.datasets[0].data = [
        triageCounts.RED || 0,
        triageCounts.ORANGE || 0,
        triageCounts.YELLOW || 0,
        triageCounts.GREEN || 0,
        triageCounts.BLUE || 0
    ];
    triageChart.update('none');
    
    // Update utilization chart - calculate average open rooms
    const avgOpenRooms = chartData.openRooms.reduce((a, b) => a + b, 0) / chartData.openRooms.length;
    utilizationChart.data.datasets[0].data = [avgOpenRooms];
    utilizationChart.update('none');
}

function updateStatistics(data) {
    const statsArea = document.getElementById('statisticsArea');
    
    const totalPatients = data.patientsProcessed + data.patientsRejected;
    const rejectionRate = ((data.patientsRejected / totalPatients) * 100).toFixed(1);
    
    statsArea.innerHTML = `
        <div class="statistics-grid">
            <div class="stat-item">
                <div class="stat-label">Simulation Days</div>
                <div class="stat-value">${data.simulationTime}</div>
            </div>
            <div class="stat-item">
                <div class="stat-label">Total Patients</div>
                <div class="stat-value">${totalPatients}</div>
            </div>
            <div class="stat-item">
                <div class="stat-label">Patients Processed</div>
                <div class="stat-value">${data.patientsProcessed}</div>
            </div>
            <div class="stat-item">
                <div class="stat-label">Patients Rejected</div>
                <div class="stat-value">${data.patientsRejected}</div>
            </div>
            <div class="stat-item">
                <div class="stat-label">Rejection Rate</div>
                <div class="stat-value">${rejectionRate}%</div>
            </div>
            <div class="stat-item">
                <div class="stat-label">Status</div>
                <div class="stat-value" style="font-size: 18px; color: #4CAF50;">Complete</div>
            </div>
        </div>
    `;
}