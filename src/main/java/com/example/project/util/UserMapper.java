package com.example.project.util;

import java.util.HashMap;
import java.util.Map;

public class UserMapper {
    private static final Map<String, String> userMap = new HashMap<>();
    static {
        userMap.put("1001", "Senthil Kumar");
        userMap.put("1002", "Kathavarayan");
        userMap.put("1003", "Kumari");
        userMap.put("1004", "Sheker");
        userMap.put("1005", "Sid Tamilselvan");
        userMap.put("1009", "Nandagopal");
        userMap.put("1010", "Ram");
        userMap.put("1011", "SRIRAM");
        userMap.put("1012", "Bavesh Ramesh");
        userMap.put("1", "Gayathri V");
        userMap.put("2", "Gnanarathinam.C");
        userMap.put("3", "Afzal Rahman.K");
        userMap.put("4", "Kaviarasi.A");
        userMap.put("5", "Abinayaa Nagarajan");
        userMap.put("6", "TAMILSELVAN.S");
        userMap.put("7", "PONNUCHAMY.V");
        userMap.put("8", "AAKASH P");
        userMap.put("10", "AREDDULA NAVEEN KUMAR");
        userMap.put("12", "Nithya.T");
        userMap.put("14", "P.Mugundhan");
        userMap.put("16", "Pooja Ganesh");
        userMap.put("9", "HARI BABU E");
        userMap.put("11", "Lavanya Sundaresan");
        userMap.put("13", "ARUN KUMAR J");
        userMap.put("15", "A SARANYA");
        userMap.put("17", "HAREESH PADMANABHAN VARADARAJU");
        userMap.put("18", "IVLIN KABUSHMA");
        userMap.put("19", "PRAKASH.G");
        userMap.put("20", "PRIYADARSHINI");
        userMap.put("21", "Alice Diana");
        userMap.put("23", "S C SARAVANAKUMAR");
        userMap.put("25", "Preety Singh");
        userMap.put("26", "Kayyuru Ashok Kumar");
        userMap.put("27", "Manju Kannan");
        userMap.put("28", "Kannan Nadarajan");
        userMap.put("30", "Ambika Kalyanasundaram");
        userMap.put("31", "Prakash Ramasamy");
        userMap.put("34", "Premalatha");
        userMap.put("35", "Janani Srinivasan");
        userMap.put("36", "Aswini.P");
        userMap.put("37", "Pulimurugan.T");
        userMap.put("38", "Jagannathan");
        userMap.put("39", "Sankara Narayanan");
        userMap.put("40", "Manikandan");

    }

    public static String getUserName(String userId) {
        return userMap.getOrDefault(userId, "Unknown User");
    }
}
