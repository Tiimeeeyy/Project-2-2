let patientFlowChart = null;
let triageChart = null;
let utilizationChart = null;
let hyperparameters = {};

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('runSimulation').addEventListener('click', runSimulation);
    document.getElementById('configBtn').addEventListener('click', showConfigModal);
    document.getElementById('saveConfig').addEventListener('click', saveConfiguration);
    document.querySelector('.close').addEventListener('click', closeConfigModal);
    
    window.addEventListener('click', function(event) {
        const modal = document.getElementById('configModal');
        if (event.target === modal) {
            closeConfigModal();
        }
    });
    
    initializeCharts();
    loadDefaultConfiguration();
    loadScenarios();
});


async function runSimulation() {
    const days = document.getElementById('days').value;
    const arrivalFunction = document.getElementById('scenario').value;
    const triageLevel = document.getElementById('triageLevel').value;
    const triageClassifier = document.getElementById('triageClassifier').value;
    const statusElement = document.getElementById('status');
    
    statusElement.textContent = 'Running DES simulation...';
    statusElement.style.backgroundColor = '#ffd54f';
    
    try {
        const requestBody = {
            days: parseInt(days),
            arrivalFunction: arrivalFunction,
            hyperparameters: hyperparameters
        };
        
        if (triageLevel && triageLevel !== '') {
            requestBody.triageLevel = triageLevel;
        }
        
        if (triageClassifier && triageClassifier !== '') {
            requestBody.triageClassifier = triageClassifier;
        }
        
        const simResponse = await fetch('/api/simulation/run', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        });
        
        if (!simResponse.ok) {
            throw new Error(`Server returned ${simResponse.status}`);
        }
        
        const simData = await simResponse.json();
        const chartResponse = await fetch('/api/simulation/chartdata');
        const chartData = await chartResponse.json();
        
        const triageResponse = await fetch('/api/patients/triage');
        const triageData = await triageResponse.json();
        
        const utilitiesResponse = await fetch('/api/simulation/utilities');
        const utilitiesData = await utilitiesResponse.json();
        
        updateCharts(chartData, triageData);
        updateStatistics(simData);
        updateUtilities(utilitiesData);
        
        statusElement.textContent = 'DES simulation complete';
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
            labels: [
                'Immediate', 
                'Emergent', 
                'Urgent', 
                'Semi-urgent', 
                'Non-urgent'
            ],
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
    // Update patient flow chart
    patientFlowChart.data.labels = labels;
    
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

// Configuration functions
function showConfigModal() {
    document.getElementById('configModal').style.display = 'block';
}

function closeConfigModal() {
    document.getElementById('configModal').style.display = 'none';
}

function saveConfiguration() {
    hyperparameters = {
        interarrivalTime: parseFloat(document.getElementById('interarrivalTime').value),
        treatmentCapacity: parseInt(document.getElementById('treatmentCapacity').value),
        waitingCapacity: parseInt(document.getElementById('waitingCapacity').value)
    };
    
    closeConfigModal();
    
    const statusElement = document.getElementById('status');
    const originalText = statusElement.textContent;
    const originalColor = statusElement.style.backgroundColor;
    
    statusElement.textContent = 'Configuration saved';
    statusElement.style.backgroundColor = '#81c784';
    
    setTimeout(() => {
        statusElement.textContent = originalText;
        statusElement.style.backgroundColor = originalColor;
    }, 2000);
}

async function loadDefaultConfiguration() {
    try {
        const response = await fetch('/api/config/hyperparameters');
        const defaults = await response.json();
        
        document.getElementById('interarrivalTime').value = defaults.interarrivalTime || 15;
        document.getElementById('treatmentCapacity').value = defaults.treatmentCapacity || 15;
        document.getElementById('waitingCapacity').value = defaults.waitingCapacity || 30;
        
        hyperparameters = defaults;
    } catch (error) {
        console.error('Error loading default configuration:', error);
    }
}

async function loadScenarios() {
    try {
        const response = await fetch('/api/config/scenarios');
        const scenarios = await response.json();
        
        const scenarioSelect = document.getElementById('scenario');
        // Clear existing options
        scenarioSelect.innerHTML = '';
        
        // Add scenarios from API
        scenarios.forEach(scenario => {
            const option = document.createElement('option');
            option.value = scenario.value;
            option.textContent = scenario.label;
            option.title = scenario.description; // Show description on hover
            scenarioSelect.appendChild(option);
        });
        
        // Set default selection to sinusoidal_24h if available
        const defaultOption = scenarioSelect.querySelector('option[value="sinusoidal_24h"]');
        if (defaultOption) {
            defaultOption.selected = true;
        }
    } catch (error) {
        console.error('Error loading scenarios:', error);
        // Fallback to hardcoded scenarios if API fails
        const scenarioSelect = document.getElementById('scenario');
        scenarioSelect.innerHTML = `
            <option value="sinusoidal_24h">Regular Operation</option>
            <option value="constant_2x">Emergency Scenario</option>
        `;
    }
}

function updateUtilities(utilities) {
    const utilityArea = document.getElementById('utilityArea');
    
    if (utilities.error) {
        utilityArea.innerHTML = '<p>Error loading utility metrics</p>';
        return;
    }
    
    utilityArea.innerHTML = `
        <div class="statistics-grid">
            <div class="stat-item">
                <div class="stat-label">Room Utilization</div>
                <div class="stat-value">${utilities.roomUtilization || 0}%</div>
            </div>
            <div class="stat-item">
                <div class="stat-label">Throughput</div>
                <div class="stat-value">${utilities.throughput || 0}%</div>
            </div>
            <div class="stat-item">
                <div class="stat-label">Rejection Rate</div>
                <div class="stat-value">${utilities.rejectionRate || 0}%</div>
            </div>
            <div class="stat-item">
                <div class="stat-label">System Type</div>
                <div class="stat-value" style="font-size: 16px; color: #2196F3;">DES</div>
            </div>
        </div>
    `;
}