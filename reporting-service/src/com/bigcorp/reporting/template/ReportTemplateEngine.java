package com.bigcorp.reporting.template;

import com.bigcorp.reporting.config.ReportConfig;
import com.bigcorp.reporting.util.ReportLogger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Report template engine that generates HTML and CSV output
 * via string concatenation.
 * 
 * We didn't want to add a template library dependency (Velocity, FreeMarker)
 * because the IT team said "no new JARs on the classpath without an
 * architecture review board meeting." So we just build the HTML manually.
 * 
 * @author contractor (reporting team)
 * @since 2013-Q2
 */
public class ReportTemplateEngine {

    private static final ReportLogger log = new ReportLogger(ReportTemplateEngine.class);

    /**
     * Generate an HTML report from tabular data.
     * 
     * @param title   report title
     * @param columns column names (keys in the row maps)
     * @param headers display headers for each column
     * @param rows    list of Maps (String -> String)
     * @return complete HTML document as a string
     */
    public static String generateHtmlReport(String title, String[] columns, String[] headers, List rows) {
        SimpleDateFormat sdf = new SimpleDateFormat(ReportConfig.TIMESTAMP_FORMAT);
        String generatedAt = sdf.format(new Date());

        StringBuffer html = new StringBuffer();
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("<meta charset=\"" + ReportConfig.HTML_CHARSET + "\">\n");
        html.append("<title>" + ReportConfig.HTML_TITLE_PREFIX + title + "</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("h1 { color: #333366; }\n");
        html.append("table { border-collapse: collapse; width: 100%; }\n");
        html.append("th { background-color: #333366; color: white; padding: 8px; text-align: left; }\n");
        html.append("td { border: 1px solid #ddd; padding: 6px; }\n");
        html.append("tr:nth-child(even) { background-color: #f2f2f2; }\n");
        html.append(".footer { margin-top: 20px; font-size: 10px; color: #999; }\n");
        html.append("</style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("<h1>" + title + "</h1>\n");
        html.append("<p>Generated: " + generatedAt + "</p>\n");
        html.append("<table>\n");

        // Header row
        html.append("<tr>\n");
        for (int i = 0; i < headers.length; i++) {
            html.append("<th>" + headers[i] + "</th>\n");
        }
        html.append("</tr>\n");

        // Data rows
        if (rows != null) {
            for (int r = 0; r < rows.size(); r++) {
                Map row = (Map) rows.get(r);
                html.append("<tr>\n");
                for (int c = 0; c < columns.length; c++) {
                    String val = (String) row.get(columns[c]);
                    html.append("<td>" + (val != null ? val : "") + "</td>\n");
                }
                html.append("</tr>\n");
            }
        }

        html.append("</table>\n");

        if (rows == null || rows.isEmpty()) {
            html.append("<p><i>No data available for this report.</i></p>\n");
        }

        html.append("<div class=\"footer\">\n");
        html.append("<p>BigCorp Reporting Service &copy; 2013 | Report rows: " + (rows != null ? rows.size() : 0) + "</p>\n");
        html.append("</div>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        log.info("Generated HTML report: " + title + " (" + (rows != null ? rows.size() : 0) + " rows)");
        return html.toString();
    }

    /**
     * Generate a CSV report from tabular data.
     * 
     * @param columns column names (keys in the row maps)
     * @param headers display headers for each column
     * @param rows    list of Maps (String -> String)
     * @return CSV content as a string
     */
    public static String generateCsvReport(String[] columns, String[] headers, List rows) {
        StringBuffer csv = new StringBuffer();

        // Header line
        for (int i = 0; i < headers.length; i++) {
            if (i > 0) csv.append(ReportConfig.CSV_DELIMITER);
            csv.append(escapeCSV(headers[i]));
        }
        csv.append("\n");

        // Data lines
        if (rows != null) {
            for (int r = 0; r < rows.size(); r++) {
                Map row = (Map) rows.get(r);
                for (int c = 0; c < columns.length; c++) {
                    if (c > 0) csv.append(ReportConfig.CSV_DELIMITER);
                    String val = (String) row.get(columns[c]);
                    csv.append(escapeCSV(val != null ? val : ""));
                }
                csv.append("\n");
            }
        }

        log.info("Generated CSV report (" + (rows != null ? rows.size() : 0) + " rows)");
        return csv.toString();
    }

    /**
     * Escape a value for CSV output.
     * If value contains comma, quote, or newline, wrap in quotes.
     */
    private static String escapeCSV(String value) {
        if (value == null) return "";
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
