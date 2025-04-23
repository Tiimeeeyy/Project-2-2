package simulation;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.TimeSeries;

import javax.swing.*;

public class Chart {
    JFreeChart statsChart;
    public Chart(int[][] data) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (int dt = 0; dt<data[0].length;dt++){
            dataset.addValue(data[1][dt],"Arrivals",Integer.toString(dt));
            dataset.addValue(data[2][dt],"Waiting",Integer.toString(dt));
            dataset.addValue(data[3][dt],"Treating",Integer.toString(dt));
            dataset.addValue(data[4][dt],"Open Rooms",Integer.toString(dt));
        }
        this.statsChart = ChartFactory.createLineChart("Hourly Statistics", "Hours Since Start", "",dataset);
    }

    public void display(){
        ChartPanel panel = new ChartPanel(statsChart);
        JFrame frame = new JFrame();
        frame.setSize(600, 450);
        frame.setContentPane(panel);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
