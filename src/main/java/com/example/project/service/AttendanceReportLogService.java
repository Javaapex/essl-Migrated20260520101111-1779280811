package com.example.project.service;

import com.example.project.model.AttendanceReportLog;
import com.example.project.repository.AttendanceReportLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AttendanceReportLogService {
    private final AttendanceReportLogRepository repository;

    public AttendanceReportLogService(AttendanceReportLogRepository repository) {
        this.repository = repository;
    }

    public AttendanceReportLog save(AttendanceReportLog log) {
        return repository.save(log);
    }

    public List<AttendanceReportLog> findAll() {
        return repository.findAll();
    }
}

