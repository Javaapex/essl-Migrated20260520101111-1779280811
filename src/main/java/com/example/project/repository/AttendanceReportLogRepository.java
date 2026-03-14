package com.example.project.repository;

import com.example.project.model.AttendanceReportLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceReportLogRepository extends JpaRepository<AttendanceReportLog, Long> {
}

