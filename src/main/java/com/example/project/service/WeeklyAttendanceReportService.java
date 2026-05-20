package com.example.project.service;

import com.example.project.model.AttendanceRecord;
import com.example.project.model.AttendanceSoapData;
import com.example.project.util.UserMapper;
import com.example.project.soap.SoapClient;
import com.example.project.soap.SoapParser;
import jakarta.xml.soap.SOAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class WeeklyAttendanceReportService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyAttendanceReportService.class);

    private final AttendanceService attendanceService;
    private final SoapClient soapClient;
    private final EmailService emailService;

    private static final DateTimeFormatter SUBJECT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEADER_SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    public WeeklyAttendanceReportService(AttendanceService attendanceService,
                                         SoapClient soapClient,
                                         EmailService emailService) {
        this.attendanceService = attendanceService;
        this.soapClient = soapClient;
        this.emailService = emailService;
    }

    /**
     * Build report for the current week (Monday–Sunday)
     */
    public String buildCurrentWeekReport() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate monday = getWeekStartMonday(today);
        LocalDate sunday = monday.plusDays(6);
        return buildWeeklyReportHtml(monday, sunday);
    }

    /**
     * Build report for any Monday–Sunday range
     */
    public String buildWeeklyReportHtml(LocalDate weekStartMonday, LocalDate weekEndSunday) throws Exception {
        // Call SOAP API dynamically for the week
        SOAPMessage response = soapClient.callSoap(weekStartMonday.toString(), weekEndSunday.toString());
        List<AttendanceSoapData> raw = SoapParser.parse(response);
        List<AttendanceRecord> records = attendanceService.processSoapData(raw);

        // Map: userId -> (date -> AttendanceRecord)
        Map<String, Map<LocalDate, AttendanceRecord>> byUser = new LinkedHashMap<>();
        for (AttendanceRecord r : records) {
            byUser.computeIfAbsent(r.getUserId(), id -> new HashMap<>()).put(r.getDate(), r);
        }

        // Sorted user IDs
        List<String> userIds = byUser.keySet().stream()
                .sorted(Comparator.comparingInt(id -> {
                    try { return Integer.parseInt(id); } catch (Exception ex) { return id.hashCode(); }
                }))
                .toList();

        List<LocalDate> weekDates = getWeekDatesMondayToSunday(weekStartMonday);

        String subject = "(" + weekStartMonday.format(SUBJECT_DATE) + " - " + weekEndSunday.format(SUBJECT_DATE) + ") Weekly Attendance Report - Chennai (408)";

        StringBuilder html = new StringBuilder();
        html.append("<p>Dear Team,</p>");
        html.append("<p>Please find the Weekly Attendance Report for ")
                .append(weekStartMonday.format(SUBJECT_DATE))
                .append(" - ")
                .append(weekEndSunday.format(SUBJECT_DATE))
                .append(".</p>");


        html.append("<p style='font-size:16px; font-weight:bold;'>").append("Total Employees: ").append(userIds.size()).append("</p>");

        // Table header
        html.append("<table border='1' cellpadding='4' cellspacing='0' style='border-collapse: collapse; font-family: Arial, sans-serif;'>");
        html.append("<tr style='background:#f2f2f2;'>")
                .append("<th style='padding:8px; white-space: nowrap;'>ID</th>")
                .append("<th style='padding:8px; white-space: nowrap;'>Name</th>");
        for (LocalDate d : weekDates) {
            html.append("<th style='padding:8px; white-space: nowrap;'>")
                    .append(formatShortDate(d))
                    .append("</th>");
        }
        html.append("<th style='padding:8px; white-space: nowrap;'>Total Hours</th></tr>");

        int redCount = 0;
        int yellowCount = 0;
        int weekendWorkers = 0;

        for (String uid : userIds) {
            Map<LocalDate, AttendanceRecord> byDate = byUser.getOrDefault(uid, Collections.emptyMap());

            String userName = byDate.values().stream()
                    .map(AttendanceRecord::getUserName)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(UserMapper.getUserName(uid));

            List<String> dailyDisplay = new ArrayList<>();
            boolean workedWeekend = false;
            int totalWeekMinutes = 0;

            for (LocalDate d : weekDates) {
                AttendanceRecord rec = byDate.get(d);
                String disp = (rec == null || rec.getDuration() == null) ? "0hr 0m" : normalizeDurationText(rec.getDuration());

                DayOfWeek dow = d.getDayOfWeek();
                int minutes = parseDurationToMinutes(disp);

                if ((dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) && minutes > 0) {
                    workedWeekend = true;
                }

                // Sum only Monday–Friday for total
                if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                    totalWeekMinutes += minutes;
                }

                dailyDisplay.add(disp);
            }

            if (workedWeekend) weekendWorkers++;

            String totalFormatted = minutesToHourMin(totalWeekMinutes);

            String totalStyle = "";
            if (totalWeekMinutes < 25 * 60) {
                totalStyle = "background-color:tomato; color:black;";
                redCount++;
            } else if (totalWeekMinutes < 35 * 60) {
                totalStyle = "background-color:yellow; color:black;";
                yellowCount++;
            }

            html.append("<tr>")
                    .append("<td style='padding:8px; white-space: nowrap;'>").append(uid).append("</td>")
                    .append("<td style='padding:8px; white-space: nowrap;'>").append(userName == null ? "" : escapeHtml(userName)).append("</td>");

            for (int i = 0; i < weekDates.size(); i++) {
                LocalDate d = weekDates.get(i);
                String cell = dailyDisplay.get(i);
                DayOfWeek dow = d.getDayOfWeek();
                String cellStyle = "padding:8px; white-space: nowrap; text-align:center;";

                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                    // Weekend → show duration if worked, else blank
                    if (parseDurationToMinutes(cell) > 0) {
                        cellStyle = "background-color:lightblue; padding:8px; white-space: nowrap; text-align:center;";
                    } else {
                        cell = "";
                    }
                } else {
                    // Weekday → 0hr 0m replaced with grey
                    if ("0hr 0m".equals(cell)) {
                        cell = "--";
                        cellStyle = "background-color:#d3d3d3; padding:8px; white-space: nowrap; text-align:center;";
                    }
                }

                html.append("<td style='").append(cellStyle).append("'>").append(cell).append("</td>");
            }

            html.append("<td style='").append(totalStyle).append(" padding:8px; white-space: nowrap; text-align:center;'>")
                    .append(totalFormatted)
                    .append("</td>")
                    .append("</tr>");
        }

        html.append("</table>");

        // Summary
        html.append(" ");
        html.append("<p style='font-size:16px;'>")
                .append("<span style='display:inline-block;width:15px;height:15px;background-color:tomato; margin-right:5px;border:1px solid #000;'></span> ")
                .append(redCount).append(" Employees (Less than 25 hrs)</p>");

        html.append("<p style='font-size:16px;'>")
                .append("<span style='display:inline-block;width:15px;height:15px;background-color:yellow; margin-right:5px;border:1px solid #000;'></span> ")
                .append(yellowCount).append(" Employees (between 25 to 35 hrs)</p>");

        html.append("<p style='font-size:16px;'>")
                .append("<span style='display:inline-block;width:15px;height:15px;background-color:lightblue; margin-right:5px;border:1px solid #000;'></span> ")
                .append(weekendWorkers).append(" Employees (Worked on weekend)</p>");

        html.append(" ");
        html.append("<p style='color:#A9A9A9; font-size:17px;'>This is an automated scheduled email.</p>");

        // Prepare To/CC/BCC lists for weekly report
        String[] toRecipients = getWeeklyToRecipients();
        String[] ccRecipients = getWeeklyCcRecipients();
        String[] bccRecipients = getWeeklyBccRecipients();

        // total recipients count (helpers always return non-null arrays)
        int totalRecipients = toRecipients.length + ccRecipients.length + bccRecipients.length;

        int sent;
        try {
            // Try sending to all recipients at once (To/CC/BCC supported)
            emailService.sendHtmlEmail(toRecipients, ccRecipients, bccRecipients, subject, html.toString());
            // Consider the send successful for all recipients if no exception
            sent = totalRecipients;
        } catch (Exception e) { // TODO: Consider catching specific exception types
            log.error("Failed to send weekly attendance email in bulk", e);
            // Fallback: try per-To recipient (preserve CC/BCC as empty for per-recipient sends)
            sent = 0;
            String[] empty = new String[0];
            for (String to : toRecipients) {
                try {
                    emailService.sendHtmlEmail(new String[]{to}, empty, empty, subject, html.toString());
                    sent++;
                } catch (Exception ex) {
                    log.error("Failed to send weekly attendance email to {}", to, ex);
                }
            }
            // Note: CC/BCC recipients won't be retried individually here
        }

        return "Weekly Attendance emailed successfully to " + sent + "/" + totalRecipients + " users!";
    }

    // -------------------- Helpers --------------------

    public LocalDate getWeekStartMonday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Helper to return a week range (Monday..Sunday) for a selected date.
     * If weeklyMode is false, returns a range with start=end=selectedDate.
     */
    public WeekRange getWeekRangeForSelectedDate(LocalDate selectedDate, boolean weeklyMode) {
        if (!weeklyMode) return new WeekRange(selectedDate, selectedDate);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate ref = selectedDate.equals(today) ? today : selectedDate;
        LocalDate start = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        return new WeekRange(start, end);
    }

    public static class WeekRange {
        private final LocalDate start;
        private final LocalDate end;

        public WeekRange(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }

        public LocalDate getStart() { return start; }
        public LocalDate getEnd() { return end; }
    }

    private List<LocalDate> getWeekDatesMondayToSunday(LocalDate monday) {
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < 7; i++) dates.add(monday.plusDays(i));
        return dates;
    }

    private String formatShortDate(LocalDate d) {
        return d.format(HEADER_SHORT);
    }

    private String normalizeDurationText(String raw) {
        if (raw == null || raw.isBlank()) return "0hr 0m";

        raw = raw.trim().toLowerCase();

        int hours = 0;
        int minutes = 0;

        try {
            if (raw.contains(":")) {
                String[] parts = raw.split(":");
                hours = Integer.parseInt(parts[0]);
                minutes = Integer.parseInt(parts[1]);
                return hours + "hr " + minutes + "m";
            }

            if (raw.contains("h")) {
                hours = Integer.parseInt(raw.substring(0, raw.indexOf("h")).replaceAll("[^0-9]", ""));
            }
            if (raw.contains("m")) {
                int start = raw.contains("h") ? raw.indexOf("h") + 1 : 0;
                minutes = Integer.parseInt(raw.substring(start, raw.indexOf("m")).replaceAll("[^0-9]", ""));
            }

            return hours + "hr " + minutes + "m";
        } catch (Exception e) { // TODO: Consider catching specific exception types
            return "0hr 0m";
        }
    }

    private int parseDurationToMinutes(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            String clean = value.replace("hr", "").replace("m", "").trim();
            String[] parts = clean.split("\\s+");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return (h * 60) + m;
        } catch (Exception e) { // TODO: Consider catching specific exception types
            return 0;
        }
    }

    private String minutesToHourMin(int totalMinutes) {
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        return h + "hr " + m + "m";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * Weekly recipients: split into To / CC / BCC. Replace these with config/DB as needed.
     */
    public String[] getWeeklyToRecipients() {
        return new String[]{


        };
    }

    public String[] getWeeklyCcRecipients() {
        return new String[]{

        };
    }

    public String[] getWeeklyBccRecipients() {
        return new String[]{

        };
    }
}
