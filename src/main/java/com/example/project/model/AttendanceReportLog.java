package com.example.project.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;


@Entity
@Table(name = "attendance_report_log")
public class AttendanceReportLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The date selected in the UI for the report (daily date or any date within the week)
    @Column(name = "report_date")
    private LocalDate reportDate;

    // "Daily" or "Weekly"
    @Column(name = "report_type", length = 32)
    private String type;

    // Short summary text (for example: "18 employees, 2 weekend workers")
    @Column(name = "summary", length = 1024)
    private String summary;

    // Status: e.g. "Success" or "Failed" or error message
    @Column(name = "status", length = 256)
    private String status;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
