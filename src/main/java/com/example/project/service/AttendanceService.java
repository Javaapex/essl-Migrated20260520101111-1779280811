package com.example.project.service;

import com.example.project.model.AttendanceRecord;
import com.example.project.model.AttendanceSoapData;
import com.example.project.util.UserMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AttendanceService {

    public List<AttendanceRecord> processSoapData(List<AttendanceSoapData> soapData) {

        Map<String, List<AttendanceSoapData>> grouped = new HashMap<>();

        // 1) GROUP BY userId + date (yyyy-MM-dd)
        for (AttendanceSoapData d : soapData) {
            if (d == null) continue;
            String ts = d.getTimeStamp();
            String uid = d.getUserId();
            if (ts == null || uid == null) continue;

            String datePart;
            try {
                datePart = ts.substring(0, 10); // "yyyy-MM-dd"
                // validate by parsing
                LocalDate.parse(datePart);
            } catch (Exception ex) {
                // invalid timestamp — skip
                continue;
            }

            String userId = uid.trim();
            String key = userId + "_" + datePart;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        List<AttendanceRecord> finalList = new ArrayList<>();

        // 2) PROCESS GROUPS
        for (String key : grouped.keySet()) {
            List<AttendanceSoapData> logs = grouped.get(key);
            if (logs == null || logs.isEmpty()) continue;

            String[] parts = key.split("_", 2);
            if (parts.length < 2) continue;
            String userId = parts[0];
            LocalDate date;
            try {
                date = LocalDate.parse(parts[1]);
            } catch (Exception e) {
                continue;
            }

            List<LocalDateTime> inTimes = new ArrayList<>();
            List<LocalDateTime> outTimes = new ArrayList<>();

            // parse timestamps (ISO format expected: 2025-11-25T10:41:50 or with fraction)
            for (AttendanceSoapData s : logs) {
                if (s.getTimeStamp() == null) continue;
                LocalDateTime ts;
                try {
                    ts = LocalDateTime.parse(s.getTimeStamp()); // works for ISO_LOCAL_DATE_TIME
                } catch (Exception ex) {
                    // try to be lenient: replace space with 'T' if needed
                    try {
                        ts = LocalDateTime.parse(s.getTimeStamp().replace(' ', 'T'));
                    } catch (Exception ex2) {
                        // give up on this entry
                        continue;
                    }
                }

                String dir = s.getDirection() == null ? "" : s.getDirection().toUpperCase().trim();
                if (dir.equals("IN") || dir.equals("0")) inTimes.add(ts);
                else if (dir.equals("OUT") || dir.equals("1")) outTimes.add(ts);
            }

            Collections.sort(inTimes);
            Collections.sort(outTimes);

            LocalDateTime firstIn = inTimes.isEmpty() ? null : inTimes.get(0);
            LocalDateTime lastOut = outTimes.isEmpty() ? null : outTimes.get(outTimes.size() - 1);

            // Build AttendanceRecord (uses model's setInTime/setOutTime which auto-calc duration)
            AttendanceRecord rec = new AttendanceRecord();
            rec.setUserId(userId);

            // choose name: SOAP-provided first, else fallback to UserMapper
            String soapName = logs.stream()
                    .map(AttendanceSoapData::getUserName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse(UserMapper.getUserName(userId));

            rec.setUserName(soapName);
            rec.setDate(date);

            // <-- FIXED: set correct fields using the correct variables/methods
            rec.setInTime(firstIn);
            rec.setOutTime(lastOut);

            // the AttendanceRecord model's updateDuration() will compute duration automatically
            finalList.add(rec);
        }

        // 3) SORT alphabetically by userName (case-insensitive, null-safe)
        // 3) SORT by userId (numeric ascending)
        finalList.sort(Comparator.comparingInt(r -> Integer.parseInt(r.getUserId())));

        return finalList;
    }
}
