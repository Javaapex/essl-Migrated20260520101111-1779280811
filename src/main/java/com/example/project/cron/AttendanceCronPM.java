package com.example.project.cron;

import com.example.project.controller.AttendanceController;
import com.example.project.service.WeeklyAttendanceReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AttendanceCronPM {

    private static final Logger log = LoggerFactory.getLogger(AttendanceCronPM.class);

    private final WeeklyAttendanceReportService weeklyReportService;
    private final AttendanceController attendanceController;

    public AttendanceCronPM(WeeklyAttendanceReportService weeklyReportService,
                            AttendanceController attendanceController) {
        this.weeklyReportService = weeklyReportService;
        this.attendanceController = attendanceController;
    }

    /**
     * Daily attendance email at 21:00 or 9:00 PM every day (sends today's report)
     */
    @Scheduled(cron = "0 0 21 * * *")
    public void sendDailyEmail() {
        try {
            log.info("Triggering daily attendance email cron (21:00)");
            attendanceController.sendAttendanceEmailHtml(null, null, null, "Cron");
            log.info("Daily attendance email cron executed");
        } catch (Exception e) { // TODO: Consider catching specific exception types
            log.error("Error while executing daily attendance cron", e);
        }
    }

    /**
     * Weekly attendance email: run once per week at Monday 09:00 AM
     */
    @Scheduled(cron = "0 0 9 ? * MON")
    public void sendWeeklyEmail() {
        try {
            LocalDate today = LocalDate.now();
            LocalDate currentMonday = weeklyReportService.getWeekStartMonday(today);

            // previous week's Monday & Sunday
            LocalDate previousMonday = currentMonday.minusWeeks(1);
            LocalDate previousSunday = previousMonday.plusDays(6);

            // use controller endpoint to ensure history saved with source=Cron
            // call the controller's weekly send with date=previousMonday and source=Cron
            attendanceController.sendWeeklyAttendanceEmailHtml(previousMonday.toString(), "Cron");
            String result = "Weekly cron triggered";
            log.info("Weekly email cron executed: {}", result);
        } catch (Exception e) { // TODO: Consider catching specific exception types
            log.error("Error while executing weekly attendance cron", e);
        }
    }

}
