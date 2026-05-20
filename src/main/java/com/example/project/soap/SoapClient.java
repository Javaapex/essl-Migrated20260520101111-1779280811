package com.example.project.soap;

import jakarta.xml.soap.*;
import org.springframework.stereotype.Component;

@Component
public class SoapClient {

    private static final String SOAP_URL = "http://192.168.0.100:90/iclock/WebAPIService.asmx";
    private static final String SOAP_ACTION = "http://tempuri.org/GetTransactionsLog";

    // Login credentials (From Postman)
    private static final String USERNAME = "dummy";       // From Postman
    private static final String PASSWORD = "Dummy@123";   // From Postman
    private static final String SERIAL_NUMBER = "JJA1251900136"; // Device serial number

    public SOAPMessage callSoap(String fromDate, String toDate) throws Exception {

        SOAPConnectionFactory factory = SOAPConnectionFactory.newInstance();
        SOAPConnection connection = factory.createConnection();

        SOAPMessage request = buildRequest(fromDate, toDate);
        SOAPMessage response = connection.call(request, SOAP_URL);

        // Debug print (safe)
        System.out.println("\n===== SOAP RESPONSE =====");
        response.writeTo(System.out);
        System.out.println("\n==========================\n");

        return response;
    }

    private SOAPMessage buildRequest(String from, String to) throws Exception {

        MessageFactory msgFactory = MessageFactory.newInstance();
        SOAPMessage message = msgFactory.createMessage();

        SOAPEnvelope envelope = message.getSOAPPart().getEnvelope();
        envelope.addNamespaceDeclaration("tem", "http://tempuri.org/");

        SOAPBody body = envelope.getBody();
        SOAPElement method = body.addChildElement("GetTransactionsLog", "tem");

        // Required SOAP parameters
        method.addChildElement("FromDateTime", "tem").addTextNode(from + " 00:00:00");
        method.addChildElement("ToDateTime", "tem").addTextNode(to + " 23:59:59");
        method.addChildElement("SerialNumber", "tem").addTextNode(SERIAL_NUMBER);
        method.addChildElement("UserName", "tem").addTextNode(USERNAME);
        method.addChildElement("UserPassword", "tem").addTextNode(PASSWORD);
        method.addChildElement("strDataList", "tem").addTextNode("");

        message.getMimeHeaders().addHeader("SOAPAction", SOAP_ACTION);
        message.saveChanges();

        return message;
    }
}

