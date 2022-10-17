package org.example;


import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.PDUStringException;
import org.jsmpp.bean.*;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.*;
import org.jsmpp.util.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeoutException;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

class SubmitDeliverTransceiver{

    static Logger log;
    static {
        try(FileInputStream ins = new FileInputStream("C:\\Users\\d.caraja\\IdeaProjects\\firstPart\\src\\main\\java\\org\\example\\log.config")){
            LogManager.getLogManager().readConfiguration(ins);
            log = Logger.getLogger(SubmitDeliverTransceiver.class.getName());
            log.addHandler(new FileHandler("D:\\smppLogs\\client_logs.txt"));
        }catch (Exception ignore){
            ignore.printStackTrace();
        }
    }
    public static final TimeFormatter TIME_FORMATTER = new AbsoluteTimeFormatter();
    static ServerMessageTransceiverListener serverMessageTransceiverListener = new ServerMessageTransceiverListener();
    public static SMPPSession client = new SMPPSession();
    public static SMPPServerSession server;
    public static SimpleDateFormat formatForDateNow = new SimpleDateFormat("yyyy_dd_MM_hh_mm_ss");

    public static void main(String[] args) {
        try {
            log.info("Слушаем...");
            SMPPServerSessionListener sessionListener = new SMPPServerSessionListener(8056);
            sessionListener.setMessageReceiverListener(serverMessageTransceiverListener);
            server = sessionListener.accept();
            try {
                BindRequest request = server.waitForBind(30000);
                log.info("Получен бинд на подключение от клиента " + request.getSystemId() + ", его пароль " + request.getPassword());
                // accepting request and send bind response immediately
                log.info("Разрешить подключение");
                request.accept("Успешно!");
                client.connectAndBind(
                        "localhost",
                        8080,
                        new BindParameter(
                                request.getBindType(),
                                request.getSystemId(),
                                request.getPassword(),
                                request.getSystemType(),
                                TypeOfNumber.INTERNATIONAL,
                                NumberingPlanIndicator.UNKNOWN,
                                null));
                client.setMessageReceiverListener(new MessageTransceiverListener());
            }catch(TimeoutException | PDUStringException e){
                log.info("Ошибка: " + e);
                server.unbindAndClose();
                sessionListener.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

class MessageTransceiverListener implements org.jsmpp.session.MessageReceiverListener {

    public void onAcceptDeliverSm(DeliverSm deliverSm){
        if (MessageType.SMSC_DEL_RECEIPT.containedIn(deliverSm.getEsmClass())) {
            // delivery receipt
            try {
                DeliveryReceipt delReceipt = deliverSm.getShortMessageAsDeliveryReceipt();
                System.out.println("Ура, получили отчёт о доставке: " + delReceipt);
                long id = Long.parseLong(delReceipt.getId());
                String messageId = Long.toString(id, 16).toUpperCase();
                SubmitDeliverTransceiver.log.info("Сообщение " + messageId + ": " + delReceipt);
                SubmitDeliverTransceiver.server.deliverShortMessage(deliverSm.getServiceType(),
                        TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN, deliverSm.getDestAddress(),
                        TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN, deliverSm.getSourceAddr(),
                        new ESMClass(1), (byte) 1, (byte) 1, new RegisteredDelivery(), DataCodings.ZERO,
                        new String(deliverSm.getShortMessage()).getBytes());
            } catch (InvalidDeliveryReceiptException e) {
                SubmitDeliverTransceiver.log.info("Доставка прервана: " + e);
            } catch (ResponseTimeoutException | PDUException | IOException | InvalidResponseException |
                     NegativeResponseException e) {
                throw new RuntimeException(e);
            }
        } else {
            // regular short message
            SubmitDeliverTransceiver.log.info("Пересылаем отчет о доставке: " + new String(deliverSm.getShortMessage()));
            try {
                SubmitDeliverTransceiver.server.deliverShortMessage(deliverSm.getServiceType(),
                        TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN, deliverSm.getDestAddress(),
                        TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN, deliverSm.getSourceAddr(),
                        new ESMClass(1), (byte) 1, (byte) 1, new RegisteredDelivery(), DataCodings.ZERO,
                        new String(deliverSm.getShortMessage()).getBytes());
            } catch (ResponseTimeoutException | PDUException | IOException | InvalidResponseException |
                     NegativeResponseException e) {
                throw new RuntimeException(e);
            }
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

class ServerMessageTransceiverListener implements org.jsmpp.session.ServerMessageReceiverListener{
    final MessageIDGenerator messageIDGenerator = new RandomMessageIDGenerator();
    public DataBaseConnect dbfw = new DataBaseConnect();
    @Override
    public SubmitSmResult onAcceptSubmitSm(SubmitSm submitSm, SMPPServerSession source) throws RuntimeException {
        String SMS = new String(submitSm.getShortMessage());
        MessageId messageID;
        System.out.println("Полученное сообщение: " + SMS);
        try {
            String date = SubmitDeliverTransceiver.formatForDateNow.format(new Date().getTime());
            dbfw.insertInDB("sms_client", SMS, date);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if(SMS.equals("stop")) System.exit(0);
        // need message_id to response submit_sm, optional parameters add in SMPP 5.
        messageID = messageIDGenerator.newMessageId();
        SubmitDeliverTransceiver.log.info("ID сообщения, отправляемое на клиент: <<" + SMS + ">>: " + messageID);
        try {
            SubmitSmResult submitSmResult1 = SubmitDeliverTransceiver.client.submitShortMessage(submitSm.getServiceType(),
                    TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.UNKNOWN, submitSm.getSourceAddr(),
                    TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.UNKNOWN, submitSm.getDestAddress(),
                    new ESMClass(), (byte) 0, (byte) 1, SubmitDeliverTransceiver.TIME_FORMATTER.format(new Date()), null,
                    new RegisteredDelivery(), (byte) 0, new GeneralDataCoding(Alphabet.ALPHA_DEFAULT, MessageClass.CLASS1, false), (byte) 0,
                    SMS.getBytes());
            SubmitDeliverTransceiver.log.info("ID сообщения, полученного с сервера: " + submitSmResult1.getMessageId());
        } catch (ResponseTimeoutException | PDUException | IOException | InvalidResponseException |
                 NegativeResponseException e) {
            throw new RuntimeException(e);
        }
        //transmitting sms to Server
        return new SubmitSmResult(messageID, new OptionalParameter[0]);
    }

    @Override
    public QuerySmResult onAcceptQuerySm(QuerySm querySm,
                                         SMPPServerSession source) {
        return null;
    }

    @Override
    public void onAcceptReplaceSm(ReplaceSm replaceSm, SMPPServerSession smppServerSession) {

    }

    @Override
    public void onAcceptCancelSm(CancelSm cancelSm, SMPPServerSession smppServerSession) {

    }

    @Override
    public SubmitMultiResult onAcceptSubmitMulti(
            SubmitMulti submitMulti, SMPPServerSession source) {
        return new SubmitMultiResult(messageIDGenerator.newMessageId().getValue(), new UnsuccessDelivery[]{}, new OptionalParameter[]{});
    }

    @Override
    public DataSmResult onAcceptDataSm(DataSm dataSm, Session source) {
        return new DataSmResult(messageIDGenerator.newMessageId(), new OptionalParameter[]{});
    }

    @Override
    public BroadcastSmResult onAcceptBroadcastSm(final BroadcastSm broadcastSm, final SMPPServerSession source) {
        return new BroadcastSmResult(messageIDGenerator.newMessageId(), new OptionalParameter[]{});
    }

    @Override
    public void onAcceptCancelBroadcastSm(CancelBroadcastSm cancelBroadcastSm, SMPPServerSession smppServerSession) {

    }

    @Override
    public QueryBroadcastSmResult onAcceptQueryBroadcastSm(final QueryBroadcastSm queryBroadcastSm,
                                                           final SMPPServerSession source) {
        return null;
    }
}