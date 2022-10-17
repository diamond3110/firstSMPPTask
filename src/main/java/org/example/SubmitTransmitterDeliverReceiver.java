package org.example;

import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.*;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.*;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.InvalidDeliveryReceiptException;
import org.jsmpp.util.TimeFormatter;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class SubmitTransmitterDeliverReceiver {

    private static final TimeFormatter TIME_FORMATTER = new AbsoluteTimeFormatter();
    static Logger LOGGER;
    static {
        try(FileInputStream ins = new FileInputStream("C:\\Users\\d.caraja\\IdeaProjects\\firstPart\\src\\main\\java\\org\\example\\log.config")){
            LogManager.getLogManager().readConfiguration(ins);
            LOGGER = Logger.getLogger(SubmitTransmitterDeliverReceiver.class.getName());
            LOGGER.addHandler(new FileHandler("D:\\smppLogs\\user_logs.txt"));
        }catch (Exception ignore){
            ignore.printStackTrace();
        }
    }
    public static SimpleDateFormat formatForDateNow = new SimpleDateFormat("yyyy_dd_MM_hh_mm_ss");
    public static DataBaseConnect dbfw;

    public static void main(String[] args) throws IOException, SQLException {
        Scanner sc = new Scanner(System.in);
        dbfw = new DataBaseConnect();
        SMPPSession smppSession = new SMPPSession();
        smppSession.connectAndBind(
                "localhost",
                8056,
                new BindParameter(
                        BindType.BIND_TRX,
                        "smpp_user",
                        "d1234",
                        "cp",
                        TypeOfNumber.INTERNATIONAL,
                        NumberingPlanIndicator.UNKNOWN,
                        null));
        smppSession.setMessageReceiverListener(new MessageReceiverListener());
        while(true){
            System.out.println("Введите SMS:");
            String s = sc.nextLine();
            LOGGER.info("Сообщение <<" + s + ">> отправлено!");
            String date = formatForDateNow.format(new Date().getTime());
            dbfw.insertInDB("sms_user", s, date);
            sendSingleSMS(s, smppSession);
            if(s.equals("stop")) break;
        }
        System.exit(0);
    }

    public static void sendSingleSMS(String shortMessage, SMPPSession session){
        try {
            SubmitSmResult submitSmResult = session.submitShortMessage("CMT",
                    TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.UNKNOWN, "1616",
                    TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.UNKNOWN, "628176504657",
                    new ESMClass(), (byte)0, (byte)1,  TIME_FORMATTER.format(new Date()), null,
                    new RegisteredDelivery(), (byte)0, new GeneralDataCoding(Alphabet.ALPHA_DEFAULT, MessageClass.CLASS1, false), (byte)0,
                    shortMessage.getBytes());
            LOGGER.info("ID сообщения: <<" + shortMessage + ">>: " + submitSmResult.getMessageId());
        } catch (ResponseTimeoutException | PDUException | InvalidResponseException | NegativeResponseException | IOException ignored) {
        }
    }
}

class MessageReceiverListener implements org.jsmpp.session.MessageReceiverListener {
    public void onAcceptDeliverSm(DeliverSm deliverSm){
        if (MessageType.SMSC_DEL_RECEIPT.containedIn(deliverSm.getEsmClass())) {
            // delivery receipt
            try {
                DeliveryReceipt delReceipt = deliverSm.getShortMessageAsDeliveryReceipt();
                long id = Long.parseLong(delReceipt.getId());
                String messageId = Long.toString(id, 16).toUpperCase();
                SubmitTransmitterDeliverReceiver.LOGGER.info("Получено потдтверждение о доставке: " + messageId + ": " + delReceipt);
            } catch (InvalidDeliveryReceiptException e) {
                SubmitTransmitterDeliverReceiver.LOGGER.info("Отчет о доставке не получен!: " + e);
            }
        } else {
            // regular short message
            SubmitTransmitterDeliverReceiver.LOGGER.info("Получено подтверждение о доставке: " + new String(deliverSm.getShortMessage()));
        }
    }

    @Override
    public void onAcceptAlertNotification(AlertNotification alertNotification) {

    }

    @Override
    public DataSmResult onAcceptDataSm(DataSm dataSm, Session session) {
        return null;
    }
}

