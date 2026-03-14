package com.example.project.controller;

import com.example.project.model.AttendanceRecord;
import com.example.project.model.AttendanceSoapData;
import com.example.project.model.AttendanceReportLog;
import com.example.project.service.AttendanceService;
import com.example.project.service.EmailService;
import com.example.project.service.WeeklyAttendanceReportService;
import com.example.project.service.AttendanceReportLogService;
import com.example.project.soap.SoapClient;
import com.example.project.soap.SoapParser;
import jakarta.xml.soap.SOAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private static final Logger log = LoggerFactory.getLogger(AttendanceController.class);

    private final AttendanceService attendanceService;
    private final SoapClient soapClient;
    private final EmailService emailService;
    private final WeeklyAttendanceReportService weeklyReportService;
    private final AttendanceReportLogService reportLogService;

    private String buildSummary(int count, String source) {

        String label = (count == 1) ? "Employee" : "Employees";

        if ("Manual".equalsIgnoreCase(source)) {

            return count + " " + label + " (Manual)";

        } else {

            // Cron or anything else → NO TAG

            return count + " " + label;

        }

    }

    // Default recipients used when UI does not supply 'to'/'cc'
    private static final String[] DEFAULT_TO = new String[]{

    };
    private static final String[] DEFAULT_CC = new String[]{

    };

    private static final String[] DEFAULT_BCC = new String[]{

    };


    public AttendanceController(AttendanceService attendanceService,
                                SoapClient soapClient,
                                EmailService emailService,
                                WeeklyAttendanceReportService weeklyReportService
            ,AttendanceReportLogService reportLogService
    ) {
        this.attendanceService = attendanceService;
        this.soapClient = soapClient;
        this.emailService = emailService;
        this.weeklyReportService = weeklyReportService;
        this.reportLogService = reportLogService;
    }

    @GetMapping("/fetch")
    public List<AttendanceRecord> fetchAttendance(@RequestParam(required = false) String date) throws Exception {
        String targetDate = (date != null) ? date : LocalDate.now().toString();

        SOAPMessage response = soapClient.callSoap(targetDate, targetDate);
        List<AttendanceSoapData> rawData = SoapParser.parse(response);
        return attendanceService.processSoapData(rawData);
    }

    @GetMapping("")
    public ResponseEntity<Map<String, Object>> getAttendance(@RequestParam(required = false) String date,
                                                             @RequestParam(required = false, defaultValue = "daily") String type) {
        LocalDate target;
        try {
            target = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        } catch (Exception e) {
            target = LocalDate.now();
        }

        try {
            if ("daily".equalsIgnoreCase(type)) {
                SOAPMessage response = soapClient.callSoap(target.toString(), target.toString());
                List<AttendanceRecord> records = attendanceService.processSoapData(SoapParser.parse(response));
                Map<String, Object> out = new HashMap<>();
                out.put("summary", records.size());
                out.put("records", records);
                return ResponseEntity.ok(out);
            } else {
                // weekly: compute week (Monday..Sunday) for the given date's week
                LocalDate weekStart = weeklyReportService.getWeekStartMonday(target);
                LocalDate weekEnd = weekStart.plusDays(6);

                List<AttendanceRecord> aggregated = new ArrayList<>();
                LocalDate cursor = weekStart;
                while (!cursor.isAfter(weekEnd)) {
                    SOAPMessage response = soapClient.callSoap(cursor.toString(), cursor.toString());
                    aggregated.addAll(attendanceService.processSoapData(SoapParser.parse(response)));
                    cursor = cursor.plusDays(1);
                }

                // Compute unique employees for the week (one employee counted once)
                Set<String> uniqueUsers = new HashSet<>();
                for (AttendanceRecord r : aggregated) {
                    if (r != null && r.getUserId() != null) uniqueUsers.add(r.getUserId());
                }

                Map<String, Object> out = new HashMap<>();
                out.put("summary", uniqueUsers.size());
                out.put("records", aggregated);
                out.put("weekStart", weekStart.toString());
                out.put("weekEnd", weekEnd.toString());
                return ResponseEntity.ok(out);
            }
        } catch (Exception e) {
            log.error("Failed to get attendance data", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    // DTO for email POST from UI
    public static class EmailRequest {
        public List<String> to;
        public List<String> cc;
        public String subject;
        public String htmlBody;
    }

    // Simple DTO used by UI for history rows
    public static class HistoryEntry {
        public String date; // yyyy-MM-dd
        public String type; // Daily/Weekly
        public String summary;
        public String status; // Success/Failed
        public String timestamp; // when sent

        public HistoryEntry() {}

        public HistoryEntry(String date, String type, String summary, String status, String timestamp) {
            this.date = date;
            this.type = type;
            this.summary = summary;
            this.status = status;
            this.timestamp = timestamp;
        }

        // keep int overload for backward compatibility
        public HistoryEntry(String date, String type, int summary, String status, String timestamp) {
            this(date, type, String.valueOf(summary), status, timestamp);
        }
    }

    @PostMapping("/send-email")
    public ResponseEntity<Map<String, Object>> sendEmail(@RequestBody EmailRequest req) {
        if (req == null || req.to == null || req.to.isEmpty()) {
            Map<String, Object> res = new HashMap<>();
            res.put("success", false);
            res.put("message", "'to' recipient list is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
        }

        try {
            String[] toArr = req.to.toArray(new String[0]);
            String[] ccArr = (req.cc == null) ? new String[0] : req.cc.toArray(new String[0]);

            // Delegates to existing EmailService (no BCC provided for generic send)
            emailService.sendHtmlEmail(toArr, ccArr, new String[0], req.subject == null ? "Attendance Report" : req.subject, req.htmlBody == null ? "" : req.htmlBody);

            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("Failed to send email", e);
            Map<String, Object> res = new HashMap<>();
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(res);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<HistoryEntry>> getHistory() {
        // Fetch history from DB and map to HistoryEntry DTO
        List<AttendanceReportLog> logs = reportLogService.findAll();
        List<HistoryEntry> out = new ArrayList<>();
        for (AttendanceReportLog l : logs) {
            String date = (l.getReportDate() == null) ? "" : l.getReportDate().toString();
            String type = (l.getType() == null) ? "" : l.getType();
            String summary = (l.getSummary() == null) ? "" : l.getSummary();
            String status = (l.getStatus() == null) ? "" : l.getStatus();
            // createdAt has been removed from the model; keep timestamp blank for UI
            String ts = "";
            HistoryEntry he = new HistoryEntry();
            he.date = date;
            he.type = type;
            he.summary = summary;
            he.status = status;
            he.timestamp = ts;
            out.add(he);
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/send-email-html")
    public ResponseEntity<Map<String, Object>> sendAttendanceEmailHtml(@RequestParam(required = false) String date,
                                                                       @RequestParam(required = false) String to,
                                                                       @RequestParam(required = false) String cc,
                                                                       @RequestParam(required = false) String source) {

        Map<String, Object> resp = new HashMap<>();
        LocalDate target;
        try {
            target = (date != null) ? LocalDate.parse(date) : LocalDate.now();
        } catch (Exception e) {
            target = LocalDate.now();
        }

        // bring these into outer scope so outer catch can persist logs too
        String[] toArr = new String[0];
        String[] ccArr = new String[0];
        String[] bccArr = new String[0];
        String subject = "";
        String dailyHtml = ""; // final HTML string (built below)

        try {
            // Build records and HTML body (unchanged behavior)
            SOAPMessage response = soapClient.callSoap(target.toString(), target.toString());
            List<AttendanceRecord> records = attendanceService.processSoapData(SoapParser.parse(response));
            records.sort(Comparator.comparingInt(r -> Integer.parseInt(r.getUserId())));

            StringBuilder sb = new StringBuilder();
            // build HTML into sb then assign to dailyHtml string
            sb.append("<p>Dear Team,</p>");
            sb.append("<p>Please find the Attendance Report for ")
                    .append(target.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .append(".</p>");

            sb.append("<p style='font-size:16px; font-weight:bold;'>")
                    .append("Total Employees: ").append(records.size()).append("</p>");

            int redCount = 0;     // Less than 5 hours
            int yellowCount = 0;  // 5 to 8 hours

            sb.append("<table border='1' cellpadding='0' cellspacing='0' style='border-collapse: collapse;'>");
            sb.append("<tr style='padding:10px;'>")
                    .append("<th style='padding:10px;'>Employee ID</th>")
                    .append("<th style='padding:10px;'>Employee Name</th>")
                    .append("<th style='padding:10px;'>IN</th>")
                    .append("<th style='padding:10px;'>OUT</th>")
                    .append("<th style='padding:10px;'>Duration</th>")
                    .append("</tr>");

            for (AttendanceRecord r : records) {

                String in = (r.getInTime() != null)
                        ? r.getInTime().toLocalTime().toString().substring(0, 5)
                        : "";

                String out = (r.getOutTime() != null)
                        ? r.getOutTime().toLocalTime().toString().substring(0, 5)
                        : "";

                String duration = r.getDuration();

                int durHours = 0;
                try {
                    durHours = Integer.parseInt(duration.split("h")[0]);
                } catch (Exception ignore) {
                }

                // Color rules
                String bgColor = "";
                if (durHours < 5) {
                    bgColor = "background-color:tomato; color:black;";
                    redCount++;
                } else if (durHours < 8) {
                    bgColor = "background-color:yellow; color:black;";
                    yellowCount++;
                }

                sb.append("<tr>")
                        .append("<td style='padding:10px;'>").append(r.getUserId()).append("</td>")
                        .append("<td style='padding:10px;'>").append(r.getUserName()).append("</td>")
                        .append("<td style='padding:10px;'>").append(in).append("</td>")
                        .append("<td style='padding:10px;'>").append(out).append("</td>")
                        .append("<td style='").append(bgColor).append(" padding:10px;'>")
                        .append(duration)
                        .append("</td>")
                        .append("</tr>");
            }

            sb.append("</table>");
            sb.append("");
            sb.append("<p style='font-size:16px;'>")
                    .append("<span style='display:inline-block;width:15px;height:15px;background-color:tomato;")
                    .append("margin-right:5px;border:1px solid #000;'></span> ")
                    .append(redCount).append(" Records (Less than 5 hrs)</p>");

            sb.append("<p style='font-size:16px;'>")
                    .append("<span style='display:inline-block;width:15px;height:15px;background-color:yellow;")
                    .append("margin-right:5px;border:1px solid #000;'></span> ")
                    .append(yellowCount).append(" Records (between 5 to 8 hrs)</p>");

            sb.append("</br>");
            sb.append("<p style='color:#A9A9A9; font-size:17px;'>This is an automated scheduled email.</p>");
            // finalize daily HTML
            dailyHtml = sb.toString();

            subject = target.format(
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " - Attendance Report Chennai (408)";

            // If UI didn't supply recipients, fall back to controller defaults
            if (to == null || to.isBlank()) {
                toArr = DEFAULT_TO;
            } else {
                toArr = Arrays.stream(to.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);
            }

            if (cc == null || cc.isBlank()) {
                ccArr = DEFAULT_CC;
            } else {
                ccArr = Arrays.stream(cc.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);
            }

            try {
                // include DEFAULT_BCC for daily reports
                bccArr = DEFAULT_BCC;
                emailService.sendHtmlEmail(toArr, ccArr, bccArr, subject, dailyHtml);
                resp.put("success", true);
                resp.put("message", "Attendance emailed successfully to " + toArr.length + " users (CC: " + ccArr.length + ")!");

                // record history entry
                // Persist to DB
                AttendanceReportLog logEntry = new AttendanceReportLog();
                logEntry.setReportDate(target);
                logEntry.setType("Daily");
                // append source marker (Manual by default)
                String src = (source != null && !source.isBlank()) ? source : "Manual";
                logEntry.setSummary(buildSummary(records.size(), src));
                logEntry.setStatus("Success");
                // removed email metadata fields from AttendanceReportLog; only persist UI-facing fields
                reportLogService.save(logEntry);
                return ResponseEntity.ok(resp);
            } catch (Exception ex) {
                log.error("Failed to send attendance email", ex);
                resp.put("success", false);
                resp.put("message", "Failed to send email: " + ex.getMessage());

                AttendanceReportLog logEntry = new AttendanceReportLog();
                logEntry.setReportDate(target);
                logEntry.setType("Daily");
                String src = (source != null && !source.isBlank()) ? source : "Manual";
                logEntry.setSummary(buildSummary(records.size(), src));
                logEntry.setStatus("Failed: " + ex.getMessage());
                // removed email metadata fields from AttendanceReportLog
                reportLogService.save(logEntry);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }

        } catch (Exception e) {
            log.error("Error building attendance email", e);
            resp.put("success", false);
            resp.put("message", "Failed to build email: " + e.getMessage());

            AttendanceReportLog logEntry = new AttendanceReportLog();
            logEntry.setReportDate(target);
            logEntry.setType("Daily");
            String src = (source != null && !source.isBlank()) ? source : "Manual";
            logEntry.setSummary(buildSummary(0, src));
            logEntry.setStatus("Failed: " + e.getMessage());

            // removed email metadata fields from AttendanceReportLog
            reportLogService.save(logEntry);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    @GetMapping("/send-weekly-email-html")
    public ResponseEntity<Map<String, Object>> sendWeeklyAttendanceEmailHtml(@RequestParam(required = false) String date,
                                                                             @RequestParam(required = false) String source) {
        Map<String, Object> resp = new HashMap<>();
        LocalDate target;

        try {
            target = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        } catch (Exception e) {
            target = LocalDate.now();
        }

        try {
            // compute week range for the selected date (selecting today returns current week)
            WeeklyAttendanceReportService.WeekRange range = weeklyReportService.getWeekRangeForSelectedDate(target, true);
            LocalDate weekStart = range.getStart();
            LocalDate weekEnd = range.getEnd();

            List<AttendanceRecord> aggregated = new ArrayList<>();
            LocalDate cursor = weekStart;
            while (!cursor.isAfter(weekEnd)) {
                SOAPMessage response = soapClient.callSoap(cursor.toString(), cursor.toString());
                aggregated.addAll(attendanceService.processSoapData(SoapParser.parse(response)));
                cursor = cursor.plusDays(1);
            }

            // use unique employee count as summary (one employee counted once per week)
            Set<String> uniqueUsers = new HashSet<>();
            for (AttendanceRecord r : aggregated) if (r != null && r.getUserId() != null) uniqueUsers.add(r.getUserId());
            int summary = uniqueUsers.size();

            // Let service build the weekly HTML and send email using its internal recipients
            String result = weeklyReportService.buildWeeklyReportHtml(weekStart, weekEnd);
            resp.put("success", true);
            resp.put("message", result);

            // record history entry
            // Persist weekly history entry
            AttendanceReportLog logEntry = new AttendanceReportLog();
            logEntry.setReportDate(weekStart);
            logEntry.setType("Weekly");
            String src = (source != null && !source.isBlank()) ? source : "Manual";
            logEntry.setSummary(buildSummary(summary, src));
            logEntry.setStatus("Success");

            // removed email metadata fields from AttendanceReportLog; only persist UI-facing fields
            reportLogService.save(logEntry);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Failed to send weekly attendance email", e);
            resp.put("success", false);
            resp.put("message", e.getMessage());

            AttendanceReportLog logEntry = new AttendanceReportLog();
            logEntry.setReportDate(java.time.LocalDate.now());
            logEntry.setType("Weekly");
            String src = (source != null && !source.isBlank()) ? source : "Manual";
            logEntry.setSummary(buildSummary(0, src));
            logEntry.setStatus("Failed: " + e.getMessage());

            reportLogService.save(logEntry);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    // Preview endpoint: returns the weekly HTML report without sending email (useful for format verification)
    @GetMapping("/weekly-preview")
    public ResponseEntity<String> previewWeeklyHtml(@RequestParam(required = false) String date) {
        LocalDate target;
        try {
            target = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        } catch (Exception e) {
            target = LocalDate.now();
        }

        try {
            WeeklyAttendanceReportService.WeekRange range = weeklyReportService.getWeekRangeForSelectedDate(target, true);
            String html = weeklyReportService.buildWeeklyReportHtml(range.getStart(), range.getEnd());
            return ResponseEntity.ok().header("Content-Type", "text/html; charset=UTF-8").body(html);
        } catch (Exception e) {
            log.error("Failed to build weekly preview", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to build weekly preview: " + e.getMessage());
        }
    }

}
