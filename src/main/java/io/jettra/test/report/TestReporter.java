package io.jettra.test.report;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TestReporter {

    public static void writeReport(String targetDir, String className, int tests, int failures, int errors, int skipped, double time, String failureDetails) {
        File reportsDir = new File(targetDir, "jettra-test-reports");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }

        File reportFile = new File(reportsDir, "TEST-" + className + ".xml");
        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write(String.format(java.util.Locale.US, "<testsuite name=\"%s\" time=\"%.3f\" tests=\"%d\" errors=\"%d\" skipped=\"%d\" failures=\"%d\">\n", 
                    className, time, tests, errors, skipped, failures));

            // Para simplicidad, solo agregamos los fallos como detalle general de la suite o en el tag principal
            if (failures > 0 || errors > 0) {
                writer.write("  <testcase name=\"JettraTestSuite\" classname=\"" + className + "\" time=\"" + time + "\">\n");
                writer.write("    <failure message=\"Test execution failed\">\n");
                // Escape XML for failure details
                String escapedDetails = failureDetails.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                writer.write(escapedDetails + "\n");
                writer.write("    </failure>\n");
                writer.write("  </testcase>\n");
            } else {
                writer.write("  <testcase name=\"JettraTestSuite\" classname=\"" + className + "\" time=\"" + time + "\"/>\n");
            }

            writer.write("</testsuite>\n");
        } catch (IOException e) {
            System.err.println("Failed to write Surefire XML report for " + className + ": " + e.getMessage());
        }

        File htmlReportFile = new File(reportsDir, "TEST-" + className + ".html");
        try (FileWriter writer = new FileWriter(htmlReportFile)) {
            writer.write("<!DOCTYPE html>\n<html>\n<head>\n");
            writer.write("<meta charset=\"UTF-8\">\n");
            writer.write("<title>Test Report - " + className + "</title>\n");
            writer.write("<style>\n");
            writer.write("body { font-family: Arial, sans-serif; margin: 20px; }\n");
            writer.write("h1 { color: #333; }\n");
            writer.write("table { border-collapse: collapse; width: 50%; margin-bottom: 20px; }\n");
            writer.write("th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }\n");
            writer.write("th { background-color: #f4f4f4; }\n");
            writer.write(".success { color: green; font-weight: bold; }\n");
            writer.write(".failure { color: red; font-weight: bold; }\n");
            writer.write(".details { background-color: #fdfdfd; padding: 10px; border: 1px solid #eee; white-space: pre-wrap; font-family: monospace; }\n");
            writer.write("</style>\n</head>\n<body>\n");
            
            writer.write("<h1>Test Report: " + className + "</h1>\n");
            writer.write("<table>\n");
            writer.write("<tr><th>Tests</th><th>Failures</th><th>Errors</th><th>Skipped</th><th>Time (s)</th></tr>\n");
            writer.write(String.format(java.util.Locale.US, "<tr><td>%d</td><td class=\"%s\">%d</td><td>%d</td><td>%d</td><td>%.3f</td></tr>\n",
                    tests, failures > 0 ? "failure" : "success", failures, errors, skipped, time));
            writer.write("</table>\n");
            
            if (failures > 0 || errors > 0) {
                writer.write("<h2>Failure Details</h2>\n");
                String escapedDetails = failureDetails.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                writer.write("<div class=\"details\">" + escapedDetails + "</div>\n");
            } else {
                writer.write("<h2>Status</h2>\n<p class=\"success\">All tests passed successfully.</p>\n");
            }
            
            writer.write("</body>\n</html>\n");
        } catch (IOException e) {
            System.err.println("Failed to write HTML report for " + className + ": " + e.getMessage());
        }
    }
}
